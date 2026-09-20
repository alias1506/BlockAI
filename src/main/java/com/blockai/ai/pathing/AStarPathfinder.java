package com.blockai.ai.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

public class AStarPathfinder {

    private static final int MAX_NODES = 5000;
    
    public static List<BlockPos> findPath(ServerLevel level, BlockPos start, BlockPos target) {
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<BlockPos, Node> allNodes = new HashMap<>();
        
        Node startNode = new Node(start, null, 0, getDistance(start, target));
        openSet.add(startNode);
        allNodes.put(start, startNode);
        
        int nodesEvaluated = 0;
        
        while (!openSet.isEmpty() && nodesEvaluated < MAX_NODES) {
            Node current = openSet.poll();
            nodesEvaluated++;
            
            int dx = Math.abs(current.pos.getX() - target.getX());
            int dy = Math.abs(current.pos.getY() - target.getY());
            int dz = Math.abs(current.pos.getZ() - target.getZ());
            
            if (dx <= 1 && dz <= 1 && dy <= 2) { // Reachable interaction position
                return retracePath(current);
            }
            
            for (BlockPos neighborPos : getNeighbors(level, current.pos)) {
                double tentativeGCost = current.gCost + getDistance(current.pos, neighborPos);
                
                Node neighborNode = allNodes.get(neighborPos);
                if (neighborNode == null) {
                    neighborNode = new Node(neighborPos, current, tentativeGCost, getDistance(neighborPos, target));
                    allNodes.put(neighborPos, neighborNode);
                    openSet.add(neighborNode);
                } else if (tentativeGCost < neighborNode.gCost) {
                    neighborNode.gCost = tentativeGCost;
                    neighborNode.parent = current;
                    // re-add to update priority
                    openSet.remove(neighborNode);
                    openSet.add(neighborNode);
                }
            }
        }
        
        return null; // No path found or max nodes reached
    }
    
    private static List<BlockPos> retracePath(Node endNode) {
        List<BlockPos> path = new ArrayList<>();
        Node current = endNode;
        while (current != null) {
            path.add(current.pos);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }
    
    private static double getDistance(BlockPos a, BlockPos b) {
        return Math.sqrt(a.distSqr(b));
    }
    
    private static List<BlockPos> getNeighbors(ServerLevel level, BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}; // N S E W
        
        for (int[] dir : directions) {
            int dx = dir[0];
            int dz = dir[1];
            
            // Try flat
            BlockPos flat = pos.offset(dx, 0, dz);
            if (isWalkable(level, flat)) {
                neighbors.add(flat);
                continue;
            }
            
            // Try step up
            BlockPos stepUp = pos.offset(dx, 1, dz);
            if (isWalkable(level, stepUp) && isAir(level, pos.above(2))) {
                neighbors.add(stepUp);
                continue;
            }
            
            // Try drop down (up to 3 blocks to avoid fall damage)
            for (int drop = 1; drop <= 3; drop++) {
                BlockPos dropPos = pos.offset(dx, -drop, dz);
                if (isWalkable(level, dropPos)) {
                    // Check if path down is clear
                    boolean clear = true;
                    for (int y = 0; y > -drop; y--) {
                        if (!isAir(level, pos.offset(dx, y, dz))) {
                            clear = false;
                            break;
                        }
                    }
                    if (clear) {
                        neighbors.add(dropPos);
                    }
                    break;
                }
            }
        }
        return neighbors;
    }
    
    private static boolean isWalkable(ServerLevel level, BlockPos pos) {
        // Feet and head must be air/passable, ground must be solid
        return isAir(level, pos) && isAir(level, pos.above()) && isSolid(level, pos.below());
    }
    
    private static boolean isAir(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
    
    private static boolean isSolid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
