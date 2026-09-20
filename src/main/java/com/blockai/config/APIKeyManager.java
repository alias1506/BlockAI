package com.blockai.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class APIKeyManager {
    private static final String KEY_PROPERTY = "groq_api_key";
    private static String currentKey = null;

    private static Path getConfigFile(MinecraftServer server) {
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        return worldDir.resolve("blockai_api.properties");
    }

    public static void load(MinecraftServer server) {
        if (server == null) return;
        currentKey = null; // reset before loading
        Path path = getConfigFile(server);
        if (Files.exists(path)) {
            try {
                Properties props = new Properties();
                props.load(Files.newInputStream(path));
                currentKey = props.getProperty(KEY_PROPERTY);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void save(MinecraftServer server, String key) {
        if (server == null) return;
        currentKey = key;
        Path path = getConfigFile(server);
        try {
            Properties props = new Properties();
            if (key != null && !key.trim().isEmpty()) {
                props.setProperty(KEY_PROPERTY, key.trim());
            }
            props.store(Files.newOutputStream(path), "BlockAI API Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void clear() {
        currentKey = null;
    }

    public static String getKey() {
        return currentKey;
    }

    public static boolean hasKey() {
        return currentKey != null && !currentKey.trim().isEmpty();
    }
}
