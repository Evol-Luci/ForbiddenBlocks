package me.lucievol.forbiddenblocks;

import me.lucievol.forbiddenblocks.config.ForbiddenBlocksConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod class for ForbiddenBlocks.
 *
 * This class serves as:
 * 1. The entry point for mod initialization
 * 2. Configuration initialization
 * 3. Future expansion point for potential server-side features
 *
 * Key Features:
 * - Configuration system initialization
 * - Logging setup for mod-wide use
 * - Extensible structure for future updates
 *
 * Note: Currently, most functionality is client-side (see
 * {@link ForbiddenBlocksClient}).
 * This class primarily handles configuration initialization and serves as a
 * foundation
 * for potential future server-side features.
 */
public class ForbiddenBlocks implements ModInitializer {
    /**
     * Logger instance for mod-wide use.
     * Uses SLF4J for consistent logging across the mod.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("forbiddenblocks");

    /**
     * Global configuration instance.
     * Loaded during mod initialization.
     */
    public static final ForbiddenBlocksConfig CONFIG = ForbiddenBlocksConfig.createAndLoad();

    /**
     * Initializes the mod.
     * Called by Fabric during game startup.
     *
     * Initializes the configuration system and provides a hook for future
     * server-side features.
     */
    @Override
    public void onInitialize() {
        // Initialize the configuration system
        // CONFIG is initialized statically
        LOGGER.info("ForbiddenBlocks initialized");
    }
}