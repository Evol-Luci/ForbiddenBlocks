package me.lucievol.forbiddenblocks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World-specific configuration manager for the ForbiddenBlocks mod.
 * 
 * This class manages the list of forbidden blocks/items for each Minecraft world or server.
 * Each world/server has its own configuration file stored in JSON format:
 * - Single player worlds: config/forbiddenblocks/worlds/singleplayer_[worldname].json
 * - Multiplayer servers: config/forbiddenblocks/worlds/multiplayer_[serveraddress].json
 * 
 * Key Features:
 * - Thread-safe configuration handling using synchronization and concurrent data structures
 * - Automatic per-world/server configuration file management
 * - Real-time saving of changes to prevent data loss
 * - Comprehensive error handling and logging
 * - Connection-based server identification for reliable multiplayer support
 * 
 * Note: This handles world-specific settings only. Global mod settings are managed by {@link ForbiddenBlocksConfig}
 */
public class WorldConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("forbiddenblocks");
    private static final String CONFIG_DIR = "config/forbiddenblocks/worlds";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConcurrentHashMap<String, WorldConfig> WORLD_CONFIGS = new ConcurrentHashMap<>();
    private static final Object CONFIG_LOCK = new Object();
    private static final TypeToken<HashSet<ItemIdentifier>> ITEM_IDENTIFIER_SET_TYPE = new TypeToken<HashSet<ItemIdentifier>>(){};
    
    // Track the current connection for multiplayer identification
    private static String currentConnectionId = null;
    private static final Object CONNECTION_LOCK = new Object();

    public static class ItemIdentifier {
        private final String registryId;
        private final String name;
        // Store all components as a sorted JSON string for consistent hashing and equality.
        // This replaces the 'lore' field, as lore is just one of many components.
        private final String componentsJson;

        public ItemIdentifier(String registryId, String name, String componentsJson) {
            this.registryId = registryId;
            this.name = name;
            this.componentsJson = componentsJson;
        }

        public String getRegistryId() {
            return registryId;
        }

        public String getName() {
            return name;
        }

        public String getComponentsJson() {
            return componentsJson;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemIdentifier that = (ItemIdentifier) o;
            return Objects.equals(registryId, that.registryId) &&
                    Objects.equals(name, that.name) &&
                    Objects.equals(componentsJson, that.componentsJson);
        }

        @Override
        public int hashCode() {
            return Objects.hash(registryId, name, componentsJson);
        }

        @Override
        public String toString() {
            // Default GSON serialization should be fine as it will include all fields.
            // For logging or debugging, you might want a more custom string.
            return "ItemIdentifier{" +
                    "registryId='" + registryId + '\'' +
                    ", name='" + name + '\'' +
                    ", componentsJson='" + componentsJson + '\'' +
                    '}';
        }
    }

    private final String worldId;
    private final Set<ItemIdentifier> forbiddenItems;
    private final File configFile;
    private volatile boolean isDirty;

    private WorldConfig(String worldId) {
        this.worldId = worldId;
        this.forbiddenItems = ConcurrentHashMap.<ItemIdentifier>newKeySet();
        this.configFile = getConfigFile(worldId);
        this.isDirty = false;
        load();
    }

    /**
     * Saves all world configurations to disk.
     * This should be called when the client is shutting down.
     */
    public static void saveAll() {
        LOGGER.info("Saving all world configurations");
        for (WorldConfig config : WORLD_CONFIGS.values()) {
            config.save();
        }
    }

    /**
     * Updates the current connection identifier when connecting to a server.
     * This should be called when the client connects to a server or loads a world.
     * 
     * @param networkHandler The client's network handler, or null if disconnected
     */
    public static void updateConnection(ClientPlayNetworkHandler networkHandler) {
        synchronized (CONNECTION_LOCK) {
            if (networkHandler == null) {
                LOGGER.info("Network connection closed, clearing connection ID");
                currentConnectionId = null;
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.isInSingleplayer()) {
                String worldName = client.getServer() != null ? 
                    client.getServer().getSaveProperties().getLevelName() : "unknown";
                currentConnectionId = "singleplayer_" + worldName;
                LOGGER.info("Updated connection: Singleplayer world '{}'", worldName);
            } else {
                ServerInfo serverInfo = client.getCurrentServerEntry();
                String serverAddress = "unknown";
                
                if (serverInfo != null) {
                    // Use the server address from the server info for consistency
                    serverAddress = serverInfo.address;
                    LOGGER.info("Server info available: {}", serverInfo.address);
                } else {
                    // Fallback to network handler info if server info isn't available
                    try {
                        serverAddress = networkHandler.getConnection().getAddress().toString();
                        // Clean up address format (remove "/" prefix if present)
                        if (serverAddress.startsWith("/")) {
                            serverAddress = serverAddress.substring(1);
                        }
                        LOGGER.info("Using network handler address: {}", serverAddress);
                    } catch (Exception e) {
                        LOGGER.warn("Could not get server address from network handler", e);
                    }
                }
                
                // Sanitize server address to create a valid filename
                serverAddress = serverAddress.replace(':', '_');
                currentConnectionId = "multiplayer_" + serverAddress;
                LOGGER.info("Updated connection: Multiplayer server '{}' -> config ID '{}'", serverAddress, currentConnectionId);
            }
            
            // Ensure we clear and reload the correct config
            WorldConfig config = getCurrentWorld();
            config.load();
        }
    }

    /**
     * Gets or creates the WorldConfig instance for the current world/server.
     * Uses the active network connection for reliable server identification.
     * 
     * @return WorldConfig instance for the current world/server
     */
    public static WorldConfig getCurrentWorld() {
        String worldId;
        synchronized (CONNECTION_LOCK) {
            if (currentConnectionId != null) {
                worldId = currentConnectionId;
            } else {
                // Fallback to traditional method if no connection is established
                MinecraftClient client = MinecraftClient.getInstance();
                if (client == null) {
                    LOGGER.warn("No client instance available");
                    worldId = "unknown";
                } else if (client.isInSingleplayer()) {
                    String worldName = client.getServer() != null ? 
                        client.getServer().getSaveProperties().getLevelName() : "unknown";
                    worldId = "singleplayer_" + worldName;
                } else {
                    ServerInfo serverInfo = client.getCurrentServerEntry();
                    worldId = serverInfo != null ? 
                        "multiplayer_" + serverInfo.address : "unknown";
                }
            }
        }

        LOGGER.debug("Getting config for world: {}", worldId);
        return WORLD_CONFIGS.computeIfAbsent(worldId, WorldConfig::new);
    }

    /**
     * Updates the configuration for the current world/server.
     * This is called when the connection state changes to ensure the configuration
     * matches the current world/server.
     */
    public static void updateForCurrentWorld() {
        String previousId = currentConnectionId;
        WorldConfig config = getCurrentWorld();
        LOGGER.info("WORLDCONFIG: Updated config - Previous ID: {}, Current ID: {}", previousId, config.worldId);
        LOGGER.info("WORLDCONFIG: Loading configuration for world: {}", config.worldId);
        config.load();
    }

    private static String sanitizeFileName(String fileName) {
        // Replace invalid filename characters with underscores
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static File getConfigFile(String worldId) {
        try {
            // Get the Minecraft directory
            File minecraftDir = MinecraftClient.getInstance().runDirectory;
            File configDir = new File(minecraftDir, CONFIG_DIR);
            
            if (!configDir.exists() && !configDir.mkdirs()) {
                LOGGER.error("Failed to create config directory: {}", configDir.getAbsolutePath());
                throw new IOException("Could not create config directory");
            }
            
            // Sanitize the worldId to create a valid filename
            String safeWorldId = sanitizeFileName(worldId);
            LOGGER.debug("WORLDCONFIG: Sanitized world ID '{}' to '{}'", worldId, safeWorldId);
            
            File configFile = new File(configDir, safeWorldId + ".json");
            LOGGER.info("Config file path: {}", configFile.getAbsolutePath());
            return configFile;
        } catch (Exception e) {
            LOGGER.error("Error creating config file for world: " + worldId, e);
            return new File("fallback.json");
        }
    }

    private void load() {
        synchronized (CONFIG_LOCK) {
            LOGGER.info("WORLDCONFIG: Attempting to load config from: {}", configFile.getAbsolutePath());
            
            if (!configFile.exists()) {
                LOGGER.info("WORLDCONFIG: No existing config for world {}, creating new file at {}", worldId, configFile.getAbsolutePath());
                save();
                return;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(configFile, StandardCharsets.UTF_8))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                
                Set<ItemIdentifier> items = GSON.fromJson(content.toString(), ITEM_IDENTIFIER_SET_TYPE.getType());
                
                if (items != null) {
                    forbiddenItems.clear();
                    forbiddenItems.addAll(items);
                    LOGGER.info("WORLDCONFIG: Loaded {} forbidden items for world {}", items.size(), worldId);
                    if (!items.isEmpty()) {
                        LOGGER.info("WORLDCONFIG: Sample of forbidden items: {}", 
                                 items.stream().limit(3).map(Object::toString).collect(java.util.stream.Collectors.joining(", ")));
                    }
                } else {
                    LOGGER.warn("WORLDCONFIG: Loaded null items set from file: {}", configFile.getAbsolutePath());
                }
            } catch (Exception e) {
                LOGGER.error("WORLDCONFIG: Error loading config for world: " + worldId, e);
            }
        }
    }

    private void save() {
        if (!isDirty) {
            LOGGER.debug("Not saving config for world {} as it is not dirty", worldId);
            return;
        }

        synchronized (CONFIG_LOCK) {
            try {
                if (!configFile.getParentFile().exists()) {
                    LOGGER.info("Creating parent directories for config file: {}", configFile.getAbsolutePath());
                    configFile.getParentFile().mkdirs();
                }
                
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile, StandardCharsets.UTF_8))) {
                    String json = GSON.toJson(forbiddenItems);
                    writer.write(json);
                    isDirty = false;
                    LOGGER.info("Saved config for world {} with {} items to {}", worldId, forbiddenItems.size(), configFile.getAbsolutePath());
                }
            } catch (Exception e) {
                LOGGER.error("Error saving config for world: " + worldId, e);
            }
        }
    }

    public void toggleItem(ItemIdentifier itemIdentifier) {
        if (itemIdentifier == null) {
            LOGGER.warn("WORLDCONFIG: Attempted to toggle null item identifier");
            return;
        }

        try {
            synchronized (CONFIG_LOCK) {
                LOGGER.info("WORLDCONFIG: Toggling item {} in world {} (config file: {})", 
                          itemIdentifier, worldId, configFile.getAbsolutePath());
                
                if (forbiddenItems.contains(itemIdentifier)) {
                    forbiddenItems.remove(itemIdentifier);
                    LOGGER.info("WORLDCONFIG: Removed {} from forbidden items", itemIdentifier);
                } else {
                    forbiddenItems.add(itemIdentifier);
                    LOGGER.info("WORLDCONFIG: Added {} to forbidden items", itemIdentifier);
                }
                isDirty = true;
                
                // Force immediate save to ensure persistence
                LOGGER.info("WORLDCONFIG: Triggering immediate save after toggle");
                save();
                
                // Verify save worked by checking file exists
                if (configFile.exists()) {
                    LOGGER.info("WORLDCONFIG: Config file exists after save: {}, size: {} bytes", 
                              configFile.getAbsolutePath(), configFile.length());
                } else {
                    LOGGER.error("WORLDCONFIG: Config file does not exist after save attempt: {}", 
                               configFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            LOGGER.error("WORLDCONFIG: Error toggling item: " + itemIdentifier, e);
        }
    }

    public boolean isItemForbidden(ItemIdentifier itemIdentifier) {
        if (itemIdentifier == null) {
            return false;
        }
        boolean forbidden = forbiddenItems.contains(itemIdentifier);
        LOGGER.debug("Checking if item {} is forbidden: {}", itemIdentifier, forbidden);
        return forbidden;
    }
}
