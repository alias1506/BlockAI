package com.blockai.gathering;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;

public class TargetSelector {

    public static List<BlockPos> analyzeStructure(ServerLevel level, BlockPos startNode, ResourceResolver requirement, int maxRadius) {
        List<BlockPos> structure = new ArrayList<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        
        queue.add(startNode);
        visited.add(startNode);
        
        // Direct-Seed Fallback
        BlockState seedState = level.getBlockState(startNode);
        boolean seedValid = requirement.acceptsBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(seedState.getBlock()).toString());
        
        if (seedValid) {
            structure.add(startNode);
        }
        
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);
            
            if (!requirement.acceptsBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) {
                continue;
            }
            
            if (!current.equals(startNode)) {
                structure.add(current);
            }
            
            // Limit structure size to prevent infinite scans
            if (structure.size() > 500) {
                break;
            }
            
            // Check all 26 neighbors (3x3x3 grid) for connected veins/trees
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        
                        BlockPos neighbor = current.offset(dx, dy, dz);
                        
                        if (!visited.contains(neighbor)) {
                            // Check max radius bounds
                            if (Math.abs(neighbor.getX() - startNode.getX()) <= maxRadius &&
                                Math.abs(neighbor.getY() - startNode.getY()) <= maxRadius &&
                                Math.abs(neighbor.getZ() - startNode.getZ()) <= maxRadius) {
                                
                                visited.add(neighbor);
                                BlockState neighborState = level.getBlockState(neighbor);
                                
                                if (requirement.acceptsBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(neighborState.getBlock()).toString())) {
                                    queue.add(neighbor);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Sort bottom-up, then by distance to start node
        structure.sort((p1, p2) -> {
            if (p1.getY() != p2.getY()) {
                return Integer.compare(p1.getY(), p2.getY());
            }
            return Double.compare(p1.distSqr(startNode), p2.distSqr(startNode));
        });
        
        return structure;
    }
}
