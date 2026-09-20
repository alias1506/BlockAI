package com.blockai.ai;

import com.blockai.player.AIPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Generic resource gathering controller.
 * Accepts a ResourceRequirement (category-based or specific) and gathers
 * until the required quantity is reached, checking real inventory.
 */
public class GatheringController {

    private ResourceRequirement requirement = null;
    private BlockPos currentTarget = null;
    private int tickDelay = 0;
    private int searchRadius = 16;
    private int searchExpansions = 0;
    private int consecutiveFailures = 0;
    private final MiningAction miningAction = new MiningAction();

    /**
     * Set a generic resource requirement (category-based or specific).
     */
    public void setRequirement(ResourceRequirement req) {
        this.requirement = req;
        this.currentTarget = null;
        this.miningAction.cancel();
        this.searchRadius = 16;
        this.searchExpansions = 0;

        System.out.println("[BlockAI] GatheringController: New requirement = " + req);
        System.out.println("[BlockAI]   Accepted blocks: " + req.getAcceptedBlocks());
    }

    /**
     * Legacy method — wraps a single block ID into a ResourceRequirement.
     */
    public void setTargetResource(String resource) {
        ResourceCategory cat = ResourceCategory.fromBlockId(resource);
        if (cat == null) cat = ResourceCategory.fromDescription(resource);
        if (cat == null) cat = ResourceCategory.WOOD_LOG; // absolute fallback

        setRequirement(new ResourceRequirement(cat, resource, 1));
    }

    public boolean isIdle() {
        return requirement == null;
    }

    public void stop() {
        this.requirement = null;
        this.currentTarget = null;
        this.tickDelay = 0;
        this.miningAction.cancel();
        this.searchRadius = 16;
        this.searchExpansions = 0;
        this.consecutiveFailures = 0;
    }

    public ControllerState tick(AIPlayer player) {
        if (!player.canExecuteAction()) return ControllerState.IDLE;
        if (isIdle()) return ControllerState.IDLE;

        if (tickDelay > 0) {
            tickDelay--;
            return ControllerState.WAITING;
        }

        ServerLevel level = (ServerLevel) player.level();

        // Check if requirement is already satisfied
        if (requirement.isSatisfied(player)) {
            int have = requirement.countInInventory(player);
            System.out.println("[BlockAI] GatheringController: Requirement satisfied! Have " +
                    have + "/" + requirement.getQuantity());
            return ControllerState.SUCCESS;
        }

        // If currently mining, continue
        MiningAction.MiningState actionState = miningAction.getState();
        if (actionState != MiningAction.MiningState.IDLE) {
            MiningAction.MiningState result = miningAction.tick(player);
            if (result == MiningAction.MiningState.SUCCESS) {
                System.out.println("[BlockAI] GatheringController: Block mined and gathered at " + currentTarget);
                // Remove from memory
                if (currentTarget != null) {
                    BlockState state = level.getBlockState(currentTarget);
                    String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    WorldObserver.MEMORY.removeBlock(blockName, currentTarget);
                    WorldObserver.MEMORY.removeBlockByPos(currentTarget);
                }

                // Record success in learning memory
                if (currentTarget != null) {
                    LearningMemory.getInstance().recordResourceDiscovery(
                            requirement.getCategory(), 
                            "gathered", 
                            currentTarget
                    );
                }
                
                // The item was added to the world and picked up. Verify if requirement met.
                if (requirement.isSatisfied(player)) {
                    System.out.println("[BlockAI] GatheringController: Gathering COMPLETE. " + 
                            requirement.getQuantity() + " " + requirement.getCategory() + " obtained.");
                    miningAction.reset();
                    currentTarget = null;
                    return ControllerState.SUCCESS;
                } else {
                    miningAction.reset();
                    currentTarget = null;
                    tickDelay = 5;
                    return ControllerState.RUNNING;
                }
            } else if (result == MiningAction.MiningState.FAILED) {
                System.out.println("[BlockAI] GatheringController: Failed to mine or gather at " + currentTarget);
                // Log failure to prevent infinite retries
                TargetFailureMemory.recordFailure(currentTarget, "MINING_FAILED");
                currentTarget = null;
                miningAction.reset();
                tickDelay = 10;
                
                consecutiveFailures++;
                if (consecutiveFailures >= 3) {
                    System.out.println("[BlockAI] Movement system failed for 3 independent reachable targets.");
                    stop();
                    return ControllerState.FAILED;
                }
                return ControllerState.RUNNING;
            }
            return ControllerState.RUNNING;
        }

        if (currentTarget == null) {
            // Search for ANY block matching the requirement's accepted blocks
            currentTarget = findBestCandidate(player);

            if (currentTarget == null) {
                // Expand search radius
                if (searchExpansions < 3) {
                    searchExpansions++;
                    searchRadius = Math.min(64, searchRadius * 2);
                    System.out.println("[BlockAI] GatheringController: Expanding search radius to " + searchRadius);
                    WorldObserver.scanLocalArea(player, searchRadius);
                    tickDelay = 20;
                    return ControllerState.RUNNING;
                }

                System.out.println("[BlockAI] GatheringController: No matching resource found after " + searchExpansions + " expansions.");
                tickDelay = 40;
                return ControllerState.NO_TARGET;
            }

            // Log what we found
            BlockState targetState = level.getBlockState(currentTarget);
            String targetBlock = BuiltInRegistries.BLOCK.getKey(targetState.getBlock()).toString();
            System.out.println("[BlockAI] GatheringController: Selected target = " + targetBlock + " at " + currentTarget);

            // Check if already in range
            InteractionValidator.ValidationResult validation = InteractionValidator.validate(player, currentTarget);
            if (validation == InteractionValidator.ValidationResult.VALID) {
                miningAction.startMining(player, currentTarget);
                return ControllerState.RUNNING;
            }

            // Find interaction position and path to it
            BlockPos interactPos = InteractionValidator.findInteractionPosition(level, player, currentTarget);
            if (interactPos == null) {
                interactPos = currentTarget;
            }

            var path = com.blockai.ai.pathing.AStarPathfinder.findPath(level, player.blockPosition(), interactPos);
            if (path != null) {
                player.movementController.setPath(path);
            } else {
                System.out.println("[BlockAI] GatheringController: Path blocked. Registering failure.");
                TargetFailureMemory.recordFailure(currentTarget, "NO_PATH");
                currentTarget = null;
                tickDelay = 5; // Immediately try next target next tick
                
                consecutiveFailures++;
                if (consecutiveFailures >= 3) {
                    System.out.println("[BlockAI] Movement system failed for 3 independent reachable targets.");
                    stop();
                    return ControllerState.FAILED;
                }
                return ControllerState.RUNNING;
            }
            return ControllerState.RUNNING;
        }

        // We have a target and path
        com.blockai.ai.pathing.MovementController.MovementState movState = player.movementController.getState();
        
        if (movState == com.blockai.ai.pathing.MovementController.MovementState.PATH_FAILED) {
            TargetFailureMemory.recordFailure(currentTarget, "PATH_BLOCKED");
            player.movementController.stop();
            currentTarget = null;
            tickDelay = 5;
            
            consecutiveFailures++;
            if (consecutiveFailures >= 3) {
                System.out.println("[BlockAI] Movement system failed for 3 independent reachable targets.");
                stop();
                return ControllerState.FAILED;
            }
            return ControllerState.RUNNING;
        }

        // Check if we've reached it (path finished)
        if (movState == com.blockai.ai.pathing.MovementController.MovementState.ARRIVED || movState == com.blockai.ai.pathing.MovementController.MovementState.IDLE) {
            InteractionValidator.ValidationResult validation = InteractionValidator.validate(player, currentTarget);

            if (validation == InteractionValidator.ValidationResult.VALID) {
                System.out.println("[BlockAI] GatheringController: In range, mining at " + currentTarget);
                consecutiveFailures = 0; // Reset failures on successful arrival
                miningAction.startMining(player, currentTarget);
                player.movementController.stop();
                return ControllerState.RUNNING;
            } else {
                System.out.println("[BlockAI] GatheringController: Target rejected upon arrival — " + validation + " at " + currentTarget);
                TargetFailureMemory.recordFailure(currentTarget, "ARRIVAL_REJECTED");
                player.movementController.stop();
                currentTarget = null;
                tickDelay = 10;
                
                consecutiveFailures++;
                if (consecutiveFailures >= 3) {
                    System.out.println("[BlockAI] Movement system failed for 3 independent reachable targets.");
                    stop();
                    return ControllerState.FAILED;
                }
                return ControllerState.RUNNING;
            }
        }

        return ControllerState.RUNNING;
    }

