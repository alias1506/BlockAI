package com.blockai.gathering;

import com.blockai.ai.AIPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TemporaryBlockManager {
    
    // Global static record of temporary blocks. Survives task switches and deaths.
    private static final List<TemporaryBlockRecord> RECORDS = new ArrayList<>();
    
    public static void addRecord(TemporaryBlockRecord record) {
        RECORDS.add(record);
    }
    
    public static List<TemporaryBlockRecord> getRecordsForTask(String taskId) {
        List<TemporaryBlockRecord> taskRecords = new ArrayList<>();
        for (TemporaryBlockRecord record : RECORDS) {
            if (taskId.equals(record.taskId)) {
                taskRecords.add(record);
            }
        }
        return taskRecords;
    }
    
    public static boolean hasRecordsForTask(String taskId) {
        for (TemporaryBlockRecord record : RECORDS) {
            if (taskId.equals(record.taskId)) {
                return true;
            }
        }
        return false;
    }
    
    public static void removeRecord(TemporaryBlockRecord record) {
        RECORDS.remove(record);
    }

    public static TemporaryBlockRecord getRecordAt(BlockPos pos) {
        for (TemporaryBlockRecord record : RECORDS) {
            if (record.position.equals(pos)) {
                return record;
            }
        }
        return null;
    }

    /**
     * Finds dirt, cobblestone, or planks in the inventory to use for scaffolding.
     */
    public static ItemStack findScaffoldBlock(AIPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (name.contains("dirt") || name.contains("cobblestone") || name.contains("planks")) {
                    return stack;
                }
            }
        }
        // Fallback: any non-valuable block
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (!name.contains("ore") && !name.contains("diamond") && !name.contains("gold") && !name.contains("iron") && !name.contains("emerald") && !name.contains("log") && !name.contains("crafting_table") && !name.contains("furnace")) {
                     return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static int getScaffoldBlockSlot(AIPlayer player) {
        for (int i = 0; i < 36; i++) { // Check main inventory slots
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (name.contains("dirt") || name.contains("cobblestone") || name.contains("planks")) {
                    return i;
                }
            }
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (!name.contains("ore") && !name.contains("diamond") && !name.contains("gold") && !name.contains("iron") && !name.contains("emerald") && !name.contains("log") && !name.contains("crafting_table") && !name.contains("furnace")) {
                     return i;
                }
            }
        }
        return -1;
    }
}
