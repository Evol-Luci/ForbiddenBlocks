package me.lucievol.forbiddenblocks;

import me.lucievol.forbiddenblocks.config.ForbiddenBlocksConfig;
import me.lucievol.forbiddenblocks.config.WorldConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Main client-side implementation of the ForbiddenBlocks mod.
 * 
 * This class handles:
 * 1. Client-side initialization and event registration
 * 2. Keybinding setup and handling
 * 3. Block placement prevention
 * 4. User feedback and messaging
 * 
 * Key Features:
 * - Thread-safe key press handling
 * - Real-time block placement prevention
 * - Configurable message feedback
 * - Robust error handling and logging
 * 
 * The mod operates entirely client-side, making it safe for use on any server
 * without requiring server-side installation.
 */
public class ForbiddenBlocksClient implements ClientModInitializer {
    // Logger instance for this class
    private static final Logger LOGGER = LoggerFactory.getLogger("forbiddenblocks");
    
    // Lock object for thread-safe key handling
    private static final Object KEY_LOCK = new Object();
    
    // Flag to prevent concurrent key handling
    private volatile boolean isHandlingKeyPress = false;

    // Connection state tracking
    private static boolean isConnected = false;
    private static String lastConnectedServer = "";

    /**
     * Updates the connection state and triggers configuration updates
     * @param connected Whether we're connected to a server
     * @param serverAddress The server address we're connecting to
     */
    public static void updateConnectionState(boolean connected, String serverAddress) {
        LOGGER.info("CONNECTION UPDATE - Previous state: Connected={}, Server={}", isConnected, lastConnectedServer);
        isConnected = connected;
        lastConnectedServer = serverAddress;
        LOGGER.info("CONNECTION UPDATE - New state: Connected={}, Server={}", connected, serverAddress);
        
        // Update configuration based on new connection state
        LOGGER.info("CONNECTION UPDATE - Requesting WorldConfig update");
        WorldConfig.updateForCurrentWorld();
    }

    /**
     * @return The current connection state
     */
    public static boolean isConnected() {
        return isConnected;
    }

    /**
     * @return The last connected server address
     */
    public static String getLastConnectedServer() {
        return lastConnectedServer;
    }

    /**
     * Key binding for toggling block/item restriction.
     * Default: O key
     * Category: ForbiddenBlocks Keys
     */
    private static final KeyBinding FORBID_KEY = new KeyBinding(
            "key.forbiddenblocks.forbid",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "category.forbiddenblocks.keys"
    );

    /**
     * Key binding for toggling feedback messages.
     * Default: M key
     * Category: ForbiddenBlocks Keys
     */
    private static final KeyBinding TOGGLE_MESSAGES_KEY = new KeyBinding(
            "key.forbiddenblocks.toggle_messages",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "category.forbiddenblocks.keys"
    );

