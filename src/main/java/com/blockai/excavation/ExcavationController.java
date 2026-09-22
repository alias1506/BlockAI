package com.blockai.excavation;

import com.blockai.ai.AIPlayer;
import com.blockai.execution.TaskResult;
import com.blockai.gathering.BlockGatherOperation;
import com.blockai.gathering.MiningController;
import com.blockai.gathering.PickupController;
import com.blockai.inventory.InventoryVerifier;
import com.blockai.movement.InteractionPositionPlanner;
import com.blockai.movement.MovementController.MovementState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class ExcavationController {

    public enum ExcavationState {
        SEARCH_RESOURCE,
        FIND_ACCESS_POSITION,
        REQUEST_MOVEMENT,
        MOVING_TO_TARGET,
        ARRIVED,
        DO_MINING,
        DO_PICKUP,
        GATHERED
    }

    private boolean active = false;
    private BlockPos center = null;
    private int width = 1;
    private int length = 1;
    private int minX, maxX, minZ, maxZ;
    private int bottomY = -64;
    
    private final Set<BlockPos> completedTargets = new HashSet<>();
    private final Set<BlockPos> failedTargets = new HashSet<>();
    private BlockPos currentTarget = null;
    private BlockPos currentAccessPosition = null;

    private final MiningController miningAction = new MiningController();
    private final PickupController pickupController = new PickupController();
    private int tickDelay = 0;

    private ExcavationState state = ExcavationState.SEARCH_RESOURCE;
    
    private BlockGatherOperation currentOperation = null;
    
    public boolean isActive() {
        return active;
    }

    public void stop(AIPlayer player) {
        active = false;
        center = null;
        currentTarget = null;
        currentAccessPosition = null;
        completedTargets.clear();
        failedTargets.clear();
        state = ExcavationState.SEARCH_RESOURCE;
        pickupController.stop(player);
        player.movementController.stop();
        currentOperation = null;
    }

    public void start(AIPlayer player) {
        this.center = player.blockPosition();
        this.width = 5;
        this.length = 5;
        
        int halfWidth = width / 2;
        int halfLength = length / 2;
        
        this.minX = center.getX() - halfWidth;
        this.maxX = center.getX() + halfWidth;
        this.minZ = center.getZ() - halfLength;
        this.maxZ = center.getZ() + halfLength;
        
        this.bottomY = Math.max(-64, center.getY() - 5);
        
        this.active = true;
        this.completedTargets.clear();
        this.failedTargets.clear();
        this.state = ExcavationState.SEARCH_RESOURCE;
        this.currentOperation = null;
        
        System.out.println("[BlockAI] Excavation task started at " + center + " size=" + width + "x" + length + " down to Y=" + bottomY);
    }

    private BlockPos findNextTarget(ServerLevel level, AIPlayer player) {
        int startY = player.blockPosition().getY() + 1; 
        
        for (int y = startY; y >= bottomY; y--) {
            BlockPos closest = null;
            double minDist = Double.MAX_VALUE;
            
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    
                    if (completedTargets.contains(p) || failedTargets.contains(p)) {
                        continue;
                    }
                    
                    BlockState bs = level.getBlockState(p);
                    if (!bs.isAir() && bs.getDestroySpeed(level, p) >= 0.0f) {
                        double dist = p.distSqr(player.blockPosition());
                        if (dist < minDist) {
                            minDist = dist;
                            closest = p;
                        }
                    }
                }
            }
            if (closest != null) {
                return closest;
            }
        }
        return null;
    }

    public TaskResult tick(AIPlayer player) {
        if (!active) return TaskResult.IDLE;
        if (!player.canExecuteAction()) return TaskResult.RUNNING;
        
        if (tickDelay > 0) {
            tickDelay--;
            return TaskResult.RUNNING;
        }

        ServerLevel level = (ServerLevel) player.level();

        switch (state) {
            case SEARCH_RESOURCE:
                currentTarget = findNextTarget(level, player);
                if (currentTarget == null) {
                    System.out.println("[BlockAI] ExcavationController: Area clear.");
                    active = false;
                    return TaskResult.SUCCESS;
                }
                
                state = ExcavationState.FIND_ACCESS_POSITION;
                break;
                
            case FIND_ACCESS_POSITION:
                currentAccessPosition = InteractionPositionPlanner.getBestAccessPosition(player, currentTarget, 3.0);
                
                if (currentAccessPosition == null) {
                    failedTargets.add(currentTarget);
                    state = ExcavationState.SEARCH_RESOURCE;
                    break;
                }
                
                // Initialize the block operation here
                BlockState targetState = level.getBlockState(currentTarget);
                
                LootParams.Builder builder = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(currentTarget))
                    .withParameter(LootContextParams.TOOL, player.getMainHandItem())
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, player);
                
                List<ItemStack> expectedDrops = targetState.getDrops(builder);
                
                currentOperation = new BlockGatherOperation(currentTarget, targetState, expectedDrops, level.getGameTime());
                System.out.println("[BLOCK " + currentOperation.operationId + "] SELECTED " + BuiltInRegistries.BLOCK.getKey(targetState.getBlock()) + " at " + currentTarget);
                
                state = ExcavationState.REQUEST_MOVEMENT;
                break;
                
            case REQUEST_MOVEMENT:
                player.movementController.setPath(List.of(currentAccessPosition));
                state = ExcavationState.MOVING_TO_TARGET;
                break;
                
            case MOVING_TO_TARGET:
                MovementState movState = player.movementController.getState();
                if (movState == MovementState.FAILED || movState == MovementState.CANCELLED) {
                    failedTargets.add(currentTarget);
                    state = ExcavationState.SEARCH_RESOURCE;
                } else if (movState == MovementState.ARRIVED || movState == MovementState.NONE) {
                    state = ExcavationState.ARRIVED;
                }
                break;
                
            case ARRIVED:
                miningAction.startMining(player, currentTarget);
                state = ExcavationState.DO_MINING;
                break;
                
            case DO_MINING:
                MiningController.MiningState ms = miningAction.tick(player);
                if (ms == MiningController.MiningState.FAILED) {
                    failedTargets.add(currentTarget);
                    state = ExcavationState.SEARCH_RESOURCE;
                } else if (ms == MiningController.MiningState.SUCCESS) {
                    currentOperation.miningCompletedTick = level.getGameTime();
                    System.out.println("[BLOCK " + currentOperation.operationId + "] BREAK_CONFIRMED");
                    
                    if (currentOperation.expectedDrops.isEmpty()) {
                        System.out.println("[BLOCK " + currentOperation.operationId + "] No collectible drop expected from this block. COMPLETE");
                        currentOperation.state = BlockGatherOperation.OperationState.COMPLETE;
                        state = ExcavationState.GATHERED;
                    } else {
                        // Snapshot inventory and item entities BEFORE we process the drop
                        currentOperation.preBreakInventory = InventoryVerifier.captureSnapshot(player);
                        AABB searchBox = new AABB(currentTarget).inflate(10.0);
                        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchBox);
                        for (ItemEntity ie : items) {
                            currentOperation.preBreakItemEntityIds.add(ie.getId());
                        }
                        
                        currentOperation.state = BlockGatherOperation.OperationState.WAITING_FOR_DROP;
                        state = ExcavationState.DO_PICKUP;
                    }
                }
                break;
                
            case DO_PICKUP:
                pickupController.tick(player, currentOperation);
                
                if (currentOperation.state == BlockGatherOperation.OperationState.COMPLETE) {
                    System.out.println("[BLOCK " + currentOperation.operationId + "] Block excavated and gathered at " + currentTarget);
                    state = ExcavationState.GATHERED;
                } else if (currentOperation.state == BlockGatherOperation.OperationState.FAILED) {
                    failedTargets.add(currentTarget);
                    state = ExcavationState.SEARCH_RESOURCE;
                }
                break;

            case GATHERED:
                completedTargets.add(currentTarget);
                currentOperation = null;
                System.out.println("[BlockAI] ExcavationController: Selecting next target");
                state = ExcavationState.SEARCH_RESOURCE;
                break;
        }

        return TaskResult.RUNNING;
    }
}
