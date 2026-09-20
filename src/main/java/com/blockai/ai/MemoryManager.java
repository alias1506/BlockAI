package com.blockai.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class MemoryManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static void save(MinecraftServer server) {
        try {
            Path savePath = server.getWorldPath(LevelResource.ROOT).resolve("blockai_data.json");
            File file = savePath.toFile();
            
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(WorldObserver.MEMORY.getAll(), writer);
            }
        } catch (Exception e) {
            System.err.println("[BlockAI] Failed to save memory: " + e.getMessage());
        }
    }
    
    public static void load(MinecraftServer server) {
        WorldObserver.MEMORY.clear();
        try {
            Path savePath = server.getWorldPath(LevelResource.ROOT).resolve("blockai_data.json");
            File file = savePath.toFile();
            
            if (file.exists()) {
                Type type = new TypeToken<Map<String, Set<BlockPos>>>(){}.getType();
                try (FileReader reader = new FileReader(file)) {
                    Map<String, Set<BlockPos>> loaded = GSON.fromJson(reader, type);
                    if (loaded != null) {
                        loaded.forEach((k, v) -> {
                            for (BlockPos pos : v) {
                                WorldObserver.MEMORY.addBlock(k, pos);
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[BlockAI] Failed to load memory: " + e.getMessage());
        }
    }
}
