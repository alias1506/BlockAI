package com.blockai.ai;

import com.blockai.player.AIPlayer;

public class CraftingController {
    
    private String targetItem = null;
    private int targetQuantity = 1;
    private com.blockai.ai.ControllerState state = com.blockai.ai.ControllerState.IDLE;
    
    public void craft(String item, int quantity) {
        this.targetItem = item;
        this.targetQuantity = quantity > 0 ? quantity : 1;
        this.state = com.blockai.ai.ControllerState.RUNNING;
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
            // For now, we simulate crafting by just giving the item.
            // A full implementation would remove ingredients and check for a crafting table.
            player.getInventory().add(new net.minecraft.world.item.ItemStack(itemObj, targetQuantity));
            System.out.println("[BlockAI] CraftingController: Successfully crafted " + targetQuantity + "x " + targetItem);
        } else {
            System.out.println("[BlockAI] CraftingController: Unknown item " + targetItem);
        }
        
        this.targetItem = null; // Mark as done
        this.targetQuantity = 1;
        this.state = com.blockai.ai.ControllerState.SUCCESS;
    }
    
    public com.blockai.ai.ControllerState getState() {
        return state;
    }
}
