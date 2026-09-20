package com.blockai.ai;

import com.blockai.player.AIPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Validates that a block interaction is physically legitimate:
 * reach distance, line-of-sight, block existence, AI state.
 */
public class InteractionValidator {

    public static final double MAX_REACH = 4.5;

    public enum ValidationResult {
        VALID,
        TOO_FAR,
        BLOCKED_LOS,
        NOT_BREAKABLE,
        AI_DEAD,
        BLOCK_MISSING,
        WRONG_DIMENSION
    }

    /**
     * Validate ALL conditions before the AI breaks a block.
     */
    public static ValidationResult validate(AIPlayer player, BlockPos target) {
        ServerLevel level = (ServerLevel) player.level();

        // 1. AI must be alive
        if (!player.isAlive()) {
            return ValidationResult.AI_DEAD;
        }

        // 2. Block must exist and not be air
        BlockState state = level.getBlockState(target);
        if (state.isAir()) {
            return ValidationResult.BLOCK_MISSING;
        }

        // 3. Block must be breakable (hardness >= 0; bedrock has hardness -1)
        float hardness = state.getDestroySpeed(level, target);
        if (hardness < 0) {
            return ValidationResult.NOT_BREAKABLE;
        }

        // 4. Distance check — eye position to block center
        Vec3 eyePos = player.getEyePosition();
        Vec3 blockCenter = Vec3.atCenterOf(target);
        double distance = eyePos.distanceTo(blockCenter);
        if (distance > MAX_REACH) {
            return ValidationResult.TOO_FAR;
        }

        // 5. Line-of-sight raycast
        BlockHitResult hitResult = level.clip(new ClipContext(
                eyePos,
                blockCenter,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hitResult.getType() == HitResult.Type.MISS) {
            // Raycast missed everything — target is somehow invisible
            return ValidationResult.BLOCKED_LOS;
        }

        // The first block hit must be the target block
        BlockPos hitPos = hitResult.getBlockPos();
        if (!hitPos.equals(target)) {
            return ValidationResult.BLOCKED_LOS;
        }

        return ValidationResult.VALID;
    }

    /**
     * Find a valid interaction position adjacent to the target block.
     * Returns null if no valid position found.
     */
    public static BlockPos findInteractionPosition(ServerLevel level, AIPlayer player, BlockPos target) {
        // Check all 4 cardinal + 4 diagonal adjacent positions
        int[][] offsets = {
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {1, 0, 1}, {-1, 0, -1}, {1, 0, -1}, {-1, 0, 1}
        };

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int[] off : offsets) {
            BlockPos candidate = target.offset(off[0], 0, off[2]);

            // Must be walkable: feet and head are air, ground is solid
            if (!isStandable(level, candidate)) {
                // Try one block up (e.g., target is on a hill)
                BlockPos up = candidate.above();
                if (isStandable(level, up)) {
                    candidate = up;
                } else {
                    continue;
                }
            }

            double dist = candidate.distSqr(player.blockPosition());
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }
}
