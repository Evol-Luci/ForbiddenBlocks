package me.lucievol.forbiddenblocks.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global configuration manager for the ForbiddenBlocks mod.
 * 
 * This class manages mod-wide settings that apply across all worlds and servers.
 * It uses AutoConfig for TOML-based configuration persistence and ClothConfig
 * for the configuration UI.
 * 
 * Key Features:
 * - Persistent configuration using TOML format
 * - Automatic configuration file management
 * - Integration with ModMenu for UI
 * - Thread-safe configuration access
 * 
 * Global Settings:
 * - Message Visibility: Controls whether feedback messages are shown to players
 * 
 * Note: World-specific settings (forbidden blocks list) are handled by {@link WorldConfig}
 */
@Config(name = "forbiddenblocks")
public class ForbiddenBlocksConfig implements ConfigData {
    // Logger instance for this class
    private static final Logger LOGGER = LoggerFactory.getLogger("forbiddenblocks");

    /**
     * Controls whether status messages are shown to the player.
     * This is a global setting that applies across all worlds.
     */
    private boolean showMessages = true;

    /**
     * Initializes the configuration system.
     * Must be called during mod initialization before any config access.
     * Sets up AutoConfig with TOML serialization.
     */
    public static void init() {
        try {
            AutoConfig.register(ForbiddenBlocksConfig.class, Toml4jConfigSerializer::new);
            LOGGER.info("ForbiddenBlocks config system initialized");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize config system", e);
        }
    }

    /**
     * Gets the current configuration instance.
     * Thread-safe access to the global configuration.
     * 
     * @return The current global configuration instance
     */
    public static ForbiddenBlocksConfig get() {
        try {
            return AutoConfig.getConfigHolder(ForbiddenBlocksConfig.class).getConfig();
        } catch (Exception e) {
            LOGGER.error("Error accessing config", e);
            return new ForbiddenBlocksConfig(); // Return default config on error
        }
    }

    /**
     * Checks if status messages should be shown to the player.
     * This affects all feedback messages across the mod.
     * 
     * @return true if messages should be shown, false otherwise
     */
    public boolean shouldShowMessages() {
        return showMessages;
    }

    /**
     * Toggles whether status messages are shown.
     * Automatically saves the configuration after toggling.
     */
    public void toggleMessages() {
        try {
            showMessages = !showMessages;
            LOGGER.info("Message visibility toggled to: {}", showMessages);
            saveConfig();
        } catch (Exception e) {
            LOGGER.error("Error toggling messages", e);
        }
    }

    /**
     * Saves the current configuration to disk.
     * Called automatically when settings are changed.
     * Uses AutoConfig's built-in save mechanism.
     */
    private static void saveConfig() {
        try {
            AutoConfig.getConfigHolder(ForbiddenBlocksConfig.class).save();
            LOGGER.debug("Configuration saved successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to save configuration", e);
        }
    }

    /**
     * Creates the configuration screen for ModMenu integration.
     * This screen allows users to modify global settings through a GUI.
     * 
     * Features:
     * - Message visibility toggle with tooltip
     * - Automatic saving of changes
     * - Clean, user-friendly interface
     * 
     * @param parent The parent screen to return to
     * @return The configuration screen
     */
    public static net.minecraft.client.gui.screen.Screen createConfigScreen(net.minecraft.client.gui.screen.Screen parent) {
        try {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.of("Forbidden Blocks Settings"));

            ConfigCategory general = builder.getOrCreateCategory(Text.of("General"));
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // Add toggle for message visibility with tooltip
            general.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Messages"), get().showMessages)
                    .setDefaultValue(true)
                    .setTooltip(Text.of("Show feedback messages when toggling blocks or changing settings"))
                    .setSaveConsumer(value -> {
                        get().showMessages = value;
                        saveConfig();
                    })
                    .build());

            return builder.build();
        } catch (Exception e) {
            LOGGER.error("Error creating config screen", e);
            return parent; // Return to parent screen on error
        }
    }

    /**
     * Validates the configuration after loading.
     * Currently performs basic validation only.
     * Override from ConfigData interface.
     */
    @Override
    public void validatePostLoad() throws ValidationException {
        // Basic validation only for now
        // Future versions may add more complex validation
        ConfigData.super.validatePostLoad();
    }
}
