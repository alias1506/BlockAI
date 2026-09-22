package com.blockai.ai.knowledge;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KnowledgeLoader {

    public static void load(MinecraftKnowledgeBase kb) {
        System.out.println("[BlockAI] Loading Local Minecraft Knowledge Base...");
        
        Gson gson = new Gson();
        
        // Load progression.json
        try (InputStream in = KnowledgeLoader.class.getResourceAsStream("/assets/blockai/knowledge/progression.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
                if (root.has("progression")) {
                    Type mapType = new TypeToken<Map<String, List<String>>>(){}.getType();
                    Map<String, List<String>> progMap = gson.fromJson(root.get("progression"), mapType);
                    kb.setToolProgression(progMap);
                }
                if (root.has("tool_requirements")) {
                    Type mapType = new TypeToken<Map<String, String>>(){}.getType();
                    Map<String, String> reqMap = gson.fromJson(root.get("tool_requirements"), mapType);
                    kb.setToolRequirements(reqMap);
                }
            } else {
                System.err.println("[BlockAI] Warning: progression.json not found in resources.");
            }
        } catch (Exception e) {
            System.err.println("[BlockAI] Failed to load progression knowledge: " + e.getMessage());
        }

        // Load resources.json
        try (InputStream in = KnowledgeLoader.class.getResourceAsStream("/assets/blockai/knowledge/resources.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
                if (root.has("categories")) {
                    Map<String, JsonObject> catMap = new HashMap<>();
                    for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("categories").entrySet()) {
                        catMap.put(entry.getKey(), entry.getValue().getAsJsonObject());
                    }
                    kb.setResourceCategories(catMap);
                }
                if (root.has("equivalencies")) {
                    Type mapType = new TypeToken<Map<String, List<String>>>(){}.getType();
                    Map<String, List<String>> equivMap = gson.fromJson(root.get("equivalencies"), mapType);
                    kb.setEquivalencies(equivMap);
                }
            } else {
                System.err.println("[BlockAI] Warning: resources.json not found in resources.");
            }
        } catch (Exception e) {
            System.err.println("[BlockAI] Failed to load resources knowledge: " + e.getMessage());
        }
        
        // Load graph files
        String[] graphFiles = {"overworld.json", "nether.json", "end.json"};
        for (String file : graphFiles) {
            try (InputStream in = KnowledgeLoader.class.getResourceAsStream("/assets/blockai/knowledge/" + file)) {
                if (in != null) {
                    kb.getGraph().loadFromJson(in);
                    System.out.println("[BlockAI] Loaded graph knowledge from: " + file);
                }
            } catch (Exception e) {
                System.err.println("[BlockAI] Failed to load graph knowledge from " + file + ": " + e.getMessage());
            }
        }
        
        System.out.println("[BlockAI] Knowledge Base loaded successfully.");
    }
}
