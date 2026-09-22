package com.blockai.gathering;

import com.blockai.ai.AIPlayer;
import com.blockai.world.WorldScanner;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResourceResolver {

    private final ResourceCategory category;
    private final String specificItem;
    private final int quantity;

    public ResourceResolver(ResourceCategory category, String specificItem, int quantity) {
        this.category = category;
        this.specificItem = specificItem;
        this.quantity = quantity;
    }

    public ResourceCategory getCategory() {
        return category;
    }

    public String getSpecificItem() {
        return specificItem;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean acceptsBlock(String blockId) {
        if (specificItem != null && !specificItem.isEmpty()) {
            return specificItem.equals(blockId);
        }
        if (category != null) {
            return category.matches(blockId);
        }
        return false;
    }

    public List<String> getAcceptedBlocks() {
        // Fallback for UI or debug displays
        List<String> list = new ArrayList<>();
        if (specificItem != null) {
            list.add(specificItem);
        }
        if (category != null) {
            // Since we use dynamic tags now, just list what we found in memory that matches
            for (String known : WorldScanner.MEMORY.getAll().keySet()) {
                if (category.matches(known)) {
                    list.add(known);
                }
            }
        }
        return list;
    }

    public int countInInventory(AIPlayer player) {
        Inventory inv = player.getInventory();
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (acceptsBlock(itemId)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return "ResourceResolver{" +
                "category=" + category +
                ", specificItem='" + specificItem + '\'' +
                ", quantity=" + quantity +
                '}';
    }
}
