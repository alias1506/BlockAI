package com.blockai.gathering;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.blockai.ai.AIPlayer;

import com.blockai.world.BlockScanner;

public class AccessPlanner {

    public enum PlanType {
        DIRECT,
        EXISTING_TERRAIN,
        JUMP,
        STAIRCASE,
        PILLAR,
        PLATFORM,
        FAILED
    }

    public static class AccessPlan {
        public BlockPos targetPosition; // Either to walk to, or to place a block at
        public boolean requiresPlacement;
        public PlanType type;
        
        public AccessPlan(BlockPos targetPosition, boolean requiresPlacement, PlanType type) {
            this.targetPosition = targetPosition;
            this.requiresPlacement = requiresPlacement;
            this.type = type;
        }
    }

    public static AccessPlan planAccess(ServerLevel level, AIPlayer player, BlockPos target) {
        // 1. DIRECT: Try to find a natural interaction position
        BlockPos naturalPos = BlockScanner.findInteractionPosition(level, player, target);
        if (naturalPos != null) {
            return new AccessPlan(naturalPos, false, PlanType.DIRECT);
        }

        // 2. PILLAR/STAIRCASE: Plan scaffolding using a simple heuristic.
        // Check if player has inventory blocks to scaffold
        int scaffoldSlot = TemporaryBlockManager.getScaffoldBlockSlot(player);
        if (scaffoldSlot == -1) {
            // It's normal to not have scaffold blocks. We just can't reach this target.
            // DO NOT spam error. Just quietly return null for this specific target.
            return null; // Cannot build
        }

        // 2. PILLAR/STAIRCASE: Plan scaffolding using a simple heuristic for now.
        // Look for an air block connected to the ground, closer to the target than the AI.
        BlockPos playerPos = player.blockPosition();
        
        // Search columns: directly under the target, and adjacent columns
        int[][] columns = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}
        };
        
        BlockPos bestScaffold = null;
        double bestDistToTarget = Double.MAX_VALUE;
        PlanType planType = PlanType.PILLAR;
        
        for (int[] col : columns) {
            int cx = target.getX() + col[0];
            int cz = target.getZ() + col[1];
            
            // Search upwards from slightly below player's feet up to the target's Y level
            for (int y = playerPos.getY() - 1; y <= target.getY(); y++) {
                BlockPos checkPos = new BlockPos(cx, y, cz);
                
                // If we can place a block here
                if (level.getBlockState(checkPos).canBeReplaced() || level.getBlockState(checkPos).isAir()) {
                    // Check if block below is solid (so the placed block is supported)
                    BlockPos below = checkPos.below();
                    if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                        
                        // Valid scaffold placement spot. Check if it's within reach.
                        if (playerPos.distSqr(checkPos) <= 25.0) { // roughly 5 blocks reach
                            double dist = checkPos.distSqr(target);
                            if (dist < bestDistToTarget) {
                                bestDistToTarget = dist;
                                bestScaffold = checkPos;
                                if (col[0] != 0 || col[1] != 0) {
                                    planType = PlanType.STAIRCASE;
                                } else {
                                    planType = PlanType.PILLAR;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (bestScaffold != null) {
            System.out.println("[AccessPlanner] Planning temporary scaffold (" + planType + ") at " + bestScaffold);
            return new AccessPlan(bestScaffold, true, planType);
        }
        
        System.out.println("[AccessPlanner] Target " + target + " is out of reach and no scaffold position found.");
        return null;
    }
}
