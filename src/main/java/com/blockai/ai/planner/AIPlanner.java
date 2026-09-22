package com.blockai.ai.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.blockai.gathering.ResourceCategory;
import com.blockai.groq.AIProvider;

public class AIPlanner implements AIProvider {
    @Override
    public void sendMessageAsync(String contextMessage, Consumer<Roadmap> onSuccess, Consumer<String> onError) {
        String lowerCtx = contextMessage.toLowerCase();
        
        List<RoadmapTask> tasks = new ArrayList<>();
        String summary = "Deterministic local execution.";
        
        // Simple deterministic parser
        if (lowerCtx.contains("collect") || lowerCtx.contains("gather") || lowerCtx.contains("mine") || lowerCtx.contains("get")) {
            // Try to extract quantity
            int qty = 1;
            Matcher m = Pattern.compile("(\\d+)").matcher(lowerCtx);
            if (m.find()) {
                qty = Integer.parseInt(m.group(1));
            }
            // Check for resource types
            ResourceCategory cat = ResourceCategory.fromDescription(lowerCtx);
            if (cat != null) {
                RoadmapTask task = new RoadmapTask(1, "resource_gathering", "Gather " + cat.name());
                task.setResource(cat.name(), null, qty);
                tasks.add(task);
            } else {
                // Try to parse a specific item like "get 10 dirt" or "mine stone"
                String[] words = lowerCtx.split("\\s+");
                for (String word : words) {
                    if (word.equals("collect") || word.equals("gather") || word.equals("mine") || word.equals("get") || word.matches("\\d+")) continue;
                    
                    // Assume it's a specific block/item
                    String itemName = "minecraft:" + word.replace(" ", "_");
                    RoadmapTask task = new RoadmapTask(1, "resource_gathering", "Gather " + itemName);
                    task.setResource(null, itemName, qty);
                    tasks.add(task);
                    break;
                }
            }
        } else if (lowerCtx.contains("make") || lowerCtx.contains("craft") || lowerCtx.contains("build")) {
            int qty = 1;
            Matcher m = Pattern.compile("(\\d+)").matcher(lowerCtx);
            if (m.find()) {
                qty = Integer.parseInt(m.group(1));
            }
            
            String[] words = lowerCtx.split("\\s+");
            StringBuilder item = new StringBuilder();
            boolean foundAction = false;
            for (String word : words) {
                if (word.equals("make") || word.equals("craft") || word.equals("build") || word.equals("a") || word.equals("an") || word.matches("\\d+")) {
                    foundAction = true;
                    continue;
                }
                if (foundAction) {
                    if (item.length() > 0) item.append("_");
                    item.append(word);
                }
            }
            
            if (item.length() > 0) {
                String itemName = "minecraft:" + item.toString().replace(".", "");
                RoadmapTask task = new RoadmapTask(1, "crafting", "Craft " + itemName);
                task.setResource(null, itemName, qty);
                
                tasks.add(task);
            }
        } else if (lowerCtx.contains("clear")) {
            int radius = 10;
            Matcher m = Pattern.compile("(\\d+)").matcher(lowerCtx);
            if (m.find()) {
                radius = Integer.parseInt(m.group(1));
            }
            RoadmapTask task = new RoadmapTask(1, "clear_area", "Clear area of radius " + radius);
            task.setRadius(radius);
            task.setAllowsExcavation(lowerCtx.contains("excavate"));
            tasks.add(task);
        } else if (lowerCtx.contains("excavate")) {
            int width = 5;
            int length = 5;
            Matcher m = Pattern.compile("(\\d+)x(\\d+)").matcher(lowerCtx);
            if (m.find()) {
                width = Integer.parseInt(m.group(1));
                length = Integer.parseInt(m.group(2));
            }
            RoadmapTask task = new RoadmapTask(1, "excavate", "Excavate " + width + "x" + length);
            tasks.add(task);
        }
        
        if (tasks.isEmpty()) {
            onError.accept("AIPlanner could not understand the goal. Please enable Groq or rephrase.");
            return;
        }
        
        Roadmap roadmap = new Roadmap(contextMessage, summary, tasks);
        
        // Simulate async delay
        new Thread(() -> {
            try { Thread.sleep(100); } catch (Exception ignored) {}
            onSuccess.accept(roadmap);
        }).start();
    }
}
