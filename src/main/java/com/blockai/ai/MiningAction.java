package com.blockai.ai;

import com.blockai.player.AIPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;

import java.util.List;

public class MiningAction {

    public enum MiningState {
        IDLE,
        MINING,
        BLOCK_BROKEN,
        FIND_DROPS,
        COLLECT_DROPS,
        VERIFY_INVENTORY,
        SUCCESS,
        FAILED
    }

    private BlockPos target = null;
    private MiningState state = MiningState.IDLE;
    
    private AIPlayer currentPlayer = null;
    
    private float miningProgress = 0.0f;
    private int lastCrackStage = -1;
    
    private List<ItemEntity> targetDrops = null;
    private int preMiningInventoryCount = 0;
    
    private int waitTicks = 0; // for drop spawning/physics to settle

    public boolean isIdle() {
        return state == MiningState.IDLE;
    }

    public MiningState getState() {
        return state;
    }

    public BlockPos getTarget() {
        return target;
    }

    private void equipBestTool(AIPlayer player, BlockState blockState) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        ItemStack mainHand = player.getMainHandItem();
        float bestSpeed = blockState.getDestroySpeed(player.level(), target);
        
        if (!mainHand.isEmpty()) {
            bestSpeed = mainHand.getDestroySpeed(blockState);
        } else {
            bestSpeed = 1.0f;
        }

