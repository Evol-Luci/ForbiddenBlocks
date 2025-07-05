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
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.CraftingTableBlock; // Added import
import net.minecraft.block.AnvilBlock; // Added import
import net.minecraft.block.GrindstoneBlock; // Added import
import net.minecraft.block.StonecutterBlock; // Added import
import net.minecraft.block.CartographyTableBlock; // Added import
import net.minecraft.block.FletchingTableBlock; // Added import
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.util.Map;
import java.util.TreeMap;
import java.util.Optional;
import net.minecraft.registry.entry.RegistryEntry;


/**
 * Main client-side implementation of the ForbiddenBlocks mod.
 */
public class ForbiddenBlocksClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("forbiddenblocks");
    private static final Object KEY_LOCK = new Object();
    private volatile boolean isHandlingKeyPress = false;
    private static final Gson GSON = new GsonBuilder().create();
    private static boolean isConnected = false;
    private static String lastConnectedServer = "";

    public static void updateConnectionState(boolean connected, String serverAddress) {
        LOGGER.info("CONNECTION UPDATE - Previous state: Connected={}, Server={}", isConnected, lastConnectedServer);
        isConnected = connected;
        lastConnectedServer = serverAddress;
        LOGGER.info("CONNECTION UPDATE - New state: Connected={}, Server={}", connected, serverAddress);
        LOGGER.info("CONNECTION UPDATE - Requesting WorldConfig update");
        WorldConfig.updateForCurrentWorld();
    }

    public static boolean isConnected() {
        return isConnected;
    }

    public static String getLastConnectedServer() {
        return lastConnectedServer;
    }

    private static final KeyBinding FORBID_KEY = new KeyBinding(
            "key.forbiddenblocks.forbid",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "category.forbiddenblocks.keys"
    );

    private static final KeyBinding TOGGLE_MESSAGES_KEY = new KeyBinding(
            "key.forbiddenblocks.toggle_messages",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "category.forbiddenblocks.keys"
    );

    @Override
    public void onInitializeClient() {
        ForbiddenBlocksConfig.init();
        KeyBindingHelper.registerKeyBinding(FORBID_KEY);
        KeyBindingHelper.registerKeyBinding(TOGGLE_MESSAGES_KEY);
        UseBlockCallback.EVENT.register(this::onBlockUse);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(this::onEntityUse);

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

    private boolean isValidGameState(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        if (!client.isRunning() || client.currentScreen != null) {
            return false;
        }
        return true;
    }

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
            String displayName = stack.getName().getString();
            LOGGER.debug("getItemIdentifier: Processing item - Registry ID: {}, Display Name: {}", registryId, displayName);

            ComponentMap currentComponents = stack.getComponents();
            Map<String, JsonElement> componentJsonMap = new TreeMap<>();

            for (ComponentType<?> componentType : Registries.DATA_COMPONENT_TYPE) {
                if (stack.contains(componentType)) {
                    Object actualValue = stack.get(componentType);
                    Identifier componentTypeId = Registries.DATA_COMPONENT_TYPE.getId(componentType);

                    if (actualValue instanceof Optional) {
                        actualValue = ((Optional<?>) actualValue).orElse(null);
                    }

                    final Object valueForRegistryEntryToString = actualValue; // For use in lambda if actualValue is a RegistryEntry
                    if (actualValue instanceof RegistryEntry) {
                        final Identifier currentComponentIdForLog = componentTypeId;
                        actualValue = ((RegistryEntry<?>) actualValue).getKey()
                                .map(key -> key.getValue().toString())
                                .orElseGet(() -> {
                                    String idForLog = (currentComponentIdForLog != null) ? currentComponentIdForLog.toString() : componentType.toString();
                                    LOGGER.warn("getItemIdentifier: Component {} for item {} is a RegistryEntry without a key. Using its toString() as fallback.", idForLog, registryId);
                                    return valueForRegistryEntryToString.toString(); // Use the effectively final variable
                                });
                    }

                    if (componentTypeId != null) {
                        if (actualValue != null) {
                            LOGGER.debug("getItemIdentifier: Found component - ID: {}, Value Class: {}", componentTypeId.toString(), actualValue.getClass().getName());
                            try {
                                componentJsonMap.put(componentTypeId.toString(), GSON.toJsonTree(actualValue));
                            } catch (Exception e_comp) {
                                LOGGER.error("getItemIdentifier: Failed to serialize component {} for item {}. Value Class: {}. Error: {}", componentTypeId.toString(), registryId, actualValue.getClass().getName(), e_comp.getMessage(), e_comp);
                            }
                        } else {
                            LOGGER.debug("getItemIdentifier: Component {} is present but its value is null for item {}.", componentTypeId.toString(), registryId);
                            componentJsonMap.put(componentTypeId.toString(), com.google.gson.JsonNull.INSTANCE);
                        }
                    } else {
                         LOGGER.warn("getItemIdentifier: ComponentType {} has null ID in registry for item {}. Skipping.", componentType, registryId);
                    }
                }
            }
            String componentsJson = GSON.toJson(componentJsonMap);
            LOGGER.debug("getItemIdentifier: Final components JSON for {}: {}", registryId, componentsJson);

            WorldConfig.ItemIdentifier resultIdentifier = new WorldConfig.ItemIdentifier(registryId, displayName, componentsJson);
            LOGGER.debug("getItemIdentifier: Created ItemIdentifier: {}", resultIdentifier.toString());
            return resultIdentifier;
        } catch (Exception e) {
            LOGGER.error("Error getting item identifier for stack " + stack.toString(), e);
            return null;
        }
    }

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
            return ActionResult.PASS;
        }
        String itemName = stackInHand.getName().getString();
        WorldConfig worldConfig = WorldConfig.getCurrentWorld();
        boolean isForbidden = worldConfig.isItemForbidden(itemIdentifier);
        LOGGER.debug("onBlockUse: Item: {}, Hand: {}, Forbidden: {}, Target: {}", itemName, hand, isForbidden, hitResult.getBlockPos());

        if (isForbidden) {
            net.minecraft.block.BlockState targetBlockState = world.getBlockState(hitResult.getBlockPos());
            net.minecraft.block.Block targetBlock = targetBlockState.getBlock();
            // Updated condition to include TrapdoorBlock and FenceGateBlock
            if (hand == Hand.MAIN_HAND &&
                (targetBlock instanceof net.minecraft.block.BlockEntityProvider ||
                 targetBlock instanceof net.minecraft.block.DoorBlock ||
                 targetBlock instanceof net.minecraft.block.TrapdoorBlock ||
                 targetBlock instanceof net.minecraft.block.FenceGateBlock ||
                 targetBlock instanceof net.minecraft.block.CraftingTableBlock ||
                 targetBlock instanceof net.minecraft.block.AnvilBlock ||
                 targetBlock instanceof net.minecraft.block.GrindstoneBlock ||
                 targetBlock instanceof net.minecraft.block.StonecutterBlock ||
                 targetBlock instanceof net.minecraft.block.CartographyTableBlock ||
                 targetBlock instanceof net.minecraft.block.FletchingTableBlock)) {
                LOGGER.info("Allowing interaction with interactive block '{}' with forbidden item '{}' in main hand.", targetBlock.getName().getString(), itemName);
                return ActionResult.PASS;
            }
            if (ForbiddenBlocksConfig.get().shouldShowMessages()) {
                clientPlayer.sendMessage(Text.of("§cYou cannot place " + itemName + "! (Client-Side)"), false);
            }
            LOGGER.info("Blocked placement of forbidden item: {} (Registry: {}) with {} hand on block {}", itemName, itemIdentifier.getRegistryId(), hand, targetBlock.getName().getString());
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

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
            if (hand == Hand.MAIN_HAND && (entity instanceof net.minecraft.entity.decoration.ItemFrameEntity || entity instanceof net.minecraft.entity.LivingEntity)) {
                 LOGGER.info("Allowing interaction with entity '{}' with forbidden item '{}' in main hand.", entity.getName().getString(), itemName);
                return ActionResult.PASS;
            }
            if (ForbiddenBlocksConfig.get().shouldShowMessages()) {
                 clientPlayer.sendMessage(Text.of("§cAction with " + itemName + " on " + entity.getName().getString() + " is blocked! (Client-Side)"), false);
            }
            LOGGER.info("Blocked entity interaction with forbidden item: {} (Registry: {}) with {} hand on entity {}", itemName, itemIdentifier.getRegistryId(), hand, entity.getName().getString());
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

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
        String itemName = stack.getName().getString();
        WorldConfig.ItemIdentifier itemIdentifier = getItemIdentifier(stack);
        if (itemIdentifier == null) {
            player.sendMessage(Text.of("§cCould not identify the item: " + itemName), false);
            LOGGER.warn("Could not get ItemIdentifier for stack in forbidItem: {}", stack);
            return;
        }
        WorldConfig config = WorldConfig.getCurrentWorld();
        config.toggleItem(itemIdentifier);
        if (ForbiddenBlocksConfig.get().shouldShowMessages()) {
            boolean isForbidden = config.isItemForbidden(itemIdentifier);
            if (isForbidden) {
                player.sendMessage(Text.of("§e" + itemName + " is now forbidden to place. (Client-Side)"), false);
            } else {
                player.sendMessage(Text.of("§a" + itemName + " is now allowed again. (Client-Side)"), false);
            }
        }
    }

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
