package com.blockai.ai;

import com.blockai.player.AIPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Dedicated controller for surface clearing tasks.
 * Never digs below the determined floor level.
 * Only targets surface obstacles, not ground blocks.
 */
public class ClearAreaController {

    // Ground blocks that must NEVER be removed during normal clearing
    private static final Set<String> GROUND_BLOCKS = Set.of(
            "minecraft:dirt", "minecraft:grass_block", "minecraft:stone",
            "minecraft:deepslate", "minecraft:bedrock", "minecraft:sand",
            "minecraft:gravel", "minecraft:sandstone", "minecraft:red_sand",
            "minecraft:red_sandstone", "minecraft:terracotta",
            "minecraft:clay", "minecraft:mud", "minecraft:podzol",
            "minecraft:mycelium", "minecraft:coarse_dirt",
            "minecraft:rooted_dirt", "minecraft:moss_block",
            "minecraft:packed_mud"
    );

    // Surface obstacles that should be removed
    private static final Set<String> OBSTACLE_BLOCKS = Set.of(
            "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
            "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
            "minecraft:cherry_log", "minecraft:mangrove_log",
            "minecraft:oak_leaves", "minecraft:spruce_leaves", "minecraft:birch_leaves",
            "minecraft:jungle_leaves", "minecraft:acacia_leaves", "minecraft:dark_oak_leaves",
            "minecraft:cherry_leaves", "minecraft:mangrove_leaves", "minecraft:azalea_leaves",
            "minecraft:flowering_azalea_leaves",
            "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern",
            "minecraft:large_fern", "minecraft:dead_bush",
            "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
            "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
            "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip",
            "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
            "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony",
            "minecraft:sweet_berry_bush", "minecraft:bamboo",
            "minecraft:sugar_cane", "minecraft:cactus",
            "minecraft:vine", "minecraft:glow_lichen",
            "minecraft:mushroom_stem", "minecraft:brown_mushroom_block",
            "minecraft:red_mushroom_block", "minecraft:brown_mushroom", "minecraft:red_mushroom",
            "minecraft:cobweb"
    );

    private boolean active = false;
    private BlockPos center = null;
    private int radius = 8;
    private int floorY = 64;
    private int maxClearHeight = 3;
    private boolean allowsExcavation = false;

