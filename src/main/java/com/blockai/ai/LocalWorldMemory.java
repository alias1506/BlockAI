package com.blockai.ai;

import net.minecraft.core.BlockPos;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LocalWorldMemory {
    
    // Maps a generic resource tag or block name to a set of known coordinates
    private final Map<String, Set<BlockPos>> knownBlocks = new ConcurrentHashMap<>();
    
    public void addBlock(String type, BlockPos pos) {
        knownBlocks.computeIfAbsent(type, k -> new HashSet<>()).add(pos);
    }
    
    public void removeBlock(String type, BlockPos pos) {
        Set<BlockPos> blocks = knownBlocks.get(type);
        if (blocks != null) {
            blocks.remove(pos);
        }
    }
    
    public void removeBlockByPos(BlockPos pos) {
        for (Set<BlockPos> blocks : knownBlocks.values()) {
            blocks.remove(pos);
        }
    }
    
    public Set<BlockPos> getKnownBlocks(String type) {
        return knownBlocks.getOrDefault(type, new HashSet<>());
    }
    
    public void clear() {
        knownBlocks.clear();
    }
    
    public Map<String, Set<BlockPos>> getAll() {
        return knownBlocks;
    }
}
