package com.blockai.ai;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public class AIPlayerController {

    private static boolean aiPlayerSpawned = false;
    private static UUID aiPlayerUUID = null;
    private static int retryDelayTicks = 0;
    
    // Store last known dimension to detect world rejoins
    private static String lastDimension = null;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick(server);
        });
    }

    public static ServerPlayer getAIPlayer(MinecraftServer server) {
        if (!aiPlayerSpawned || aiPlayerUUID == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            net.minecraft.world.entity.Entity e = level.getEntity(aiPlayerUUID);
            if (e instanceof ServerPlayer) {
                return (ServerPlayer) e;
            }
        }
        return null;
    }

    private static void tick(MinecraftServer server) {
        if (server.getPlayerList().getPlayerCount() == 0) {
            // Reset state if everyone left
            aiPlayerSpawned = false;
            aiPlayerUUID = null;
            lastDimension = null;
            return;
        }

        if (aiPlayerSpawned) {
            // Verify entity still exists
            if (aiPlayerUUID != null) {
                boolean found = false;
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.getEntity(aiPlayerUUID) != null) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    aiPlayerSpawned = false; // Need to respawn
                }
            }
            return;
        }

        if (retryDelayTicks > 0) {
            retryDelayTicks--;
            return;
        }

        forceSpawn(server);
    }
    
    public static void forceSpawn(MinecraftServer server) {
        if (aiPlayerSpawned) return;

        // Get the first human player (since this is mostly singleplayer/testing)
        ServerPlayer humanPlayer = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof AIPlayer)) {
                humanPlayer = player;
                break;
            }
        }

        if (humanPlayer == null) {
            return; // No human to spawn near
        }

        ServerLevel level = (ServerLevel) humanPlayer.level();
        String currentDimension = level.dimension().identifier().toString();

        if (lastDimension != null && !lastDimension.equals(currentDimension)) {
            // Dimension changed, we will handle this via normal survival pathfinding later,
            // but for now we spawn a fresh one if it's a completely new world session.
            // (If they just went through a portal, we don't respawn, we wait for teleport logic)
        }
        lastDimension = currentDimension;

        System.out.println("[BlockAI] Checking AI player spawn...");
        System.out.println("[BlockAI] Human player detected: " + humanPlayer.getName().getString());
        System.out.println("[BlockAI] Human dimension: " + currentDimension);
        System.out.println("[BlockAI] Human position: X=" + humanPlayer.getX() + " Y=" + humanPlayer.getY() + " Z=" + humanPlayer.getZ());
        System.out.println("[BlockAI] Searching for safe AI spawn position...");

        BlockPos safePos = findSafeSpawnPosition(level, humanPlayer);

        if (safePos == null) {
            System.out.println("[BlockAI] AI player spawn FAILED: Could not find safe nearby block.");
            humanPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("BlockAI companion could not spawn yet. Retrying..."));
            retryDelayTicks = 40; // Wait 2 seconds before retrying
            return;
        }

        System.out.println("[BlockAI] Safe spawn position found: X=" + safePos.getX() + " Y=" + safePos.getY() + " Z=" + safePos.getZ());
        System.out.println("[BlockAI] Creating AI player...");

        GameProfile profile = new GameProfile(UUID.randomUUID(), "BlockAI");
        AIPlayer aiPlayer = new AIPlayer(server, level, profile, ClientInformation.createDefault());
        aiPlayer.setPos(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
        aiPlayer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

        System.out.println("[BlockAI] Adding AI player to ServerWorld...");
        
        // Notify all clients that this player exists so they don't ignore the entity spawn packet
        server.getPlayerList().broadcastAll(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(
            java.util.List.of(aiPlayer)
        ));
        
        level.addNewPlayer(aiPlayer);

        aiPlayerSpawned = true;
        aiPlayerUUID = aiPlayer.getUUID();
        System.out.println("[BlockAI] AI player spawned successfully: " + aiPlayerUUID);
        humanPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("BlockAI companion spawned nearby."));
    }

    private static BlockPos findSafeSpawnPosition(ServerLevel level, ServerPlayer humanPlayer) {
        BlockPos playerPos = humanPlayer.blockPosition();
        
        // Search offsets: +3/-3 in X and Z
        int[][] offsets = {
            {3, 0}, {-3, 0}, {0, 3}, {0, -3},
            {4, 0}, {-4, 0}, {0, 4}, {0, -4},
            {3, 3}, {-3, -3}, {3, -3}, {-3, 3}
        };

        for (int[] offset : offsets) {
            int ox = offset[0];
            int oz = offset[1];

            // Check y levels from y-2 to y+2
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos pos = playerPos.offset(ox, dy, oz);
                
                if (isPositionSafe(level, pos)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static boolean isPositionSafe(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;

        BlockPos headPos = pos.above();
        BlockPos groundPos = pos.below();

        // Feet and head must be air/non-solid
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        if (!level.getBlockState(headPos).getCollisionShape(level, headPos).isEmpty()) return false;

        // Ground must be solid
        if (level.getBlockState(groundPos).getCollisionShape(level, groundPos).isEmpty()) return false;

        // Ensure not lava/water/fire
        net.minecraft.world.level.material.FluidState fluid = level.getFluidState(pos);
        if (!fluid.isEmpty()) return false;
        net.minecraft.world.level.material.FluidState groundFluid = level.getFluidState(groundPos);
        if (!groundFluid.isEmpty()) return false;

        // Ensure no entity collisions
        AABB aabb = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
        if (!level.noCollision(aabb)) return false;

        return true;
    }
}
