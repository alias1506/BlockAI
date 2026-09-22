package com.blockai.movement;

import com.blockai.ai.AIPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InteractionPositionPlanner {

    /**
     * Finds a valid access position (the block the AI should stand IN or ON) to interact
     * with the specified target block.
     * 
     * @param player The AI player
     * @param target The target block to interact with (e.g. to mine)
     * @param maxDistance Maximum acceptable distance from the player
     * @return A valid BlockPos for movement, or null if none is found.
     */
    public static BlockPos getBestAccessPosition(AIPlayer player, BlockPos target, double maxDistance) {
        ServerLevel level = (ServerLevel) player.level();
        List<BlockPos> candidates = new ArrayList<>();

        // Max reach distance is generally 3 blocks for AI (Minecraft is 4.5).
        int r = 3; 
        BlockPos playerPos = player.blockPosition();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // Don't stand IN the target block
                    
                    BlockPos candidate = target.offset(x, y, z);
                    
                    // Limit search
                    if (candidate.distManhattan(target) > r) continue;
                    
                    BlockState legs = level.getBlockState(candidate);
                    BlockState head = level.getBlockState(candidate.above());
                    BlockState ground = level.getBlockState(candidate.below());
                    
                    if (!legs.getCollisionShape(level, candidate).isEmpty()) continue; // Legs blocked
                    if (!head.getCollisionShape(level, candidate.above()).isEmpty()) continue; // Head blocked
                    
                    // Must not be dangerous (e.g. lava)
                    if (legs.getFluidState().isSource() || legs.getFluidState().getType() == net.minecraft.world.level.material.Fluids.LAVA) continue;
                    
                    // Ground must be solid
                    if (ground.getCollisionShape(level, candidate.below()).isEmpty() && !ground.getFluidState().isSource()) {
                        continue; 
                    }
                    
                    candidates.add(candidate);
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(Comparator.comparingDouble((BlockPos p) -> p.distSqr(playerPos))
                .thenComparingDouble(p -> Math.abs(p.getY() - target.getY())));

        return candidates.get(0);
    }
}
