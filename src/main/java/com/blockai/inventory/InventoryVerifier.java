package com.blockai.inventory;

import com.blockai.ai.AIPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

import com.blockai.gathering.ResourceResolver;

public class InventoryVerifier {
    
    // Kept for backward compatibility with older classes like ItemRecoveryController
    private final Map<String, Integer> snapshot = new HashMap<>();

    public void takeSnapshot(AIPlayer player) {
        snapshot.clear();
        snapshot.putAll(captureSnapshot(player));
    }

    public Map<String, Integer> calculateDelta(AIPlayer player) {
        return calculateDelta(snapshot, captureSnapshot(player));
    }

    public boolean hasDeltaForCategory(AIPlayer player, ResourceResolver requirement) {
        Map<String, Integer> delta = calculateDelta(player);
        for (Map.Entry<String, Integer> entry : delta.entrySet()) {
            if (requirement.acceptsBlock(entry.getKey())) {
                System.out.println("[BlockAI] InventoryVerifier: Collected " + entry.getValue() + "x " + entry.getKey());
                return true;
            }
        }
        return false;
    }

    public boolean hasExpectedInventoryDelta(AIPlayer player, net.minecraft.world.item.Item expectedItem, int minimumIncrease) {
        Map<String, Integer> delta = calculateDelta(player);
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(expectedItem).toString();
        int increase = delta.getOrDefault(itemId, 0);
        return increase >= minimumIncrease;
    }

    public int getSnapshotDeltaCount(AIPlayer player, ResourceResolver requirement) {
        Map<String, Integer> delta = calculateDelta(player);
        int total = 0;
        for (Map.Entry<String, Integer> entry : delta.entrySet()) {
            if (requirement.acceptsBlock(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total;
    }

    public int getSnapshotDeltaCount(AIPlayer player) {
        Map<String, Integer> delta = calculateDelta(player);
        int total = 0;
        for (int count : delta.values()) {
            total += count;
        }
        return total;
    }

    // --- New Static Pure Functions for Pipeline ---

    public static Map<String, Integer> captureSnapshot(AIPlayer player) {
        Map<String, Integer> snap = new HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                snap.put(itemId, snap.getOrDefault(itemId, 0) + stack.getCount());
            }
        }
        return snap;
    }

    public static Map<String, Integer> calculateDelta(Map<String, Integer> oldSnap, Map<String, Integer> newSnap) {
        Map<String, Integer> delta = new HashMap<>();
        for (String item : newSnap.keySet()) {
            int old = oldSnap.getOrDefault(item, 0);
            int n = newSnap.get(item);
            if (n > old) {
                delta.put(item, n - old);
            }
        }
        return delta;
    }
}