        int bestSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                float speed = stack.getDestroySpeed(blockState);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }
        
        if (bestSlot != -1) {
            ItemStack temp = inv.getItem(bestSlot);
            inv.setItem(bestSlot, mainHand);
            player.setItemInHand(InteractionHand.MAIN_HAND, temp);
            System.out.println("[BlockAI] MiningAction: Equipped best tool for " + 
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockState.getBlock()));
        }
    }

    public void startMining(AIPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        BlockState blockState = level.getBlockState(pos);

        if (blockState.isAir()) {
            state = MiningState.FAILED;
            return;
        }
        
        this.currentPlayer = player;

        equipBestTool(player, blockState);

        float progressPerTick = blockState.getDestroyProgress(player, level, pos);
        if (progressPerTick <= 0) {
            System.out.println("[BlockAI] MiningAction: Block at " + pos + " is unbreakable.");
            state = MiningState.FAILED;
            return;
        }

        this.target = pos;
        this.miningProgress = 0.0f;
        this.lastCrackStage = -1;
        this.state = MiningState.MINING;
        
        // Count total items before mining to verify gathering
        this.preMiningInventoryCount = countTotalItems(player);

        faceTarget(player, pos);

        int expectedTicks = (int) Math.ceil(1.0f / progressPerTick);
        System.out.println("[BlockAI] MiningAction: Started mining " +
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockState.getBlock()) +
                " at " + pos + " (expected " + expectedTicks + " ticks)");
    }
    
    private void clearCrack(ServerLevel level, int entityId) {
        if (target != null) {
            level.destroyBlockProgress(entityId, target, -1);
            lastCrackStage = -1;
        }
    }
    
    private int countTotalItems(AIPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void faceTarget(AIPlayer player, BlockPos pos) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 blockCenter = Vec3.atCenterOf(pos);
        Vec3 diff = blockCenter.subtract(eyePos);
        double xzLen = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(diff.y, xzLen));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yHeadRot = yaw;
        player.yBodyRot = yaw;
    }

    public MiningState tick(AIPlayer player) {
        if (state == MiningState.IDLE || target == null) {
            return state;
        }

        ServerLevel level = (ServerLevel) player.level();

        if (state == MiningState.MINING) {
            BlockState currentState = level.getBlockState(target);

            if (currentState.isAir()) {
                clearCrack(level, player.getId());
                System.out.println("[BlockAI] MiningAction: Block break CONFIRMED at " + target);
                state = MiningState.BLOCK_BROKEN;
                waitTicks = 5; // Wait a few ticks for drops to spawn and settle
                return state;
            }

            faceTarget(player, target);
            player.swing(InteractionHand.MAIN_HAND);

            // Accumulate progress precisely using the correct vanilla logic
            float progressPerTick = currentState.getDestroyProgress(player, level, target);
            miningProgress += progressPerTick;

            int crackStage = (int) (miningProgress * 10.0f);
            if (crackStage != lastCrackStage) {
                level.destroyBlockProgress(player.getId(), target, crackStage);
                lastCrackStage = crackStage;
                System.out.println("[BlockAI] MiningAction: Mining progress: " + (crackStage * 10) + "%");
            }

            if (miningProgress >= 1.0f) {
                // Re-validate before breaking
                InteractionValidator.ValidationResult result = InteractionValidator.validate(player, target);
                if (result != InteractionValidator.ValidationResult.VALID) {
                    System.out.println("[BlockAI] MiningAction: Validation failed at break time: " + result);
                    clearCrack(level, player.getId());
                    state = MiningState.FAILED;
                    return state;
                }

                // Actually destroy block using survival logic
                boolean broken = player.gameMode.destroyBlock(target);
                clearCrack(level, player.getId());

                if (broken) {
                    BlockState afterState = level.getBlockState(target);
                    if (afterState.isAir()) {
                        System.out.println("[BlockAI] MiningAction: Block break CONFIRMED at " + target);
                        state = MiningState.BLOCK_BROKEN;
                        waitTicks = 5;
                    } else {
                        System.out.println("[BlockAI] MiningAction: Block break reported success but block remains!");
                        state = MiningState.FAILED;
                    }
                } else {
                    System.out.println("[BlockAI] MiningAction: destroyBlock returned false at " + target);
                    state = MiningState.FAILED;
                }
            }
        } 
        else if (state == MiningState.BLOCK_BROKEN) {
            if (waitTicks > 0) {
                waitTicks--;
                return state;
            }
            state = MiningState.FIND_DROPS;
        } 
        else if (state == MiningState.FIND_DROPS) {
            // Scan for ItemEntity near the broken block
            AABB searchBox = new AABB(target).inflate(2.0);
            targetDrops = level.getEntitiesOfClass(ItemEntity.class, searchBox, e -> e.isAlive() && !e.hasPickUpDelay());
            
            if (targetDrops.isEmpty()) {
                // No drops found. Maybe block didn't drop anything (e.g., stone with fist, or glass)
                System.out.println("[BlockAI] MiningAction: No drops detected.");
                state = MiningState.SUCCESS;
            } else {
                System.out.println("[BlockAI] MiningAction: Drop(s) detected: " + targetDrops.size() + " items.");
                state = MiningState.COLLECT_DROPS;
                waitTicks = 0; // Using waitTicks for timeout
            }
        } 
        else if (state == MiningState.COLLECT_DROPS) {
            // Filter alive drops
            targetDrops.removeIf(e -> !e.isAlive());
            
            if (targetDrops.isEmpty()) {
                System.out.println("[BlockAI] MiningAction: All targeted drops were collected or destroyed.");
                state = MiningState.VERIFY_INVENTORY;
                return state;
            }
            
            ItemEntity firstDrop = targetDrops.get(0);
            double distSqr = player.distanceToSqr(firstDrop);
            
            if (distSqr > 1.5) {
                // Move towards drop
                var path = com.blockai.ai.pathing.AStarPathfinder.findPath(level, player.blockPosition(), firstDrop.blockPosition());
                if (path != null) {
                    player.movementController.setPath(path);
                } else {
                    // Try to move directly to the position
                    player.movementController.setPath(List.of(firstDrop.blockPosition()));
                }
            } else {
                // Stop moving if close enough, physics engine will handle collision
                player.movementController.stop();
            }
            
            waitTicks++;
            if (waitTicks > 60) {
                System.out.println("[BlockAI] MiningAction: Drop collection timed out.");
                state = MiningState.VERIFY_INVENTORY;
            }
        }
        else if (state == MiningState.VERIFY_INVENTORY) {
            int postMiningInventoryCount = countTotalItems(player);
            if (postMiningInventoryCount > preMiningInventoryCount) {
                int gathered = postMiningInventoryCount - preMiningInventoryCount;
                System.out.println("[BlockAI] MiningAction: Inventory verified. +" + gathered + " item(s).");
            } else {
                System.out.println("[BlockAI] MiningAction: Inventory verification failed. Drop lost or not collected.");
            }
            state = MiningState.SUCCESS;
        }

        return state;
    }

    public void cancel() {
        if (state == MiningState.MINING && target != null && currentPlayer != null) {
            clearCrack((ServerLevel) currentPlayer.level(), currentPlayer.getId());
        }
        state = MiningState.IDLE;
        target = null;
        miningProgress = 0.0f;
        targetDrops = null;
        currentPlayer = null;
    }

    public void reset() {
        if (state == MiningState.MINING && target != null && currentPlayer != null) {
            clearCrack((ServerLevel) currentPlayer.level(), currentPlayer.getId());
        }
        state = MiningState.IDLE;
        target = null;
        miningProgress = 0.0f;
        targetDrops = null;
        currentPlayer = null;
    }
}