    private BlockPos findBestCandidate(AIPlayer player) {
        List<String> acceptedBlocks = requirement.getAcceptedBlocks();
        Set<BlockPos> allCandidates = new HashSet<>();

        // Gather all known blocks matching any accepted type
        for (String blockId : acceptedBlocks) {
            Set<BlockPos> known = WorldObserver.MEMORY.getKnownBlocks(blockId);
            allCandidates.addAll(known);
        }

        if (allCandidates.isEmpty()) {
            // Force a scan
            System.out.println("[BlockAI] GatheringController: Scanning for " + requirement.getCategory().name() +
                    " (accepted: " + acceptedBlocks.size() + " block types)");
            WorldObserver.scanLocalArea(player, searchRadius);

            for (String blockId : acceptedBlocks) {
                Set<BlockPos> known = WorldObserver.MEMORY.getKnownBlocks(blockId);
                allCandidates.addAll(known);
            }

            if (allCandidates.isEmpty()) {
                System.out.println("[BlockAI] GatheringController: No " + requirement.getCategory().name() +
                        " found after scan (radius=" + searchRadius + ").");
                return null;
            }
        }

        System.out.println("[BlockAI] GatheringController: Found " + allCandidates.size() +
                " candidate(s) for " + requirement.getCategory().name());

        // Score and select best candidate, PRE-VALIDATING REACHABILITY HEURISTIC
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        ServerLevel level = (ServerLevel) player.level();

        for (BlockPos pos : allCandidates) {
            if (TargetFailureMemory.isBlacklisted(pos)) continue;

            // Heuristic Score: lower is better
            double dist = pos.distManhattan(player.blockPosition());
            
            TargetFailureMemory.FailureRecord rec = TargetFailureMemory.getRecord(pos);
            int failurePenalty = (rec != null) ? (rec.attempts * 50) : 0;
            
            double score = dist + failurePenalty;

            if (score < bestScore) {
                bestScore = score;
                best = pos;
            }
        }
        
        if (best == null) {
            System.out.println("[BlockAI] GatheringController: All candidates were unreachable or blacklisted.");
        }

        return best;
    }
}
