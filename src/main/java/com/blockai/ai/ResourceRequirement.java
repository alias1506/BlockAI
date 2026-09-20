package com.blockai.ai;

import com.blockai.player.AIPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Encapsulates a resource-gathering requirement.
 * Supports both generic categories ("any wood") and specific items ("oak_log only").
 */
public class ResourceRequirement {

    private final ResourceCategory category;
    private final String specificItem; // null = accept any in category
    private final int quantity;

    public ResourceRequirement(ResourceCategory category, String specificItem, int quantity) {
        this.category = category;
        this.specificItem = specificItem;
        this.quantity = quantity > 0 ? quantity : 1;
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

    /**
     * Get the list of block IDs that satisfy this requirement.
     * If specificItem is set, only that item counts.
     * Otherwise, all blocks in the category count.
     */
    public List<String> getAcceptedBlocks() {
        if (specificItem != null && !specificItem.isEmpty()) {
            return List.of(specificItem);
        }
        return category.getAcceptedBlocks();
    }

    /**
     * Does the given block ID satisfy this requirement?
     */
    public boolean acceptsBlock(String blockId) {
        if (specificItem != null && !specificItem.isEmpty()) {
            return specificItem.equals(blockId);
        }
        return category.matches(blockId);
    }

    /**
     * Count how many items matching this requirement are in the AI's actual inventory.
     */
    public int countInInventory(AIPlayer player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            // Items dropped from blocks often have the same ID as the block
            // e.g. minecraft:oak_log block drops minecraft:oak_log item
            if (acceptsBlock(itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * How many more items are needed?
     */
    public int getRemainingQuantity(AIPlayer player) {
        return Math.max(0, quantity - countInInventory(player));
    }

    /**
     * Is the requirement fully satisfied by the current inventory?
     */
    public boolean isSatisfied(AIPlayer player) {
        return getRemainingQuantity(player) <= 0;
    }

    @Override
    public String toString() {
        if (specificItem != null) {
            return specificItem + " x" + quantity;
        }
        return category.name() + " x" + quantity;
    }
}
