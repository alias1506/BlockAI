package com.blockai.gathering;

import com.blockai.ai.AIPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;

import java.util.List;

import com.blockai.world.BlockScanner;

public class MiningController {

    public enum MiningState {
        IDLE,
        MINING,
        SUCCESS,
        FAILED
    }

    private BlockPos target = null;
    private MiningState state = MiningState.IDLE;
    
    private AIPlayer currentPlayer = null;
    
    private float miningProgress = 0.0f;
    private int lastCrackStage = -1;

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
        
        float bestSpeed = 1.0f;
        if (!mainHand.isEmpty()) {
            bestSpeed = mainHand.getDestroySpeed(blockState);
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
            System.out.println("[BlockAI] MiningController: Equipped best tool for " + 
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
            System.out.println("[BlockAI] MiningController: Block at " + pos + " is unbreakable.");
            state = MiningState.FAILED;
            return;
        }

        this.target = pos;
        this.miningProgress = 0.0f;
        this.lastCrackStage = -1;
        this.state = MiningState.MINING;

        faceTarget(player, pos);

        int expectedTicks = (int) Math.ceil(1.0f / progressPerTick);
        System.out.println("[BlockAI] MiningController: Started mining " +
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
                System.out.println("[BlockAI] MiningController: Block break CONFIRMED at " + target);
                state = MiningState.SUCCESS;
                return state;
            }

            // Real-time validation during mining
            BlockScanner.ValidationResult validation = BlockScanner.validate(player, target);
            if (validation.status != BlockScanner.ValidationStatus.VALID) {
                System.out.println("[BlockAI] MiningController: Target became invalid during mining (" + validation.status + ") at " + target);
                clearCrack(level, player.getId());
                state = MiningState.FAILED;
                return state;
            }

            faceTarget(player, target);
            player.swing(InteractionHand.MAIN_HAND);

            // Accumulate progress precisely using the correct vanilla logic
            float progressPerTick = currentState.getDestroyProgress(player, level, target);
            miningProgress += progressPerTick;
            
            // Clamp progress to 1.0f max
            if (miningProgress > 1.0f) {
                miningProgress = 1.0f;
            }

            int crackStage = (int) (miningProgress * 10.0f);
            if (crackStage != lastCrackStage && crackStage <= 10) {
                level.destroyBlockProgress(player.getId(), target, crackStage);
                lastCrackStage = crackStage;
                System.out.println("[BlockAI] MiningController: Mining progress: " + (crackStage * 10) + "%");
            }

            if (miningProgress >= 1.0f) {
                // Actually destroy block using survival logic
                boolean broken = player.gameMode.destroyBlock(target);
                clearCrack(level, player.getId());

                if (broken) {
                    BlockState afterState = level.getBlockState(target);
                    if (afterState.isAir()) {
                        System.out.println("[BlockAI] MiningController: Block break CONFIRMED at " + target);
                        state = MiningState.SUCCESS;
                    } else {
                        System.out.println("[BlockAI] MiningController: Block break reported success but block remains!");
                        state = MiningState.FAILED;
                    }
                } else {
                    System.out.println("[BlockAI] MiningController: destroyBlock returned false at " + target);
                    state = MiningState.FAILED;
                }
            }
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
        currentPlayer = null;
    }

    public void reset() {
        if (state == MiningState.MINING && target != null && currentPlayer != null) {
            clearCrack((ServerLevel) currentPlayer.level(), currentPlayer.getId());
        }
        state = MiningState.IDLE;
        target = null;
        miningProgress = 0.0f;
        currentPlayer = null;
    }
}
