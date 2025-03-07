package me.lucievol.forbiddenblocks;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.lucievol.forbiddenblocks.config.ForbiddenBlocksConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod class for ForbiddenBlocks.
 * 
 * This class serves as:
 * 1. The entry point for mod initialization
 * 2. ModMenu integration point for configuration UI
 * 3. Future expansion point for potential server-side features
 * 
 * Key Features:
 * - ModMenu integration for easy configuration access
 * - Logging setup for mod-wide use
 * - Extensible structure for future updates
 * 
 * Note: Currently, most functionality is client-side (see {@link ForbiddenBlocksClient}).
 * This class primarily handles ModMenu integration and serves as a foundation
 * for potential future server-side features.
 */
public class ForbiddenBlocks implements ModInitializer, ModMenuApi {
    /**
     * Logger instance for mod-wide use.
     * Uses SLF4J for consistent logging across the mod.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("forbiddenblocks");

    /**
     * Initializes the mod.
     * Called by Fabric during game startup.
     * 
     * Currently minimal as most functionality is client-side,
     * but provides a hook for future server-side features.
     */
    @Override
    public void onInitialize() {
        LOGGER.info("ForbiddenBlocks initialized");
    }

    /**
     * Creates the configuration screen factory for ModMenu integration.
     * This allows users to access mod settings through ModMenu's UI.
     * 
     * @param parent The parent screen to return to
     * @return A factory that creates the mod's configuration screen
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ForbiddenBlocksConfig::createConfigScreen;
    }
}