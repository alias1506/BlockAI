package com.blockai.player;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

public class AIInventoryHelper {
    
    public static void openAIInventoryFor(ServerPlayer human, ServerPlayer ai) {
        if (human == null || ai == null) return;
        
        Inventory aiInv = ai.getInventory();
        
        // Player inventory has 41 slots. Chest 9x5 has 45 slots.
        SimpleContainer container = new SimpleContainer(45) {
            @Override
            public void setChanged() {
                super.setChanged();
                // Sync back to AI inventory
                for (int i = 0; i < 41; i++) {
                    aiInv.setItem(i, this.getItem(i));
                }
            }
        };
        
        // Copy items from AI to container
        for (int i = 0; i < 41; i++) {
            container.setItem(i, aiInv.getItem(i));
        }
        
        human.openMenu(new SimpleMenuProvider(
            (id, inventory, player) -> new ChestMenu(MenuType.GENERIC_9x5, id, inventory, container, 5),
            Component.literal("BlockAI Inventory")
        ));
    }
}
