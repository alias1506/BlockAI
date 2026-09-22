package com.blockai.gathering;

import com.blockai.ai.AIPlayer;
import com.blockai.ai.memory.FailureMemory;
import com.blockai.execution.TaskResult;
import com.blockai.inventory.InventoryVerifier;
import com.blockai.movement.InteractionPositionPlanner;
import com.blockai.movement.MovementController.MovementState;
import com.blockai.world.WorldScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class GatheringController {

    public enum GatheringState {
        IDLE,
        SEARCH_RESOURCE,
        ANALYZE_STRUCTURE,
        SELECT_TARGET,
        FIND_ACCESS_POSITION,
        REQUEST_MOVEMENT,
        MOVING,
        ARRIVED,
        DO_MINING,
        DO_PICKUP,
        COLLECTED_ONE,
        NEXT_TARGET,
        PAUSED_PICKUP_FAILURE,
        DONE
    }

    private GatheringState state = GatheringState.IDLE;
    private ResourceResolver requirement;
    private String currentTaskId;
    private int searchRadius = 16;
    private int searchExpansions = 0;
    
    private BlockPos currentSeed;
    private List<BlockPos> resourceStructure = new ArrayList<>();
    private BlockPos currentTarget;
    private BlockPos currentAccessPosition;
    
    private final MiningController miningAction = new MiningController();
    private final PickupController pickupController = new PickupController();
    
    private int consecutiveFailures = 0;
    private int tickDelay = 0;
    private boolean GATHER_ONE_AT_A_TIME = false;

    private BlockGatherOperation currentOperation = null;

    private BlockPos findClosestResource(ServerLevel level, BlockPos playerPos, int searchRadius, ResourceResolver requirement) {
        BlockPos closest = null;
        double minDist = searchRadius * searchRadius;
        for (Map.Entry<String, Set<BlockPos>> entry : WorldScanner.MEMORY.getAll().entrySet()) {
            if (requirement.acceptsBlock(entry.getKey())) {
                for (BlockPos pos : entry.getValue()) {
                    if (FailureMemory.getRecord(pos) != null) continue;
                    double dist = pos.distSqr(playerPos);
                    if (dist < minDist) {
                        minDist = dist;
                        closest = pos;
                    }
                }
            }
        }
        return closest;
    }

    private BlockPos selectBestTarget(AIPlayer player, List<BlockPos> targets) {
        if (targets.isEmpty()) return null;
        BlockPos playerPos = player.blockPosition();
        BlockPos best = null;
        double minDist = Double.MAX_VALUE;
        for (BlockPos pos : targets) {
            if (FailureMemory.getRecord(pos) != null) continue;
            double dist = pos.distSqr(playerPos);
            if (dist < minDist) {
                minDist = dist;
                best = pos;
            }
        }
        return best;
    }

    public void start(AIPlayer player, ResourceResolver requirement, String taskId) {
        this.requirement = requirement;
        this.currentTaskId = taskId;
        this.state = GatheringState.SEARCH_RESOURCE;
        this.searchRadius = 16;
        this.searchExpansions = 0;
        this.consecutiveFailures = 0;
        this.resourceStructure.clear();
        this.currentOperation = null;
        this.pickupController.stop(player);
        player.movementController.stop();
        System.out.println("[BlockAI][GATHER] Started gathering task: " + requirement.getCategory());
    }

    public void stop(AIPlayer player) {
        this.state = GatheringState.IDLE;
        player.movementController.stop();
        this.pickupController.stop(player);
        this.currentOperation = null;
    }

    public boolean isActive() {
        return state != GatheringState.IDLE && state != GatheringState.DONE;
    }

    public TaskResult tick(AIPlayer player) {
        if (!player.canExecuteAction()) return TaskResult.RUNNING;
        if (state == GatheringState.IDLE) return TaskResult.IDLE;
        
        // HARD GUARD: NEVER proceed to next tasks if current operation is unresolved
        if (currentOperation != null && 
            currentOperation.state != BlockGatherOperation.OperationState.COMPLETE && 
            currentOperation.state != BlockGatherOperation.OperationState.FAILED) {
            
            // Only DO_PICKUP and DO_MINING states are allowed while operation is active
            if (state != GatheringState.DO_PICKUP && state != GatheringState.DO_MINING) {
                System.out.println("[BlockAI][GATHER] FATAL: Invalid state " + state + " while operation is active. Forcing to DO_PICKUP.");
                state = GatheringState.DO_PICKUP;
            }
        }

        
        if (tickDelay > 0) {
            tickDelay--;
            return TaskResult.RUNNING;
        }
        
        ServerLevel level = (ServerLevel) player.level();
        
        switch (state) {
            case SEARCH_RESOURCE:
                BlockPos foundPos = findClosestResource(level, player.blockPosition(), searchRadius, requirement);
                if (foundPos != null) {
                    currentSeed = foundPos;
                    System.out.println("[BlockAI][GATHER] FOUND_SEED at " + currentSeed);
                    state = GatheringState.ANALYZE_STRUCTURE;
                } else {
                    if (searchExpansions < 3) {
                        searchRadius += 16;
                        searchExpansions++;
                        tickDelay = 10;
                    } else {
                        state = GatheringState.DONE;
                        return TaskResult.NO_TARGET;
                    }
                }
                break;
                
            case ANALYZE_STRUCTURE:
                resourceStructure = TargetSelector.analyzeStructure(level, currentSeed, requirement, 8);
                state = GatheringState.SELECT_TARGET;
                break;
                
            case SELECT_TARGET:
                if (resourceStructure.isEmpty()) {
                    state = GatheringState.SEARCH_RESOURCE;
                    break;
                }
                
                currentTarget = selectBestTarget(player, resourceStructure);
                if (currentTarget == null) {
                    FailureMemory.recordFailure(currentSeed, "UNREACHABLE_CLUSTER");
                    state = GatheringState.SEARCH_RESOURCE;
                    break;
                }
                
                state = GatheringState.FIND_ACCESS_POSITION;
                break;
                
            case FIND_ACCESS_POSITION:
                currentAccessPosition = InteractionPositionPlanner.getBestAccessPosition(player, currentTarget, 3.0);
                if (currentAccessPosition == null) {
                    FailureMemory.recordFailure(currentTarget, "NO_ACCESS");
                    resourceStructure.remove(currentTarget);
                    state = GatheringState.SELECT_TARGET;
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
                System.out.println("[BLOCK " + currentOperation.operationId + "] SELECTED target " + targetState.getBlock().getDescriptionId() + " at " + currentTarget);
                
                state = GatheringState.REQUEST_MOVEMENT;
                break;
                
            case REQUEST_MOVEMENT:
                player.movementController.setPath(List.of(currentAccessPosition));
                state = GatheringState.MOVING;
                break;
                
            case MOVING:
                MovementState movState = player.movementController.getState();
                if (movState == MovementState.FAILED || movState == MovementState.CANCELLED) {
                    FailureMemory.recordFailure(currentTarget, "MOVEMENT_FAILED");
                    resourceStructure.remove(currentTarget);
                    state = GatheringState.SELECT_TARGET;
                } else if (movState == MovementState.ARRIVED || movState == MovementState.NONE) {
                    state = GatheringState.ARRIVED;
                }
                break;
                
            case ARRIVED:
                miningAction.startMining(player, currentTarget);
                state = GatheringState.DO_MINING;
                break;
                
            case DO_MINING:
                MiningController.MiningState ms = miningAction.tick(player);
                if (ms == MiningController.MiningState.FAILED) {
                    FailureMemory.recordFailure(currentTarget, "MINING_FAILED");
                    resourceStructure.remove(currentTarget);
                    state = GatheringState.SELECT_TARGET;
                } else if (ms == MiningController.MiningState.SUCCESS) {
                    currentOperation.miningCompletedTick = level.getGameTime();
                    System.out.println("[BLOCK " + currentOperation.operationId + "] BREAK_CONFIRMED");
                    
                    if (currentOperation.expectedDrops.isEmpty()) {
                        System.out.println("[BLOCK " + currentOperation.operationId + "] No collectible drop expected from this block. COMPLETE");
                        currentOperation.state = BlockGatherOperation.OperationState.COMPLETE;
                        state = GatheringState.COLLECTED_ONE;
                    } else {
                        // Snapshot inventory and item entities BEFORE we process the drop
                        currentOperation.preBreakInventory = InventoryVerifier.captureSnapshot(player);
                        AABB searchBox = new AABB(currentTarget).inflate(10.0);
                        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchBox);
                        for (ItemEntity ie : items) {
                            currentOperation.preBreakItemEntityIds.add(ie.getId());
                        }
                        
                        currentOperation.state = BlockGatherOperation.OperationState.WAITING_FOR_DROP;
                        state = GatheringState.DO_PICKUP;
                    }
                }
                break;
                
            case DO_PICKUP:
                pickupController.tick(player, currentOperation);
                
                if (currentOperation.state == BlockGatherOperation.OperationState.COMPLETE) {
                    System.out.println("[BLOCK " + currentOperation.operationId + "] Block excavated and gathered at " + currentTarget);
                    state = GatheringState.COLLECTED_ONE;
                } else if (currentOperation.state == BlockGatherOperation.OperationState.FAILED) {
                    System.out.println("[BlockAI][GATHER] COLLECTION_BLOCKED. Stopping gathering operation.");
                    FailureMemory.recordFailure(currentTarget, "PICKUP_FAILED");
                    resourceStructure.remove(currentTarget);
                    state = GatheringState.PAUSED_PICKUP_FAILURE;
                }
                break;
                
            case COLLECTED_ONE:
                resourceStructure.remove(currentTarget);
                consecutiveFailures = 0;
                currentOperation = null;
                
                if (requirement.getQuantity() > 0) {
                    // Logic to count total gathered
                }
                
                System.out.println("[BlockAI][GATHER] Selecting next target");
                state = GatheringState.NEXT_TARGET;
                break;
                
            case NEXT_TARGET:
                state = GatheringState.SELECT_TARGET;
                break;
                
            case PAUSED_PICKUP_FAILURE:
                return TaskResult.FAILED;
                
            case DONE:
                return TaskResult.SUCCESS;
        }
        
        return TaskResult.RUNNING;
    }
}
