package com.blockai.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public class InventoryManager {
    
    public static void openAIInventoryFor(ServerPlayer human, ServerPlayer ai) {
        if (human == null || ai == null) return;
        
        Inventory aiInv = ai.getInventory();
        
        // Wrap the AI inventory in a 45-slot virtual container
        Container liveContainer = new Container() {
            @Override
            public int getContainerSize() {
                return 45;
            }

            @Override
            public boolean isEmpty() {
                return aiInv.isEmpty();
            }

            @Override
            public ItemStack getItem(int slot) {
                if (slot < aiInv.getContainerSize()) {
                    return aiInv.getItem(slot);
                }
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack removeItem(int slot, int amount) {
                if (slot < aiInv.getContainerSize()) {
                    return aiInv.removeItem(slot, amount);
                }
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack removeItemNoUpdate(int slot) {
                if (slot < aiInv.getContainerSize()) {
                    return aiInv.removeItemNoUpdate(slot);
                }
                return ItemStack.EMPTY;
            }

            @Override
            public void setItem(int slot, ItemStack stack) {
                if (slot < aiInv.getContainerSize()) {
                    aiInv.setItem(slot, stack);
                }
            }

            @Override
            public void setChanged() {
                aiInv.setChanged();
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }

            @Override
            public void clearContent() {
                aiInv.clearContent();
            }
        };
        
        human.openMenu(new SimpleMenuProvider(
            (id, inventory, player) -> new ChestMenu(MenuType.GENERIC_9x5, id, inventory, liveContainer, 5),
            Component.literal("BlockAI Inventory")
        ));
    }
}
