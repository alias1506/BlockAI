package com.blockai.knowledge;

import com.blockai.ai.ResourceCategory;
import com.blockai.ai.RoadmapTask;
import com.blockai.player.AIPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

public class DependencyResolver {

    /**
     * Resolves dependencies by traversing the Knowledge Graph.
     * Uses Depth-First Search (DFS) to build a prerequisite list, pruning branches
     * where the requirement is already met (e.g. item is in inventory).
     */
    public static List<RoadmapTask> resolveDependencies(RoadmapTask task, AIPlayer player) {
        List<RoadmapTask> plan = new ArrayList<>();
        
        // Translate the Groq task into a Knowledge Graph goal if possible
        String goalId = mapTaskToGoalId(task);
        if (goalId == null) {
            plan.add(task);
            return plan;
        }

        KnowledgeGraph graph = MinecraftKnowledgeBase.getInstance().getGraph();
        if (graph == null) {
            plan.add(task);
            return plan;
        }

        System.out.println("[BlockAI] DependencyResolver: Starting resolution for goal: " + goalId);
        
        List<String> visited = new ArrayList<>();
        List<String> resolutionOrder = new ArrayList<>();
        
        // DFS
        boolean success = resolveNode(goalId, graph, player, visited, resolutionOrder);
        
        if (success) {
            System.out.println("[BlockAI] DependencyResolver: Resolution order: " + resolutionOrder);
            
            // Convert resolved nodes back into RoadmapTasks
            for (String nodeId : resolutionOrder) {
                if (nodeId.equals(goalId)) continue; // The original task will be added at the end
                
                KnowledgeNode node = graph.getNode(nodeId);
                if (node != null && node.getAction() != null) {
                    RoadmapTask subTask = new RoadmapTask(-1, node.getAction().toLowerCase(), "Satisfy " + nodeId);
                    
                    if (node.getType().equals("item")) {
                        String itemName = nodeId.replace("ITEM:", "");
                        // Rough categorization for crafting vs gathering
                        if (node.getAction().equals("CRAFT")) {
                            subTask.setResource("CRAFTING", itemName, 1);
                        } else {
                            // Check if itemName is a generic category string
                            ResourceCategory cat = ResourceCategory.fromName(itemName);
                            if (cat != null) {
                                subTask.setResource(cat.name(), null, 1);
                            } else {
                                subTask.setResource("GATHERING", itemName, 1);
                            }
                        }
                    }
                    
                    plan.add(subTask);
                }
            }
        } else {
            System.out.println("[BlockAI] DependencyResolver: Failed to resolve full dependency tree for " + goalId);
        }
        
        // Add the original task at the end of the prerequisites
        plan.add(task);
        return plan;
    }
    
    private static boolean resolveNode(String nodeId, KnowledgeGraph graph, AIPlayer player, List<String> visited, List<String> order) {
        if (visited.contains(nodeId)) {
            return true; // Already processed
        }
        
        KnowledgeNode node = graph.getNode(nodeId);
        if (node == null) {
            System.out.println("[BlockAI] DependencyResolver Warning: Node not found in graph: " + nodeId);
            return false; 
        }
        
        // Check if condition is already satisfied (Pruning)
        if (isSatisfied(node, player)) {
            System.out.println("[BlockAI] DependencyResolver: Node " + nodeId + " is already satisfied. Pruning branch.");
            return true;
        }
        
        visited.add(nodeId);
        
        // Resolve children (requirements) first
        for (String reqId : node.getRequires()) {
            boolean reqSuccess = resolveNode(reqId, graph, player, visited, order);
            if (!reqSuccess) {
                return false;
            }
        }
        
        // After all children are resolved, add this node to the execution order
        order.add(nodeId);
        return true;
    }
    
    private static boolean isSatisfied(KnowledgeNode node, AIPlayer player) {
        if (node.getType().equals("item")) {
            String itemName = node.getId().replace("ITEM:", "");
            
            // If the item name is a semantic category, check if any accepted item is in the inventory
            ResourceCategory cat = ResourceCategory.fromName(itemName);
            
            // Check inventory
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                
                String invItemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                
                if (cat != null) {
                    if (cat.matches(invItemId)) return true;
                } else if (invItemId.equals(itemName)) {
                    return true;
                }
            }
            
            // Also check for pickaxe progression logic. If we want a wooden pickaxe but have a better one:
            if (itemName.equals("minecraft:wooden_pickaxe")) {
                if (hasItem(player, "minecraft:stone_pickaxe") || 
                    hasItem(player, "minecraft:iron_pickaxe") ||
                    hasItem(player, "minecraft:diamond_pickaxe")) {
                    return true;
                }
            } else if (itemName.equals("minecraft:stone_pickaxe")) {
                if (hasItem(player, "minecraft:iron_pickaxe") ||
                    hasItem(player, "minecraft:diamond_pickaxe")) {
                    return true;
                }
            }

            return false;
        }
        
        // Example logic for other types can be expanded later (e.g. REACH_NETHER checks dimension)
        return false;
    }
    
    private static String mapTaskToGoalId(RoadmapTask task) {
        if (MinecraftKnowledgeBase.getInstance().getGraph().getNode(task.getDescription()) != null) {
            return task.getDescription(); // Direct goal ID match (e.g. from debug command)
        }
        
        String type = task.getType().toLowerCase();
        
        if (type.contains("mine") || type.contains("gather") || type.contains("collect")) {
            String specific = task.getSpecificItem();
            if (specific != null) {
                return "ITEM:" + specific;
            }
            
            String cat = task.getResourceCategory();
            if (cat != null) {
                if (cat.equals("STONE")) return "ITEM:STONE";
                if (cat.equals("WOOD_LOG")) return "ITEM:WOOD_LOG";
            }
        } else if (type.contains("combat")) {
            if (task.getDescription().toLowerCase().contains("dragon")) return "DEFEAT_ENDER_DRAGON";
        }
        
        return null;
    }
    
    private static boolean hasItem(AIPlayer player, String exactId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String invItemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (invItemId.equals(exactId)) return true;
            }
        }
        return false;
    }
}
