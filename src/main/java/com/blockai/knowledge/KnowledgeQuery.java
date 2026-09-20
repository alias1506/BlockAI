package com.blockai.knowledge;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class KnowledgeQuery {

    /**
     * Determine what tool is required for a resource category.
     */
    public static String getRequiredToolForCategory(com.blockai.ai.ResourceCategory category) {
        if (category == null) return null;
        
        // Query the loaded JSON knowledge base
        MinecraftKnowledgeBase kb = MinecraftKnowledgeBase.getInstance();
        if (kb != null) {
            String tool = kb.getToolRequirement(category);
            if (tool != null) return tool;
            
            JsonObject catData = kb.getResourceKnowledge(category.name());
            if (catData != null && catData.has("default_tool")) {
                return catData.get("default_tool").getAsString();
            }
        }
        
        // Fallbacks based on category semantics
        if (category.name().contains("WOOD")) return "axe";
        if (category.name().contains("STONE") || category.name().contains("ORE")) return "pickaxe";
        if (category.name().contains("DIRT") || category.name().contains("SAND") || category.name().contains("GRAVEL")) return "shovel";
        
        return null;
    }

    /**
     * Determines if a specific item is a tool of the specified category.
     * Works by checking the item ID string (e.g. "wooden_pickaxe" contains "pickaxe").
     */
    public static boolean isToolOfType(String itemId, String toolCategory) {
        if (itemId == null || toolCategory == null) return false;
        return itemId.toLowerCase().contains(toolCategory.toLowerCase());
    }
    
    /**
     * Returns the basic crafting prerequisite for a generic tool category.
     * Very simplified version for early game.
     */
    public static String getToolPrerequisite(String toolCategory) {
        // Just return wooden variant as a default prerequisite
        if (toolCategory == null) return null;
        return "minecraft:wooden_" + toolCategory.toLowerCase();
    }
}
