package com.blockai.recovery;

import com.blockai.ai.AIPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ItemRecoveryController {
    
    public enum CollectionState {
        IDLE,
        FIND_DROPS,
        MOVING_TO_DROP,
        WAITING_FOR_PICKUP,
        SUCCESS,
        FAILED
    }
    
    private CollectionState state = CollectionState.IDLE;
    private BlockPos sourcePos = null;
    private ItemEntity targetDrop = null;
    private int waitTicks = 0;
    private int timeoutTicks = 0;

    public void start(BlockPos sourcePos) {
        this.sourcePos = sourcePos;
        this.state = CollectionState.FIND_DROPS;
        this.targetDrop = null;
        this.waitTicks = 5; // allow time for drops to spawn and fall
        this.timeoutTicks = 0;
    }

    public CollectionState tick(AIPlayer player) {
        if (state == CollectionState.IDLE) {
            return state;
        }

        ServerLevel level = (ServerLevel) player.level();

        if (state == CollectionState.FIND_DROPS) {
            if (waitTicks > 0) {
                waitTicks--;
                return state;
            }

            AABB searchBox = new AABB(sourcePos).inflate(3.0);
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, searchBox, e -> e.isAlive());
            
            if (drops.isEmpty()) {
                System.out.println("[BlockAI] ItemRecoveryController: No drops detected around " + sourcePos);
                state = CollectionState.FAILED; // We failed to find anything
            } else {
                targetDrop = drops.get(0);
                System.out.println("[BlockAI] ItemRecoveryController: Drop detected. Moving to collect.");
                state = CollectionState.MOVING_TO_DROP;
                timeoutTicks = 0;
                // Issue ONE movement request
                player.movementController.setPath(List.of(targetDrop.blockPosition()));
            }
        } 
        else if (state == CollectionState.MOVING_TO_DROP) {
            timeoutTicks++;
            if (timeoutTicks > 200) {
                System.out.println("[BlockAI] ItemRecoveryController: Movement to drop timed out.");
                state = CollectionState.FAILED;
                return state;
            }
            
            if (!targetDrop.isAlive()) {
                // It was picked up or destroyed while we were moving
                System.out.println("[BlockAI] ItemRecoveryController: Drop disappeared while moving.");
                state = CollectionState.SUCCESS; // we will verify inventory anyway
                return state;
            }

            if (!player.movementController.hasPath()) {
                // Arrived
                System.out.println("[BlockAI] ItemRecoveryController: Arrived at drop. Waiting for pickup.");
                state = CollectionState.WAITING_FOR_PICKUP;
                timeoutTicks = 0;
            }
        }
        else if (state == CollectionState.WAITING_FOR_PICKUP) {
            timeoutTicks++;
            
            if (!targetDrop.isAlive()) {
                System.out.println("[BlockAI] ItemRecoveryController: Drop disappeared (likely picked up).");
                state = CollectionState.SUCCESS;
                return state;
            }
            
            // Re-adjust if it moved slightly
            double distSqr = player.distanceToSqr(targetDrop);
            if (distSqr > 4.0 && timeoutTicks % 20 == 0) {
                 player.movementController.setPath(List.of(targetDrop.blockPosition()));
            }

            if (timeoutTicks > 100) {
                System.out.println("[BlockAI] ItemRecoveryController: Pickup timed out. Drop might be stuck or uncollectable.");
                state = CollectionState.FAILED;
            }
        }

        return state;
    }

    public void stop() {
        state = CollectionState.IDLE;
        sourcePos = null;
        targetDrop = null;
    }
}
