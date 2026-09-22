package com.blockai.ai.knowledge;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import com.blockai.gathering.ResourceCategory;

public class MinecraftKnowledgeBase {
    
    private static MinecraftKnowledgeBase instance;
    private final MinecraftServer server;
    
    // Knowledge maps
    private final Map<String, List<String>> toolProgression = new HashMap<>();
    private final Map<String, String> toolRequirements = new HashMap<>();
    private final Map<String, JsonObject> resourceCategories = new HashMap<>();
    private final Map<String, List<String>> equivalencies = new HashMap<>();
    private final KnowledgeGraph graph = new KnowledgeGraph();
    
    public static void initialize(MinecraftServer server) {
        instance = new MinecraftKnowledgeBase(server);
        KnowledgeLoader.load(instance);
    }
    
    public static MinecraftKnowledgeBase getInstance() {
        return instance;
    }
    
    private MinecraftKnowledgeBase(MinecraftServer server) {
        this.server = server;
    }
    
    public MinecraftServer getServer() {
        return server;
    }
    
    public void setToolProgression(Map<String, List<String>> map) {
        toolProgression.clear();
        toolProgression.putAll(map);
    }
    
    public void setToolRequirements(Map<String, String> map) {
        toolRequirements.clear();
        toolRequirements.putAll(map);
    }
    
    public void setResourceCategories(Map<String, JsonObject> map) {
        resourceCategories.clear();
        resourceCategories.putAll(map);
    }
    
    public void setEquivalencies(Map<String, List<String>> map) {
        equivalencies.clear();
        equivalencies.putAll(map);
    }
    
    public String getToolRequirement(ResourceCategory category) {
        if (category == null) return null;
        return toolRequirements.get(category.name());
    }
    
    public List<String> getEquivalentBlocks(String categoryName) {
        return equivalencies.get(categoryName);
    }
    
    public JsonObject getResourceKnowledge(String categoryName) {
        return resourceCategories.get(categoryName);
    }
    
    public KnowledgeGraph getGraph() {
        return graph;
    }
}