    /**
     * Initializes the client-side mod components.
     * This method is called by Fabric when the game is starting up.
     * 
     * Sets up:
     * - Configuration system
     * - Key bindings
     * - Event handlers for block placement and key presses
     */
    @Override
    public void onInitializeClient() {
        ForbiddenBlocksConfig.init();
        KeyBindingHelper.registerKeyBinding(FORBID_KEY);
        KeyBindingHelper.registerKeyBinding(TOGGLE_MESSAGES_KEY);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> onBlockPlace(player, hand));

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Client shutting down, saving configurations");
            WorldConfig.saveAll();
        }));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerInfo serverInfo = client.getCurrentServerEntry();
            String serverAddress = "unknown";
            if (serverInfo != null) {
                serverAddress = serverInfo.address;
                LOGGER.info("MULTIPLAYER JOIN EVENT: Connected to server: {}. Server info: {}", serverAddress, serverInfo.name);
            } else {
                try {
                    serverAddress = handler.getConnection().getAddress().toString();
                    LOGGER.info("MULTIPLAYER JOIN EVENT: Connected to server via address: {}", serverAddress);
                } catch (Exception e) {
                    LOGGER.error("MULTIPLAYER JOIN EVENT: Failed to get server address from handler", e);
                }
            }
            updateConnectionState(true, serverAddress);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LOGGER.info("MULTIPLAYER DISCONNECT EVENT: Disconnected from server: {}", lastConnectedServer);
            updateConnectionState(false, "");
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isValidGameState(client)) {
                return;
            }

            if (!isHandlingKeyPress) {
                synchronized (KEY_LOCK) {
                    try {
                        isHandlingKeyPress = true;
                        handleKeyPresses(client);
                    } finally {
                        isHandlingKeyPress = false;
                    }
                }
            }
        });

        LOGGER.info("ForbiddenBlocks client initialized");
    }

    /**
     * Validates the current game state for key handling.
     * Ensures we only process keys when appropriate (e.g., not in menus).
     * 
     * @param client The Minecraft client instance
     * @return true if key handling should proceed, false otherwise
     */
    private boolean isValidGameState(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        
        // Don't process keys if a screen is open (inventory, chat, etc.)
        if (!client.isRunning() || client.currentScreen != null) {
            return false;
        }

        return true;
    }

    /**
     * Handles registered key presses in a thread-safe manner.
     * Processes both the forbid toggle and message toggle keys.
     * 
     * @param client The Minecraft client instance
     */
    private void handleKeyPresses(MinecraftClient client) {
        try {
            if (FORBID_KEY.wasPressed()) {
                LOGGER.info("Forbid key pressed - starting forbid item process");
                client.execute(() -> forbidItem(client.player));
            }
            
            if (TOGGLE_MESSAGES_KEY.wasPressed()) {
                LOGGER.info("Toggle messages key pressed");
                client.execute(() -> toggleMessages(client.player));
            }
        } catch (Exception e) {
            LOGGER.error("Error handling key press", e);
        }
    }

    /**
     * Gets the registry identifier for an item stack.
     * This is used to uniquely identify items across different environments.
     * 
     * @param stack The ItemStack to get the identifier for
     * @return The registry ID as a string, or empty string if invalid
     */
    private static String getItemIdentifier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            LOGGER.warn("Attempted to get identifier for null/empty stack");
            return "";
        }
        try {
            Identifier id = Registries.ITEM.getId(stack.getItem());
            String identifier = id.toString();
            String displayName = stack.getName().getString();
            LOGGER.info("Item info - Registry ID: {}, Display Name: {}", identifier, displayName);
            return identifier;
        } catch (Exception e) {
            LOGGER.error("Error getting item identifier", e);
            return "";
        }
    }

    /**
     * Handles block placement attempts by players.
     * Prevents placement if the block is in the forbidden list.
     * 
     * @param player The player attempting to place the block
     * @param hand The hand used for placement (main or off hand)
     * @return ActionResult.FAIL if blocked, ActionResult.PASS otherwise
     */
    private static ActionResult onBlockPlace(PlayerEntity player, Hand hand) {
        try {
            if (!(player instanceof ClientPlayerEntity clientPlayer)) {
                return ActionResult.PASS;
            }

            if (hand == Hand.MAIN_HAND) {
                ItemStack stack = clientPlayer.getMainHandStack();
                String itemId = getItemIdentifier(stack);
                
                if (itemId.isEmpty()) {
                    return ActionResult.PASS;
                }

                String itemName = stack.getName().getString();
                String lore = ""; // Skip lore for now due to NBT access issues

                LOGGER.debug("Checking if item is forbidden - ID: {}, Name: {}", itemId, itemName);
                WorldConfig worldConfig = WorldConfig.getCurrentWorld();
                WorldConfig.ItemIdentifier identifier = new WorldConfig.ItemIdentifier(itemId, itemName, lore);
                boolean isForbidden = worldConfig.isItemForbidden(identifier);
                LOGGER.debug("Item forbidden status: {}", isForbidden);

                if (isForbidden) {
                    if (ForbiddenBlocksConfig.get().shouldShowMessages()) {
                        clientPlayer.sendMessage(Text.of("§cYou cannot place " + itemName + "! (Client-Side)"), false);
                    }
                    LOGGER.info("Blocked placement of forbidden item: {} ({})", itemName, itemId);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        } catch (Exception e) {
            LOGGER.error("Error checking block placement", e);
            return ActionResult.PASS; // On error, allow placement to prevent disruption
        }
    }

    /**
     * Toggles whether an item is forbidden or allowed.
     * Uses the item in the player's main hand.
     * 
     * @param player The player toggling the item
     */
    private void forbidItem(ClientPlayerEntity player) {
        if (player == null) {
            LOGGER.warn("Attempted to forbid item for null player");
            return;
        }

        ItemStack stack = player.getMainHandStack();
        String registryId = getItemIdentifier(stack);
        String name = stack.getName().getString();
        // Skip lore check for now since NBT access methods are causing issues
        String lore = "";

        if (registryId.isEmpty()) {
            return;
        }

        WorldConfig config = WorldConfig.getCurrentWorld();
        WorldConfig.ItemIdentifier identifier = new WorldConfig.ItemIdentifier(registryId, name, lore);
        config.toggleItem(identifier);
        
        // Add user feedback
        if (ForbiddenBlocksConfig.get().shouldShowMessages()) {
            boolean isForbidden = config.isItemForbidden(identifier);
            if (isForbidden) {
                player.sendMessage(Text.of("§e" + name + " is now forbidden to place. (Client-Side)"), false);
            } else {
                player.sendMessage(Text.of("§a" + name + " is now allowed again. (Client-Side)"), false);
            }
        }
    }

    /**
     * Toggles visibility of feedback messages.
     * Updates the global configuration and notifies the player.
     * 
     * @param player The player toggling message visibility
     */
    private static void toggleMessages(ClientPlayerEntity player) {
        try {
            if (player == null) {
                LOGGER.warn("Cannot toggle messages - player is null");
                return;
            }

            ForbiddenBlocksConfig config = ForbiddenBlocksConfig.get();
            config.toggleMessages();
            boolean showMessages = config.shouldShowMessages();
            LOGGER.info("Messages are now {}", showMessages ? "enabled" : "disabled");

            player.sendMessage(Text.of(showMessages ? 
                "§aForbiddenBlocks messages enabled" : 
                "§eForbiddenBlocks messages disabled"), false);
        } catch (Exception e) {
            LOGGER.error("Error toggling message visibility", e);
        }
    }
}
