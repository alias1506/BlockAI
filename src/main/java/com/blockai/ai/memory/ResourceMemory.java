package com.blockai.ai.memory;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.blockai.gathering.ResourceCategory;

public class ResourceMemory {

    private static ResourceMemory instance;
    private Path memoryFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // In-memory representation
    private final List<ResourceDiscovery> resourceDiscoveries = new ArrayList<>();
    private final List<TaskOutcome> taskOutcomes = new ArrayList<>();
    private final List<PathFailure> pathFailures = new ArrayList<>();

    public static void load(MinecraftServer server) {
        instance = new ResourceMemory(server);
        instance.loadFromFile();
    }

    public static ResourceMemory getInstance() {
        return instance;
    }

    private ResourceMemory(MinecraftServer server) {
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        Path blockAiDir = worldDir.resolve("blockai");
        try {
            Files.createDirectories(blockAiDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.memoryFile = blockAiDir.resolve("memory.json");
    }

    private void loadFromFile() {
        if (!Files.exists(memoryFile)) return;
        
        try {
            String json = Files.readString(memoryFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            
            if (root.has("resourceDiscoveries")) {
                JsonArray arr = root.getAsJsonArray("resourceDiscoveries");
                for (JsonElement el : arr) {
                    resourceDiscoveries.add(gson.fromJson(el, ResourceDiscovery.class));
                }
            }
            if (root.has("taskOutcomes")) {
                JsonArray arr = root.getAsJsonArray("taskOutcomes");
                for (JsonElement el : arr) {
                    taskOutcomes.add(gson.fromJson(el, TaskOutcome.class));
                }
            }
            if (root.has("pathFailures")) {
                JsonArray arr = root.getAsJsonArray("pathFailures");
                for (JsonElement el : arr) {
                    pathFailures.add(gson.fromJson(el, PathFailure.class));
                }
            }
            decayOldEntries();
        } catch (Exception e) {
            System.err.println("[BlockAI] Failed to load learning memory: " + e.getMessage());
        }
    }

    private void saveToFile() {
        try {
            JsonObject root = new JsonObject();
            root.add("resourceDiscoveries", gson.toJsonTree(resourceDiscoveries));
            root.add("taskOutcomes", gson.toJsonTree(taskOutcomes));
            root.add("pathFailures", gson.toJsonTree(pathFailures));
            
            Files.writeString(memoryFile, gson.toJson(root));
        } catch (IOException e) {
            System.err.println("[BlockAI] Failed to save learning memory: " + e.getMessage());
        }
    }

    public void recordResourceDiscovery(ResourceCategory category, String blockId, BlockPos pos) {
        if (category == null) return;
        
        // Update existing or add new
        boolean found = false;
        for (ResourceDiscovery d : resourceDiscoveries) {
            if (d.category == category && d.blockId.equals(blockId) && 
                d.x == pos.getX() && d.y == pos.getY() && d.z == pos.getZ()) {
                d.lastSeen = Instant.now().toEpochMilli();
                d.confidence = Math.min(1.0, d.confidence + 0.1);
                found = true;
                break;
            }
        }
        
        if (!found) {
            resourceDiscoveries.add(new ResourceDiscovery(category, blockId, pos));
        }
        
        saveToFile();
    }

    public void recordTaskOutcome(String goal, boolean success, String strategy, String details) {
        taskOutcomes.add(new TaskOutcome(goal, success, strategy, details));
        saveToFile();
    }

    public void recordPathFailure(BlockPos from, BlockPos to, String reason) {
        pathFailures.add(new PathFailure(from, to, reason));
        saveToFile();
    }
    
    private void decayOldEntries() {
        long now = Instant.now().toEpochMilli();
        long oneDayMs = 24 * 60 * 60 * 1000L;
        
        resourceDiscoveries.removeIf(d -> {
            long age = now - d.lastSeen;
            if (age > oneDayMs) {
                d.confidence -= 0.2; // decay
            }
            return d.confidence <= 0;
        });
    }

    // --- Data Classes ---

    public static class ResourceDiscovery {
        public ResourceCategory category;
        public String blockId;
        public int x, y, z;
        public long lastSeen;
        public double confidence;

        public ResourceDiscovery(ResourceCategory category, String blockId, BlockPos pos) {
            this.category = category;
            this.blockId = blockId;
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
            this.lastSeen = Instant.now().toEpochMilli();
            this.confidence = 0.8;
        }
    }

    public static class TaskOutcome {
        public String goal;
        public boolean success;
        public String strategy;
        public String details;
        public long timestamp;

        public TaskOutcome(String goal, boolean success, String strategy, String details) {
            this.goal = goal;
            this.success = success;
            this.strategy = strategy;
            this.details = details;
            this.timestamp = Instant.now().toEpochMilli();
        }
    }

    public static class PathFailure {
        public int fromX, fromY, fromZ;
        public int toX, toY, toZ;
        public String reason;
        public long timestamp;

        public PathFailure(BlockPos from, BlockPos to, String reason) {
            this.fromX = from.getX(); this.fromY = from.getY(); this.fromZ = from.getZ();
            this.toX = to.getX(); this.toY = to.getY(); this.toZ = to.getZ();
            this.reason = reason;
            this.timestamp = Instant.now().toEpochMilli();
        }
    }
}
