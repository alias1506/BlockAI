package com.blockai.movement;

import com.blockai.ai.AIPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MovementController {

    public enum MovementState {
        NONE, REQUESTED, MOVING, ARRIVED, FAILED, CANCELLED
    }

    private List<BlockPos> currentPath = null;
    private int currentPathIndex = 0;
    private int stuckTicks = 0;
    private int zeroProgressTicks = 0;
    private Vec3 lastPosition = null;
    private MovementState state = MovementState.NONE;
    
    private int moveTicks = 0;
    private String currentRequestId = null;
    
    public String getRequestId() {
        return currentRequestId;
    }
    
    public void setPath(List<BlockPos> path, String requestId) {
        if (path == null || path.isEmpty()) {
            this.state = MovementState.NONE;
            return;
        }
        
        BlockPos newDestination = path.get(path.size() - 1);
        
        this.currentPath = path;
        this.currentPathIndex = 0;
        this.stuckTicks = 0;
        this.zeroProgressTicks = 0;
        this.moveTicks = 0;
        this.lastPosition = null;
        this.state = MovementState.REQUESTED;
        
        if (requestId != null) {
            this.currentRequestId = requestId;
        }
        System.out.println("[BlockAI] MovementController: PATH_REQUESTED. Starting movement to " + newDestination + " (ReqID: " + this.currentRequestId + ")");
    }
    
    public void setPath(List<BlockPos> path) {
        setPath(path, "MOV_" + java.util.UUID.randomUUID().toString().substring(0, 8));
    }
    
    public void stop() {
        this.currentPath = null;
        this.stuckTicks = 0;
        this.lastPosition = null;
        this.state = MovementState.NONE;
    }
    
    public void cancelRequest(String requestId) {
        if (requestId != null && requestId.equals(this.currentRequestId)) {
            System.out.println("[BlockAI] MovementController: CANCELLING request " + requestId);
            this.state = MovementState.CANCELLED;
            this.currentPath = null;
        }
    }
    
    public boolean hasPath() {
        return (state == MovementState.REQUESTED || state == MovementState.MOVING) && currentPath != null && currentPathIndex < currentPath.size();
    }
    
    public MovementState getState() {
        return state;
    }
    
    public MovementState tick(AIPlayer player) {
        if (!player.canExecuteAction()) return state;
        
        if (state == MovementState.FAILED || state == MovementState.ARRIVED || state == MovementState.CANCELLED || state == MovementState.NONE) {
            // Apply gravity and physics when idle
            player.zza = 0.0f;
            player.setJumping(false);
            player.travel(new Vec3(0, 0, 0));
            player.move(net.minecraft.world.entity.MoverType.SELF, player.getDeltaMovement());
            return state;
        }
        
        if (state == MovementState.REQUESTED) {
            state = MovementState.MOVING;
        }
        
        if (!hasPath()) {
            state = MovementState.ARRIVED;
            System.out.println("[BlockAI] MovementController: ARRIVED at target.");
            return state;
        }
        
        BlockPos target = currentPath.get(currentPathIndex);
        Vec3 playerPos = player.position();
        
        // Target center of block
        double tx = target.getX() + 0.5;
        double ty = target.getY();
        double tz = target.getZ() + 0.5;
        
        // Stuck detection
        double dx = tx - playerPos.x;
        double dz = tz - playerPos.z;
        double distanceSq = dx * dx + dz * dz;

        double distMoved = 0;
        if (lastPosition != null) {
            distMoved = lastPosition.distanceTo(playerPos);
            if (distMoved < 0.02) {
                zeroProgressTicks++;
                stuckTicks++;
                if (zeroProgressTicks == 60) {
                    System.out.println("[BlockAI] Movement warning: 60 ticks with zero progress. Recalculating path...");
                    // Try jumping if stuck
                    if (player.onGround()) {
                        player.setJumping(true);
                        player.jumpFromGround();
                    }
                }
                
                if (stuckTicks > 120) {
                    System.out.println("[BlockAI] Movement failed:");
                    System.out.println("  target=" + currentPath.get(currentPath.size() - 1));
                    System.out.println("  lastPosition=" + lastPosition);
                    System.out.println("  currentPosition=" + playerPos);
                    System.out.println("  distanceMoved=" + distMoved);
                    System.out.println("  velocity=" + player.getDeltaMovement());
                    System.out.println("  onGround=" + player.onGround());
                    System.out.println("  pathNode=" + currentPathIndex + "/" + currentPath.size());
                    System.out.println("  reason=COLLISION_OR_PHYSICS_BLOCKED_120_TICKS");
                    state = MovementState.FAILED;
                    return state;
                }
            } else {
                zeroProgressTicks = 0;
                stuckTicks = 0; // reset stuck detection if we are actively moving
            }
        }
        lastPosition = playerPos;
        
        moveTicks++;
        if (moveTicks % 10 == 0) {
            System.out.println("[BlockAI] MOVEMENT DEBUG: pos=" + playerPos + ", vel=" + player.getDeltaMovement() + ", node=" + currentPathIndex + ", dist=" + Math.sqrt(distanceSq));
        }
        
        // If close enough horizontally to the target node, move to next node
        if (distanceSq < 0.25 && Math.abs(ty - playerPos.y) < 1.5) {
            currentPathIndex++;
            if (currentPathIndex >= currentPath.size()) {
                System.out.println("[BlockAI] MovementController: ARRIVED at target.");
                state = MovementState.ARRIVED;
            }
            return state;
        }
        
        // Face the target
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        player.setYRot((float) yaw);
        player.yHeadRot = (float) yaw;
        player.yBodyRot = (float) yaw;
        
        player.zza = 1.0f; // Move forward full speed
        
        // Jump logic
        if (ty > playerPos.y + 0.5 && player.onGround()) {
            player.setJumping(true);
            player.jumpFromGround(); // Force jump velocity
        } else {
            player.setJumping(false);
        }
        
        // Let the player entity travel using its updated inputs
        player.travel(new Vec3(0, 0, player.zza));
        
        // FORCE SERVER-SIDE PHYSICAL MOVEMENT
        player.move(net.minecraft.world.entity.MoverType.SELF, player.getDeltaMovement());
        
        return state;
    }
}
