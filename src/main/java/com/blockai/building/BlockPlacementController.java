package com.blockai.building;

import com.blockai.ai.AIPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class BlockPlacementController {
    public enum PlacementState {
        IDLE,
        PLACING,
        VERIFYING,
        SUCCESS,
        FAILED
    }
    
    private PlacementState state = PlacementState.IDLE;
    private BlockPos targetPos;
    private ItemStack placeStack;
    private int waitTicks = 0;
    
    public void startPlacement(AIPlayer player, BlockPos targetPos, int inventorySlot) {
        this.targetPos = targetPos;
        this.state = PlacementState.PLACING;
        
        // Equip item
        ItemStack mainHand = player.getMainHandItem();
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        ItemStack temp = inv.getItem(inventorySlot);
        inv.setItem(inventorySlot, mainHand);
        player.setItemInHand(InteractionHand.MAIN_HAND, temp);
        this.placeStack = temp;
    }
    
    public PlacementState tick(AIPlayer player) {
        if (state == PlacementState.IDLE) return state;
        
        ServerLevel level = (ServerLevel) player.level();
        
        if (state == PlacementState.PLACING) {
            // Find adjacent block to place against
            BlockPos placeAgainst = null;
            Direction placeDir = null;
            
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = targetPos.relative(dir);
                BlockState neighborState = level.getBlockState(neighbor);
                if (!neighborState.isAir() && !neighborState.canBeReplaced()) {
                    placeAgainst = neighbor;
                    placeDir = dir.getOpposite(); // The face of the neighbor we click on
                    break;
                }
            }
            
            if (placeAgainst == null) {
                System.out.println("[BlockAI] BlockPlacementController: No adjacent block to place against at " + targetPos);
                state = PlacementState.FAILED;
                return state;
            }
            
            // Face target
            faceTarget(player, targetPos);
            
            // Perform placement
            Vec3 hitVec = Vec3.atCenterOf(placeAgainst).add(new Vec3(placeDir.getStepX(), placeDir.getStepY(), placeDir.getStepZ()).scale(0.5));
            BlockHitResult hitResult = new BlockHitResult(hitVec, placeDir, placeAgainst, false);
            
            InteractionResult result = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hitResult);
            
            if (result.consumesAction() || result == InteractionResult.SUCCESS) {
                state = PlacementState.VERIFYING;
                waitTicks = 2; // Wait a moment for block update
            } else {
                System.out.println("[BlockAI] BlockPlacementController: useItemOn failed/did not consume action.");
                state = PlacementState.FAILED;
            }
        } else if (state == PlacementState.VERIFYING) {
            if (waitTicks > 0) {
                waitTicks--;
                return state;
            }
            BlockState currentState = level.getBlockState(targetPos);
            if (!currentState.isAir() && !currentState.canBeReplaced()) {
                System.out.println("[BlockAI] BlockPlacementController: Block placement confirmed at " + targetPos);
                state = PlacementState.SUCCESS;
            } else {
                System.out.println("[BlockAI] BlockPlacementController: Block did not appear at " + targetPos);
                state = PlacementState.FAILED;
            }
        }
        
        return state;
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
    
    public void reset() {
        state = PlacementState.IDLE;
        targetPos = null;
        placeStack = null;
    }
    
    public PlacementState getState() {
        return state;
    }
}
