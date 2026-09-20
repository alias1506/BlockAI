package com.blockai.ai;

import com.blockai.player.AIPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Controller for excavating a volume layer by layer downward.
 */
public class ExcavationController {

    private boolean active = false;
    private BlockPos center = null;
    private int width = 1;
    private int length = 1;
    private int minX, maxX, minZ, maxZ;
    private int bottomY = -64; // default to bedrock bottom
    
    private final Set<BlockPos> completedTargets = new HashSet<>();
    private final Set<BlockPos> failedTargets = new HashSet<>();
    private BlockPos currentTarget = null;
    private BlockPos interactPos = null;
    private int consecutiveFailures = 0;

    private final MiningAction miningAction = new MiningAction();
    private int tickDelay = 0;
    private boolean needsScan = true;

    public boolean isActive() {
        return active;
    }

    public void stop() {
        active = false;
        center = null;
        currentTarget = null;
        interactPos = null;
        completedTargets.clear();
        failedTargets.clear();
        // Wait, stop() doesn't receive player in ExcavationController.
        // We will pass null or remove clearCrack from cancel, but cancel NEEDS player to clear crack.
        // Actually, we can just leave it as is if I modify MiningAction to store player or not.
        // Let's modify ExcavationController.stop() to take player, or just call cancel with player where we can.
        needsScan = true;
    }

    public void start(AIPlayer player, int width, int length) {
        this.center = player.blockPosition();
        this.width = width;
        this.length = length;
        
        int halfWidth = width / 2;
        int halfLength = length / 2;
        
        // Handle even-sized width/length if necessary, but assume centered on the player block
        this.minX = center.getX() - halfWidth;
        this.maxX = center.getX() + halfWidth;
        this.minZ = center.getZ() - halfLength;
        this.maxZ = center.getZ() + halfLength;
        
        this.bottomY = -64; // Minecraft 1.18+ minimum build height
        this.active = true;
        this.needsScan = true;
        this.currentTarget = null;
        this.interactPos = null;
        this.consecutiveFailures = 0;
        this.completedTargets.clear();
        this.failedTargets.clear();
        this.miningAction.cancel();

        System.out.println("[BlockAI] Excavation plan:");
        System.out.println("[BlockAI]   center=" + center);
        System.out.println("[BlockAI]   width=" + width);
        System.out.println("[BlockAI]   length=" + length);
        System.out.println("[BlockAI]   minX=" + minX);
        System.out.println("[BlockAI]   maxX=" + maxX);
        System.out.println("[BlockAI]   minZ=" + minZ);
        System.out.println("[BlockAI]   maxZ=" + maxZ);
        System.out.println("[BlockAI]   bottomY=" + bottomY);
    }

    private BlockPos findBestTarget(ServerLevel level) {
        int highestY = bottomY - 1;
        BlockPos bestPos = null;
        double bestDistSqr = Double.MAX_VALUE;
        int remainingInLayer = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Scan down from a reasonable surface point
                for (int y = center.getY() + 10; y >= bottomY; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    
                    if (failedTargets.contains(pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    float hardness = state.getDestroySpeed(level, pos);
                    if (hardness < 0) {
                        // Bedrock or unbreakable. Mark as failed so we ignore it in future scans.
                        failedTargets.add(pos);
                        continue;
                    }

                    if (y > highestY) {
                        highestY = y;
                        bestPos = pos;
                        bestDistSqr = pos.distManhattan(center);
                        remainingInLayer = 1;
                    } else if (y == highestY) {
                        remainingInLayer++;
                        double dist = pos.distManhattan(center);
                        if (dist < bestDistSqr) {
                            bestDistSqr = dist;
                            bestPos = pos;
                        }
                    }
                    // Since we found the highest block in this column, stop scanning down this column
                    break;
                }
            }
        }
        
        if (bestPos != null && needsScan) {
            System.out.println("[BlockAI] Current layer Y=" + highestY);
            System.out.println("[BlockAI] Remaining blocks in layer=" + remainingInLayer);
            needsScan = false; // Prevents spamming this log for every block in the layer
        } else if (bestPos == null) {
            needsScan = true; // reset for next layer/scan
        }
        
        return bestPos;
    }

