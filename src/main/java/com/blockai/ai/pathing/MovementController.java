package com.blockai.ai.pathing;

import com.blockai.player.AIPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MovementController {

    public enum MovementState {
        IDLE, FOLLOWING_PATH, STUCK, PATH_FAILED, ARRIVED
    }

    private List<BlockPos> currentPath = null;
    private int currentPathIndex = 0;
    private int stuckTicks = 0;
    private Vec3 lastPosition = null;
    private MovementState state = MovementState.IDLE;
    
    private int moveTicks = 0;
    
    public void setPath(List<BlockPos> path) {
        this.currentPath = path;
        this.currentPathIndex = 0;
        this.stuckTicks = 0;
        this.moveTicks = 0;
        this.lastPosition = null;
        this.state = path != null && !path.isEmpty() ? MovementState.FOLLOWING_PATH : MovementState.IDLE;
        if (this.state == MovementState.FOLLOWING_PATH) {
            System.out.println("[BlockAI] MovementController: PATH_REQUESTED. Starting movement to " + path.get(path.size() - 1));
        }
    }
    
    public void stop() {
        this.currentPath = null;
        this.stuckTicks = 0;
        this.lastPosition = null;
        this.state = MovementState.IDLE;
    }
    
    public boolean hasPath() {
        return state == MovementState.FOLLOWING_PATH && currentPath != null && currentPathIndex < currentPath.size();
    }
    
    public MovementState getState() {
        return state;
    }
    
    public MovementState tick(AIPlayer player) {
        if (!player.canExecuteAction()) return state;
        
        if (state == MovementState.PATH_FAILED || state == MovementState.ARRIVED || state == MovementState.IDLE) {
            return state;
        }
        
        if (!hasPath()) {
            state = MovementState.ARRIVED;
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
            if (distMoved < 0.01) {
                stuckTicks++;
                if (stuckTicks > 40) {
                    System.out.println("[BlockAI] Movement failed:");
                    System.out.println("  target=" + currentPath.get(currentPath.size() - 1));
                    System.out.println("  lastPosition=" + lastPosition);
                    System.out.println("  currentPosition=" + playerPos);
                    System.out.println("  distanceMoved=" + distMoved);
                    System.out.println("  velocity=" + player.getDeltaMovement());
                    System.out.println("  onGround=" + player.onGround());
                    System.out.println("  pathNode=" + currentPathIndex + "/" + currentPath.size());
                    System.out.println("  reason=COLLISION_OR_PHYSICS_BLOCKED");
                    state = MovementState.PATH_FAILED;
                    return state;
                }
            } else {
                stuckTicks = 0;
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