    private final List<BlockPos> pendingTargets = new ArrayList<>();
    private final Set<BlockPos> completedTargets = new HashSet<>();
    private final Set<BlockPos> failedTargets = new HashSet<>();
    private BlockPos currentTarget = null;

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
        pendingTargets.clear();
        completedTargets.clear();
        failedTargets.clear();
        miningAction.cancel();
        needsScan = true;
    }

    /**
     * Start a clearing operation centered on the AI's current position.
     */
    public void start(AIPlayer player, int radius, boolean allowsExcavation) {
        this.center = player.blockPosition();
        this.radius = radius;
        this.allowsExcavation = allowsExcavation;
        this.active = true;
        this.needsScan = true;
        this.currentTarget = null;
        this.pendingTargets.clear();
        this.completedTargets.clear();
        this.failedTargets.clear();
        this.miningAction.cancel();

        // Determine floor level: average highest solid ground in region
        this.floorY = determineSurfaceLevel((ServerLevel) player.level());

        System.out.println("[BlockAI] ClearAreaController: Started clearing.");
        System.out.println("[BlockAI]   Center: " + center);
        System.out.println("[BlockAI]   Radius: " + radius);
        System.out.println("[BlockAI]   Floor Y: " + floorY);
        System.out.println("[BlockAI]   Max clear height: " + maxClearHeight);
        System.out.println("[BlockAI]   Allows excavation: " + allowsExcavation);
    }

    /**
     * Determine the surface level by sampling X/Z columns in the region.
     */
    private int determineSurfaceLevel(ServerLevel level) {
        int totalY = 0;
        int samples = 0;

        for (int x = center.getX() - radius; x <= center.getX() + radius; x += 2) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += 2) {
                // Find highest solid ground from top down
                for (int y = center.getY() + 10; y >= center.getY() - 5; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    BlockState above = level.getBlockState(pos.above());

                    if (!state.getCollisionShape(level, pos).isEmpty()
                            && above.getCollisionShape(level, pos.above()).isEmpty()) {
                        totalY += y;
                        samples++;
                        break;
                    }
                }
            }
        }

        return samples > 0 ? totalY / samples : center.getY();
    }

    /**
     * Scan the clearing region for valid obstacle targets.
     */
    private void scanForObstacles(ServerLevel level) {
        pendingTargets.clear();

        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        int minY = floorY + 1; // Never touch the floor itself
        int maxY = floorY + maxClearHeight;

        // If excavation is allowed, extend downward
        if (allowsExcavation) {
            minY = floorY - 5;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (completedTargets.contains(pos) || failedTargets.contains(pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

                    // For normal clearing, only target obstacles
                    if (!allowsExcavation) {
                        if (GROUND_BLOCKS.contains(blockName)) {
                            continue; // Protect ground
                        }
                        // Accept known obstacles, or any non-ground block above floor
                        if (!OBSTACLE_BLOCKS.contains(blockName) && y <= floorY) {
                            continue; // Below floor and not a known obstacle
                        }
                    }

                    // Block hardness must be >= 0 (breakable)
                    float hardness = state.getDestroySpeed(level, pos);
                    if (hardness < 0) continue;

                    pendingTargets.add(pos);
                }
            }
        }

        // Sort by distance from AI center (closest first)
        pendingTargets.sort(Comparator.comparingDouble(p -> p.distSqr(center)));

        needsScan = false;
        System.out.println("[BlockAI] ClearAreaController: Scanned region. Found " + pendingTargets.size() + " obstacle(s).");
    }

    public ControllerState tick(AIPlayer player) {
        if (!active) return ControllerState.IDLE;

        if (tickDelay > 0) {
            tickDelay--;
            return ControllerState.WAITING;
        }

        ServerLevel level = (ServerLevel) player.level();

        // First scan
        if (needsScan) {
            scanForObstacles(level);
        }

        // If currently mining or collecting drops, continue
        MiningAction.MiningState actionState = miningAction.getState();
        if (actionState != MiningAction.MiningState.IDLE) {
            MiningAction.MiningState result = miningAction.tick(player);
            if (result == MiningAction.MiningState.SUCCESS) {
                System.out.println("[BlockAI] ClearAreaController: Obstacle removed at " + currentTarget);
                completedTargets.add(currentTarget);
                WorldObserver.MEMORY.removeBlockByPos(currentTarget);
                currentTarget = null;
                miningAction.reset();
                tickDelay = 5; // Brief pause before next target
            } else if (result == MiningAction.MiningState.FAILED) {
                System.out.println("[BlockAI] ClearAreaController: Failed to mine at " + currentTarget);
                failedTargets.add(currentTarget);
                currentTarget = null;
                miningAction.reset();
                tickDelay = 10;
            }
            return ControllerState.RUNNING;
        }

        // If we have a current target and are walking to it
        if (currentTarget != null && player.movementController.hasPath()) {
            return ControllerState.RUNNING;
        }

        // If we reached the target's vicinity, start mining
        if (currentTarget != null && !player.movementController.hasPath()) {
            InteractionValidator.ValidationResult validation = InteractionValidator.validate(player, currentTarget);
            if (validation == InteractionValidator.ValidationResult.VALID) {
                miningAction.startMining(player, currentTarget);
                return ControllerState.RUNNING;
            } else if (validation == InteractionValidator.ValidationResult.TOO_FAR) {
                // Need to get closer — find interaction position
                BlockPos interactPos = InteractionValidator.findInteractionPosition(level, player, currentTarget);
                if (interactPos != null) {
                    var path = com.blockai.ai.pathing.AStarPathfinder.findPath(level, player.blockPosition(), interactPos);
                    if (path != null) {
                        player.movementController.setPath(path);
                        return ControllerState.RUNNING;
                    }
                }
                System.out.println("[BlockAI] ClearAreaController: Target rejected — unreachable interaction position at " + currentTarget);
                failedTargets.add(currentTarget);
                currentTarget = null;
                tickDelay = 5;
                return ControllerState.RUNNING;
            } else {
                System.out.println("[BlockAI] ClearAreaController: Target rejected — " + validation + " at " + currentTarget);
                failedTargets.add(currentTarget);
                currentTarget = null;
                tickDelay = 5;
                return ControllerState.RUNNING;
            }
        }

        // Find next target
        if (pendingTargets.isEmpty()) {
            // Re-scan to check if anything new appeared or if we're done
            scanForObstacles(level);
            if (pendingTargets.isEmpty()) {
                System.out.println("[BlockAI] ClearAreaController: Area clearing COMPLETE. " +
                        completedTargets.size() + " obstacles removed, " +
                        failedTargets.size() + " failed.");
                active = false;
                return ControllerState.SUCCESS;
            }
        }

        // Pick next target
        currentTarget = pendingTargets.remove(0);
        BlockState targetState = level.getBlockState(currentTarget);
        if (targetState.isAir()) {
            completedTargets.add(currentTarget);
            currentTarget = null;
            return ControllerState.RUNNING;
        }

        System.out.println("[BlockAI] ClearAreaController: Next target — " +
                BuiltInRegistries.BLOCK.getKey(targetState.getBlock()) +
                " at " + currentTarget +
                " (floor Y=" + floorY + ", target Y=" + currentTarget.getY() + ")");

        // Validate target Y is not below floor (safety check)
        if (!allowsExcavation && currentTarget.getY() <= floorY) {
            String blockName = BuiltInRegistries.BLOCK.getKey(targetState.getBlock()).toString();
            if (GROUND_BLOCKS.contains(blockName)) {
                System.out.println("[BlockAI] ClearAreaController: Target rejected — below clearing floor (ground block)");
                failedTargets.add(currentTarget);
                currentTarget = null;
                return ControllerState.RUNNING;
            }
        }

        // Check if we can reach it already
        InteractionValidator.ValidationResult validation = InteractionValidator.validate(player, currentTarget);
        if (validation == InteractionValidator.ValidationResult.VALID) {
            miningAction.startMining(player, currentTarget);
            return ControllerState.RUNNING;
        }

        // Path to an interaction position
        BlockPos interactPos = InteractionValidator.findInteractionPosition(level, player, currentTarget);
        if (interactPos != null) {
            var path = com.blockai.ai.pathing.AStarPathfinder.findPath(level, player.blockPosition(), interactPos);
            if (path != null) {
                player.movementController.setPath(path);
                return ControllerState.RUNNING;
            }
        }

        System.out.println("[BlockAI] ClearAreaController: Target rejected — no path to interaction position at " + currentTarget);
        failedTargets.add(currentTarget);
        currentTarget = null;
        tickDelay = 5;
        return ControllerState.RUNNING;
    }
}