    public ControllerState tick(AIPlayer player) {
        if (!active) return ControllerState.IDLE;
        if (!player.canExecuteAction()) return ControllerState.IDLE;

        if (tickDelay > 0) {
            tickDelay--;
            return ControllerState.WAITING;
        }

        ServerLevel level = (ServerLevel) player.level();

        // If currently mining or collecting drops, continue
        MiningAction.MiningState actionState = miningAction.getState();
        if (actionState != MiningAction.MiningState.IDLE) {
            MiningAction.MiningState result = miningAction.tick(player);
            if (result == MiningAction.MiningState.SUCCESS) {
                System.out.println("[BlockAI] ExcavationController: Block excavated and gathered at " + currentTarget);
                completedTargets.add(currentTarget);
                WorldObserver.MEMORY.removeBlockByPos(currentTarget);
                currentTarget = null;
                miningAction.reset();
                tickDelay = 5;
            } else if (result == MiningAction.MiningState.FAILED) {
                System.out.println("[BlockAI] ExcavationController: Failed to mine or gather at " + currentTarget);
                failedTargets.add(currentTarget);
                currentTarget = null;
                miningAction.reset();
                tickDelay = 10;
                consecutiveFailures++;
                if (consecutiveFailures >= 3) {
                    System.out.println("[BlockAI] Movement system failed for 3 independent reachable targets.");
                    System.out.println("[BlockAI] Pausing excavation until movement is repaired/recovered.");
                    active = false;
                    return ControllerState.FAILED;
                }
            }
            return ControllerState.RUNNING;
        }

        // Find next target if we don't have one
        if (currentTarget == null) {
            currentTarget = findBestTarget(level);
            
            if (currentTarget == null) {
                System.out.println("[BlockAI] ExcavationController: Excavation COMPLETE. " +
                        completedTargets.size() + " blocks excavated, " +
                        failedTargets.size() + " failed/unbreakable.");
                active = false;
                return ControllerState.SUCCESS;
            }
            
            BlockState targetState = level.getBlockState(currentTarget);
            System.out.println("[BlockAI] ExcavationController: Next target — " +
                    BuiltInRegistries.BLOCK.getKey(targetState.getBlock()) +
                    " at " + currentTarget);
                    
            // Check if we can reach it already
            InteractionValidator.ValidationResult validation = InteractionValidator.validate(player, currentTarget);
            if (validation == InteractionValidator.ValidationResult.VALID) {
                miningAction.startMining(player, currentTarget);
                return ControllerState.RUNNING;
            }

            // Path to an interaction position
            interactPos = InteractionValidator.findInteractionPosition(level, player, currentTarget);
            if (interactPos != null) {
                var path = com.blockai.ai.pathing.AStarPathfinder.findPath(level, player.blockPosition(), interactPos);
                if (path != null) {
                    player.movementController.setPath(path);
                    return ControllerState.RUNNING;
                }
            }

            System.out.println("[BlockAI] ExcavationController: Target rejected — no path to interaction position at " + currentTarget);
            failedTargets.add(currentTarget);
            currentTarget = null;
            tickDelay = 5;
            
            consecutiveFailures++;
            if (consecutiveFailures >= 3) {
                System.out.println("[BlockAI] Movement system failed for 3 independent reachable targets.");
                active = false;
                return ControllerState.FAILED;
            }
            return ControllerState.RUNNING;
        }

        // We have a target, check movement state
        com.blockai.ai.pathing.MovementController.MovementState movState = player.movementController.getState();
        
        if (movState == com.blockai.ai.pathing.MovementController.MovementState.PATH_FAILED) {
            System.out.println("[BlockAI] ExcavationController: Movement blocked to " + currentTarget);
            failedTargets.add(currentTarget);
            player.movementController.stop();
            currentTarget = null;
            tickDelay = 5;
            consecutiveFailures++;
            if (consecutiveFailures >= 3) {
                System.out.println("[BlockAI] Movement system failed for 3 independent reachable targets.");
                active = false;
                return ControllerState.FAILED;
            }
            return ControllerState.RUNNING;
        }

        if (movState == com.blockai.ai.pathing.MovementController.MovementState.ARRIVED || movState == com.blockai.ai.pathing.MovementController.MovementState.IDLE) {
            // Re-validate now that we have arrived
            InteractionValidator.ValidationResult validation = InteractionValidator.validate(player, currentTarget);
            if (validation == InteractionValidator.ValidationResult.VALID) {
                consecutiveFailures = 0; // Reset failures on successful arrival
                miningAction.startMining(player, currentTarget);
                player.movementController.stop();
                return ControllerState.RUNNING;
            } else {
                System.out.println("[BlockAI] ExcavationController: Target rejected upon arrival — " + validation + " at " + currentTarget);
                failedTargets.add(currentTarget);
                player.movementController.stop();
                currentTarget = null;
                tickDelay = 5;
                consecutiveFailures++;
                if (consecutiveFailures >= 3) {
                    System.out.println("[BlockAI] Movement system failed for 3 independent reachable targets.");
                    active = false;
                    return ControllerState.FAILED;
                }
                return ControllerState.RUNNING;
            }
        }

        return ControllerState.RUNNING;
    }
}
