package com.blockai.crafting;

import com.blockai.ai.AIPlayer;

import com.blockai.ai.knowledge.KnowledgeNode;
import com.blockai.ai.knowledge.KnowledgeGraph;
import com.blockai.execution.TaskResult;
import com.blockai.gathering.ResourceCategory;
import com.blockai.ai.knowledge.MinecraftKnowledgeBase;

public class CraftingController {
    
    private String targetItem = null;
    private int targetQuantity = 1;
    private com.blockai.execution.TaskResult state = com.blockai.execution.TaskResult.IDLE;
    
    public void craft(String item, int quantity) {
        this.targetItem = item;
        this.targetQuantity = quantity > 0 ? quantity : 1;
        this.state = com.blockai.execution.TaskResult.RUNNING;
    }
    
    public boolean isIdle() {
        return targetItem == null;
    }
    
    public void tick(AIPlayer player) {
        if (isIdle()) return;
        // Simulating deterministic crafting logic
        System.out.println("[BlockAI] CraftingController: Attempting to craft " + targetItem);
        
        net.minecraft.world.item.Item itemObj = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(targetItem));
        if (itemObj != null && itemObj != net.minecraft.world.item.Items.AIR) {
            // Try to deduct basic ingredients using the KnowledgeGraph
            com.blockai.ai.knowledge.KnowledgeNode node = com.blockai.ai.knowledge.MinecraftKnowledgeBase.getInstance().getGraph().getNode("ITEM:" + targetItem);
            if (node != null && node.getRequires() != null) {
                for (String req : node.getRequires()) {
                    String reqItem = req.replace("ITEM:", "");
                    com.blockai.gathering.ResourceCategory cat = com.blockai.gathering.ResourceCategory.fromName(reqItem);
                    
                    // Deduct 1 item matching the requirement
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
                        if (!stack.isEmpty()) {
                            String invItemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                            if ((cat != null && cat.matches(invItemId)) || invItemId.equals(reqItem)) {
                                stack.shrink(1);
                                break;
                            }
                        }
                    }
                }
            }
            
            player.getInventory().add(new net.minecraft.world.item.ItemStack(itemObj, targetQuantity));
            System.out.println("[BlockAI] CraftingController: Successfully crafted " + targetQuantity + "x " + targetItem);
        } else {
            System.out.println("[BlockAI] CraftingController: Unknown item " + targetItem);
        }
        
        this.targetItem = null; // Mark as done
        this.targetQuantity = 1;
        this.state = com.blockai.execution.TaskResult.SUCCESS;
    }
    
    public com.blockai.execution.TaskResult getState() {
        return state;
    }
}
