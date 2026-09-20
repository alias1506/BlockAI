package com.blockai.knowledge;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class KnowledgeGraph {
    private final Map<String, KnowledgeNode> nodes = new HashMap<>();

    public void addNode(KnowledgeNode node) {
        nodes.put(node.getId(), node);
    }

    public KnowledgeNode getNode(String id) {
        return nodes.get(id);
    }
    
    public Map<String, KnowledgeNode> getAllNodes() {
        return nodes;
    }

    public void loadFromJson(InputStream in) {
        if (in == null) return;
        Gson gson = new Gson();
        try {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
            if (root.has("goals")) {
                JsonObject goalsObj = root.getAsJsonObject("goals");
                for (Map.Entry<String, JsonElement> entry : goalsObj.entrySet()) {
                    String id = entry.getKey();
                    KnowledgeNode node = gson.fromJson(entry.getValue(), KnowledgeNode.class);
                    node.setId(id);
                    addNode(node);
                }
            }
        } catch (Exception e) {
            System.err.println("[BlockAI] Failed to parse KnowledgeGraph JSON: " + e.getMessage());
        }
    }
}
