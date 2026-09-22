package com.blockai.world;

import com.blockai.ai.AIPlayer;
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
public class BlockScanner {

    public static final double MAX_REACH = 4.5;

    public enum ValidationStatus {
        VALID,
        TOO_FAR,
        BLOCKED_LOS,
        NOT_BREAKABLE,
        AI_DEAD,
        BLOCK_MISSING,
        WRONG_DIMENSION
    }
    
    public static class ValidationResult {
        public ValidationStatus status;
        public BlockPos obstacle;
        
        public ValidationResult(ValidationStatus status) {
            this.status = status;
        }
        
        public ValidationResult(ValidationStatus status, BlockPos obstacle) {
            this.status = status;
            this.obstacle = obstacle;
        }
        
        public String toString() {
            return status.toString();
        }
    }

    /**
     * Validate ALL conditions before the AI breaks a block.
     */
    public static ValidationResult validate(AIPlayer player, BlockPos target) {
        ServerLevel level = (ServerLevel) player.level();

        // 1. AI must be alive
        if (!player.isAlive()) {
            return new ValidationResult(ValidationStatus.AI_DEAD);
        }

        // 2. Block must exist and not be air
        BlockState state = level.getBlockState(target);
        if (state.isAir()) {
            return new ValidationResult(ValidationStatus.BLOCK_MISSING);
        }

        // 3. Block must be breakable (hardness >= 0; bedrock has hardness -1)
        float hardness = state.getDestroySpeed(level, target);
        if (hardness < 0) {
            return new ValidationResult(ValidationStatus.NOT_BREAKABLE);
        }

        // 4. Distance check — eye position to block center
        Vec3 eyePos = player.getEyePosition();
        Vec3 blockCenter = Vec3.atCenterOf(target);
        double distance = eyePos.distanceTo(blockCenter);
        if (distance > MAX_REACH) {
            return new ValidationResult(ValidationStatus.TOO_FAR);
        }

        // 5. Line-of-sight raycast
        BlockHitResult hitResult = level.clip(new ClipContext(
                eyePos,
                blockCenter,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hitResult.getType() == HitResult.Type.MISS) {
            // Raycast missed everything — target is somehow invisible
            return new ValidationResult(ValidationStatus.BLOCKED_LOS);
        }

        // The first block hit must be the target block
        BlockPos hitPos = hitResult.getBlockPos();
        if (!hitPos.equals(target)) {
            return new ValidationResult(ValidationStatus.BLOCKED_LOS, hitPos);
        }

        return new ValidationResult(ValidationStatus.VALID);
    }

    /**
     * Find a valid interaction position adjacent to the target block.
     * Checks 3D offsets, verifies standability, line of sight, and ensures pathability.
     */
    public static BlockPos findInteractionPosition(ServerLevel level, AIPlayer player, BlockPos target) {
        System.out.println("[BlockAI][GATHER] ACCESS_SEARCH target=" + target);
        // Evaluate horizontal and vertical adjacent offsets
        int[][] offsets = {
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
                {1, 0, 1}, {-1, 0, -1}, {1, 0, -1}, {-1, 0, 1},
                {2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2}
        };

        BlockPos best = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (int[] off : offsets) {
            BlockPos candidate = target.offset(off[0], off[1], off[2]);

            if (candidate.equals(target)) {
                continue;
            }

            // 1. Must be standable
            if (!isStandable(level, candidate)) {
                System.out.println("[BlockAI][GATHER] ACCESS_CANDIDATE candidate=" + candidate + " walkable=false reason=NotStandable");
                continue;
            }

            // 2. Must be within physical reach
            Vec3 feetPos = Vec3.atBottomCenterOf(candidate);
            Vec3 eyePos = feetPos.add(0, 1.62, 0); // Approx player eye height
            Vec3 blockCenter = Vec3.atCenterOf(target);
            if (eyePos.distanceTo(blockCenter) > MAX_REACH) {
                System.out.println("[BlockAI][GATHER] ACCESS_CANDIDATE candidate=" + candidate + " walkable=true interactionRange=false reason=TooFar");
                continue;
            }

            // 3. Must have line of sight from the candidate position
            BlockHitResult hitResult = level.clip(new ClipContext(
                    eyePos,
                    blockCenter,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            ));
            
            if (hitResult.getType() == HitResult.Type.MISS || !hitResult.getBlockPos().equals(target)) {
                System.out.println("[BlockAI][GATHER] ACCESS_CANDIDATE candidate=" + candidate + " walkable=true interactionRange=false reason=BlockedLOS");
                continue; // Blocked line of sight from here
            }
            
            System.out.println("[BlockAI][GATHER] ACCESS_CANDIDATE candidate=" + candidate + " walkable=true interactionRange=true valid=true");

            // 4. Shortest distance to the candidate position
            double distSq = player.blockPosition().distSqr(candidate);
            if (distSq < bestDistanceSq) {
                bestDistanceSq = distSq;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        // Feet and head must be clear
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        
        // Block below must be solid
        BlockPos below = pos.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }
}
