package me.lucievol.forbiddenblocks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Global configuration manager for the ForbiddenBlocks mod.
 * Replaces the previous OWO-based configuration.
 */
public class ForbiddenBlocksConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("forbiddenblocks");
    private static final String CONFIG_FILE_NAME = "config/forbiddenblocks.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean showMessages = true;

    // Transient field to avoid serialization, used for file operations
    private transient File configFile;

    public ForbiddenBlocksConfig() {
        // Default constructor for GSON
    }

    public static ForbiddenBlocksConfig createAndLoad() {
        ForbiddenBlocksConfig config = new ForbiddenBlocksConfig();
        config.load();
        return config;
    }

    private File getConfigFile() {
        if (configFile == null) {
            File minecraftDir = MinecraftClient.getInstance().runDirectory;
            configFile = new File(minecraftDir, CONFIG_FILE_NAME);
        }
        return configFile;
    }

    public void load() {
        File file = getConfigFile();
        if (!file.exists()) {
            save(); // Save defaults
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            ForbiddenBlocksConfig loadedConfig = GSON.fromJson(reader, ForbiddenBlocksConfig.class);
            if (loadedConfig != null) {
                this.showMessages = loadedConfig.showMessages;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load ForbiddenBlocks global config", e);
        }
    }

    public void save() {
        File file = getConfigFile();
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save ForbiddenBlocks global config", e);
        }
    }

    public boolean showMessages() {
        return showMessages;
    }

    public void showMessages(boolean showMessages) {
        this.showMessages = showMessages;
        save();
    }
}
