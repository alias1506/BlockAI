package com.blockai.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BlockAIConfig {
    public static Path getConfigDir() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("blockai");
        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return configDir;
    }
}
