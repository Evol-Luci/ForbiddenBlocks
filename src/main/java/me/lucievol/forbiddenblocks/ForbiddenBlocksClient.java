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
import net.minecraft.component.ComponentMap; // Corrected class name
import net.minecraft.component.ComponentType; // Corrected class name
// import net.minecraft.component.DataComponentTypes; // Not directly used
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.util.Map;
import java.util.TreeMap;
// import java.util.stream.Collectors; // Not used
// import java.util.List; // Not used
// import net.minecraft.component.type.LoreComponent; // Not used


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

    // Gson instance for serializing components
    private static final Gson GSON = new GsonBuilder().create(); // Not pretty printing for compactness

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
        UseBlockCallback.EVENT.register(this::onBlockUse); // Pass all params
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(this::onEntityUse);

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
     * @return The full ItemIdentifier object, or null if invalid
     */
    private static WorldConfig.ItemIdentifier getItemIdentifier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            LOGGER.warn("Attempted to get identifier for null/empty stack");
            return null;
        }
        try {
            Identifier id = Registries.ITEM.getId(stack.getItem());
            if (id == null) {
                LOGGER.warn("Item has no registry ID: {}", stack);
                return null;
            }
            String registryId = id.toString();
            // Removed duplicate registryId declaration here
            String displayName = stack.getName().getString();

            ComponentMap currentComponents = stack.getComponents(); // Use ComponentMap
            Map<String, JsonElement> componentJsonMap = new TreeMap<>(); // TreeMap for sorted keys

            Map<String, JsonElement> componentJsonMap = new TreeMap<>(); // TreeMap for sorted keys

            // Iterate Registries.DATA_COMPONENT_TYPE and use stack.contains() + stack.get()
            for (ComponentType<?> componentType : Registries.DATA_COMPONENT_TYPE) {
                if (stack.contains(componentType)) {
                    Object actualValue = stack.get(componentType);
                    if (actualValue != null) { // Should generally be true if contains is true, but good practice
                        Identifier componentTypeId = Registries.DATA_COMPONENT_TYPE.getId(componentType);
                        if (componentTypeId != null) {
                            componentJsonMap.put(componentTypeId.toString(), GSON.toJsonTree(actualValue));
                        }
                    }
                }
            }
            String componentsJson = GSON.toJson(componentJsonMap);

            LOGGER.debug("Item info - Registry ID: {}, Display Name: {}, Components: {}", registryId, displayName, componentsJson);
            return new WorldConfig.ItemIdentifier(registryId, displayName, componentsJson);
        } catch (Exception e) {
            LOGGER.error("Error getting item identifier for stack " + stack.toString(), e);
            return null;
        }
    }

    /**
     * Handles block usage attempts by players against blocks.
     * Prevents placement if the block is in the forbidden list, with exceptions.
     */
    private ActionResult onBlockUse(PlayerEntity player, net.minecraft.world.World world, Hand hand, net.minecraft.util.hit.BlockHitResult hitResult) {
        if (!(player instanceof ClientPlayerEntity clientPlayer)) {
            return ActionResult.PASS;
        }

        ItemStack stackInHand = player.getStackInHand(hand);
        if (stackInHand.isEmpty()) {
            return ActionResult.PASS;
        }

        WorldConfig.ItemIdentifier itemIdentifier = getItemIdentifier(stackInHand);
        if (itemIdentifier == null) {
            LOGGER.warn("onBlockUse: Could not get ItemIdentifier for stack: {}", stackInHand);
            return ActionResult.PASS; // Safety: pass if item can't be identified
        }

        String itemName = stackInHand.getName().getString();
        WorldConfig worldConfig = WorldConfig.getCurrentWorld();
        boolean isForbidden = worldConfig.isItemForbidden(itemIdentifier);

        LOGGER.debug("onBlockUse: Item: {}, Hand: {}, Forbidden: {}, Target: {}", itemName, hand, isForbidden, hitResult.getBlockPos());

        if (isForbidden) {
            net.minecraft.block.BlockState targetBlockState = world.getBlockState(hitResult.getBlockPos());
            net.minecraft.block.Block targetBlock = targetBlockState.getBlock();

            // Exception: Interacting with containers (BlockEntityProvider) or Doors with a forbidden item in MAIN_HAND should be allowed.
            // This allows opening chests, doors, etc., even if holding a forbidden item.
            if (hand == Hand.MAIN_HAND && (targetBlock instanceof net.minecraft.block.BlockEntityProvider || targetBlock instanceof net.minecraft.block.DoorBlock)) {
                LOGGER.info("Allowing interaction with container/door '{}' with forbidden item '{}' in main hand.", targetBlock.getName().getString(), itemName);
                return ActionResult.PASS;
            }

            // If the item is forbidden and it's not an allowed interaction (like opening a chest with main hand),
            // then block the action (which is usually placing the block).
            if (ForbiddenBlocksConfig.get().shouldShowMessages()) {
                clientPlayer.sendMessage(Text.of("§cYou cannot place " + itemName + "! (Client-Side)"), false);
            }
            LOGGER.info("Blocked placement of forbidden item: {} (Registry: {}) with {} hand on block {}", itemName, itemIdentifier.getRegistryId(), hand, targetBlock.getName().getString());
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    /**
     * Handles entity usage attempts by players.
     * Prevents interaction if the item is forbidden, with exceptions for item frames and living entities.
     */
    private ActionResult onEntityUse(PlayerEntity player, net.minecraft.world.World world, Hand hand, net.minecraft.entity.Entity entity, @org.jetbrains.annotations.Nullable net.minecraft.util.hit.EntityHitResult hitResult) {
        if (!(player instanceof ClientPlayerEntity clientPlayer)) {
            return ActionResult.PASS;
        }

        ItemStack stackInHand = player.getStackInHand(hand);
        if (stackInHand.isEmpty()) {
            return ActionResult.PASS;
        }

        WorldConfig.ItemIdentifier itemIdentifier = getItemIdentifier(stackInHand);
        if (itemIdentifier == null) {
            LOGGER.warn("onEntityUse: Could not get ItemIdentifier for stack: {}", stackInHand);
            return ActionResult.PASS;
        }

        String itemName = stackInHand.getName().getString();
        WorldConfig worldConfig = WorldConfig.getCurrentWorld();
        boolean isForbidden = worldConfig.isItemForbidden(itemIdentifier);

        LOGGER.debug("onEntityUse: Item: {}, Hand: {}, Forbidden: {}, TargetEntity: {}", itemName, hand, isForbidden, entity.getName().getString());

        if (isForbidden) {
            // Exception: Interacting with Item Frames or LivingEntities (like villagers) with a forbidden item in MAIN_HAND should be allowed.
            // This allows placing items into item frames or trading with villagers.
            if (hand == Hand.MAIN_HAND && (entity instanceof net.minecraft.entity.decoration.ItemFrameEntity || entity instanceof net.minecraft.entity.LivingEntity)) {
                 LOGGER.info("Allowing interaction with entity '{}' with forbidden item '{}' in main hand.", entity.getName().getString(), itemName);
                return ActionResult.PASS;
            }

            // If the item is forbidden and it's not an allowed entity interaction, block it.
            // This might prevent attacking with a forbidden "block" item if it's also a weapon.
            if (ForbiddenBlocksConfig.get().shouldShowMessages()) {
                 clientPlayer.sendMessage(Text.of("§cAction with " + itemName + " on " + entity.getName().getString() + " is blocked! (Client-Side)"), false);
            }
            LOGGER.info("Blocked entity interaction with forbidden item: {} (Registry: {}) with {} hand on entity {}", itemName, itemIdentifier.getRegistryId(), hand, entity.getName().getString());
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
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
        if (stack == null || stack.isEmpty()) {
            player.sendMessage(Text.of("§cYou must hold an item to forbid/allow it."), false);
            return;
        }

        String itemName = stack.getName().getString(); // For messages
        WorldConfig.ItemIdentifier itemIdentifier = getItemIdentifier(stack);

        if (itemIdentifier == null) {
            player.sendMessage(Text.of("§cCould not identify the item: " + itemName), false);
            LOGGER.warn("Could not get ItemIdentifier for stack in forbidItem: {}", stack);
            return;
        }

        WorldConfig config = WorldConfig.getCurrentWorld();
        config.toggleItem(itemIdentifier);
        
        // Add user feedback
        if (ForbiddenBlocksConfig.get().shouldShowMessages()) {
            boolean isForbidden = config.isItemForbidden(itemIdentifier);
            if (isForbidden) {
                player.sendMessage(Text.of("§e" + itemName + " is now forbidden to place. (Client-Side)"), false);
            } else {
                player.sendMessage(Text.of("§a" + itemName + " is now allowed again. (Client-Side)"), false);
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
