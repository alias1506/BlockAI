package com.blockai.gathering;

import com.blockai.ai.AIPlayer;
import com.blockai.inventory.InventoryVerifier;
import com.blockai.movement.MovementController.MovementState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PickupController {

    private String dropMovementRequestId = null;
    private boolean initialScanDone = false;
    
    public void stop(AIPlayer player) {
        if (dropMovementRequestId != null) {
            player.movementController.cancelRequest(dropMovementRequestId);
        }
        dropMovementRequestId = null;
        initialScanDone = false;
    }

    public void tick(AIPlayer player, BlockGatherOperation operation) {
        ServerLevel level = (ServerLevel) player.level();
        
        switch (operation.state) {
            case WAITING_FOR_DROP:
                if (!initialScanDone) {
                    System.out.println("[BLOCK " + operation.operationId + "] [DROP_DEBUG]");
                    System.out.println("  brokenBlock=" + operation.blockPosition);
                    System.out.println("  expectedDrops=" + operation.expectedDrops.stream().map(is -> is.getItem().toString() + " x" + is.getCount()).collect(Collectors.joining(", ")));
                    
                    AABB scanBox = new AABB(operation.blockPosition).inflate(2.5);
                    List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, scanBox);
                    
                    System.out.println("  nearby ItemEntities=" + nearby.size());
                    
                    for (ItemEntity item : nearby) {
                        double dist = Math.sqrt(item.distanceToSqr(operation.blockPosition.getX() + 0.5, operation.blockPosition.getY() + 0.5, operation.blockPosition.getZ() + 0.5));
                        System.out.println("  entityId=" + item.getId());
                        System.out.println("  item=" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem().getItem()));
                        System.out.println("  count=" + item.getItem().getCount());
                        System.out.println("  position=(" + String.format("%.2f", item.getX()) + "," + String.format("%.2f", item.getY()) + "," + String.format("%.2f", item.getZ()) + ")");
                        System.out.println("  distanceToBrokenBlock=" + String.format("%.2f", dist));
                        System.out.println("  age=" + item.tickCount);
                        System.out.println("  alive=" + item.isAlive());
                        System.out.println("  ---");
                        
                        // Check if it matches expected drops and is relatively new (age < 60)
                        boolean matchesExpected = operation.expectedDrops.stream().anyMatch(e -> e.getItem().equals(item.getItem().getItem()));
                        if (matchesExpected && item.tickCount < 60) {
                            operation.discoveredDrops.add(item);
                        }
                    }
                    initialScanDone = true;
                }

                if (!operation.discoveredDrops.isEmpty()) {
                    operation.currentDrop = operation.discoveredDrops.poll();
                    System.out.println("[BLOCK " + operation.operationId + "] DROP_FOUND entityId=" + operation.currentDrop.getId() + " item=" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(operation.currentDrop.getItem().getItem()));
                    operation.state = BlockGatherOperation.OperationState.MOVING_TO_DROP;
                } else {
                    long elapsed = level.getGameTime() - operation.miningCompletedTick;
                    if (elapsed > 40) { // wait a maximum of 2 seconds for server to spawn the drop entity
                        System.out.println("[BLOCK " + operation.operationId + "] PICKUP_TIMEOUT - Expected drops never appeared.");
                        operation.state = BlockGatherOperation.OperationState.PICKUP_FAILED;
                    } else {
                        // Scan again next tick
                        initialScanDone = false; 
                    }
                }
                break;

            case MOVING_TO_DROP:
                if (dropMovementRequestId == null) {
                    dropMovementRequestId = "DRP_" + UUID.randomUUID().toString().substring(0, 8);
                    System.out.println("[BLOCK " + operation.operationId + "] MOVING_TO_DROP entityId=" + operation.currentDrop.getId());
                    player.movementController.setPath(List.of(operation.currentDrop.blockPosition()), dropMovementRequestId);
                }
                
                MovementState movState = player.movementController.getState();
                
                if (movState == MovementState.FAILED || movState == MovementState.CANCELLED) {
                    System.out.println("[BLOCK " + operation.operationId + "] Path to drop failed.");
                    operation.state = BlockGatherOperation.OperationState.PICKUP_FAILED;
                } else if (movState == MovementState.ARRIVED || movState == MovementState.NONE) {
                    double dist = player.position().distanceTo(operation.currentDrop.position());
                    if (dist > 2.0) { // Too far for vanilla pickup
                        System.out.println("[BLOCK " + operation.operationId + "] ARRIVED_NEAR_DROP but too far (" + dist + "). Repathing to ItemEntity.");
                        dropMovementRequestId = null; // Re-request next tick using updated entity position
                    } else {
                        System.out.println("[BLOCK " + operation.operationId + "] ARRIVED_NEAR_DROP (Inside vanilla pickup range: " + dist + ")");
                        operation.pickupStartTick = level.getGameTime();
                        operation.state = BlockGatherOperation.OperationState.WAITING_FOR_VANILLA_PICKUP;
                    }
                }
                break;

            case WAITING_FOR_VANILLA_PICKUP:
                if (player.tickCount % 20 == 0) {
                    System.out.println("[BlockAI][PICKUP_HEARTBEAT]");
                    System.out.println("  state=WAITING_FOR_VANILLA_PICKUP");
                    System.out.println("  aiPosition=" + player.position());
                    System.out.println("  aiBBox=" + player.getBoundingBox());
                    System.out.println("  aiAlive=" + player.isAlive());
                    System.out.println("  aiSpectator=" + player.isSpectator());
                    System.out.println("  aiInventorySize=" + player.getInventory().getContainerSize());
                    System.out.println("  dropPosition=" + operation.currentDrop.position());
                    System.out.println("  dropBBox=" + operation.currentDrop.getBoundingBox());
                    System.out.println("  dropAlive=" + operation.currentDrop.isAlive());
                    System.out.println("  dropAge=" + operation.currentDrop.tickCount);
                }

                boolean isPickedUp = false;
                // Wait, if it died from a merge, it is NOT picked up unless it entered our inventory.
                // However, we only do the verification step if we think it was picked up.
                // We'll proceed to verify if it's dead, but the inventory verification will fail if it was just a merge.
                if (!operation.currentDrop.isAlive()) {
                    isPickedUp = true;
                }
                
                // Continuous distance check - if item slides away, move back to MOVING_TO_DROP
                if (!isPickedUp && operation.currentDrop.isAlive()) {
                    double dist = player.position().distanceTo(operation.currentDrop.position());
                    if (dist > 2.0) {
                        System.out.println("[BLOCK " + operation.operationId + "] Item slid out of range (" + dist + "). Chasing ItemEntity.");
                        dropMovementRequestId = null;
                        operation.state = BlockGatherOperation.OperationState.MOVING_TO_DROP;
                        break;
                    }
                }
                
                long pickupElapsed = level.getGameTime() - operation.pickupStartTick;
                // Temporarily removed timeout so AI waits indefinitely for diagnostic purposes
                
                if (isPickedUp) {
                    System.out.println("[BLOCK " + operation.operationId + "] Vanilla pickup pop detected. Verifying inventory...");
                    operation.state = BlockGatherOperation.OperationState.VERIFYING_INVENTORY;
                }
                break;

            case VERIFYING_INVENTORY:
                Map<String, Integer> newSnap = InventoryVerifier.captureSnapshot(player);
                Map<String, Integer> delta = InventoryVerifier.calculateDelta(operation.preBreakInventory, newSnap);
                
                boolean verified = false;
                
                // Check if any expected drop actually entered the inventory
                for (net.minecraft.world.item.ItemStack expected : operation.expectedDrops) {
                    String expectedId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(expected.getItem()).toString();
                    if (delta.containsKey(expectedId)) {
                        verified = true;
                        System.out.println("[INVENTORY] Pickup verified:\n" + expectedId + " +" + delta.get(expectedId));
                        break;
                    }
                }
                
                if (verified) {
                    System.out.println("[BLOCK " + operation.operationId + "] GATHER_SUCCESS");
                    if (operation.discoveredDrops.isEmpty()) {
                        operation.state = BlockGatherOperation.OperationState.COMPLETE;
                    } else {
                        // Move to next drop from this block
                        operation.currentDrop = operation.discoveredDrops.poll();
                        dropMovementRequestId = null;
                        initialScanDone = false;
                        operation.state = BlockGatherOperation.OperationState.MOVING_TO_DROP;
                    }
                } else {
                    long verifyElapsed = level.getGameTime() - operation.pickupStartTick;
                    if (verifyElapsed > 40) { // Allow up to 2 seconds for inventory sync
                        System.out.println("[BLOCK " + operation.operationId + "] Inventory delta empty. PICKUP_FAILED.");
                        operation.state = BlockGatherOperation.OperationState.PICKUP_FAILED;
                    }
                }
                break;
                
            case PICKUP_FAILED:
                if (operation.retryCount < 2) {
                    operation.retryCount++;
                    System.out.println("[BLOCK " + operation.operationId + "] Retrying pickup... (Attempt " + operation.retryCount + ")");
                    dropMovementRequestId = null;
                    initialScanDone = false;
                    operation.state = BlockGatherOperation.OperationState.WAITING_FOR_DROP;
                } else {
                    operation.state = BlockGatherOperation.OperationState.FAILED;
                    System.out.println("[BLOCK " + operation.operationId + "] GATHER_FAILED");
                }
                break;
                
            default:
                break;
        }
    }
}
