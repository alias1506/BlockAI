package com.blockai.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.blockai.player.AIPlayer;

public class WorldObserver {

    private static final int DEFAULT_SCAN_RADIUS = 16;
    private static int tickCounter = 0;

    public static final LocalWorldMemory MEMORY = new LocalWorldMemory();

    public static void tick(AIPlayer player) {
        tickCounter++;
        // Scan once every 20 ticks (1 second)
        if (tickCounter % 20 != 0) return;

        scanLocalArea(player);
    }

    public static void scanLocalArea(AIPlayer player) {
        scanLocalArea(player, DEFAULT_SCAN_RADIUS);
    }

    /**
     * Scan the area around the AI player with a given radius.
     * Records ALL potentially useful blocks, not just a hardcoded few.
     */
    public static void scanLocalArea(AIPlayer player, int radius) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = player.blockPosition();

        BlockPos.betweenClosedStream(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius)
        ).forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return;

            Block block = state.getBlock();
            String name = BuiltInRegistries.BLOCK.getKey(block).toString();

            // Record any block that could be a resource target
            if (isTrackableBlock(name)) {
                MEMORY.addBlock(name, pos.immutable());
            }
        });
    }

    /**
     * Determines if a block should be tracked in world memory.
     * Broadly includes all gatherable/interactable resource blocks.
     */
    private static boolean isTrackableBlock(String name) {
        // Logs (all wood types)
        if (name.contains("log")) return true;
        if (name.contains("wood")) return true;

        // Ores
        if (name.contains("ore")) return true;

        // Stone types
        if (name.contains("stone") && !name.contains("redstone")) return true;
        if (name.contains("cobblestone")) return true;
        if (name.contains("andesite")) return true;
        if (name.contains("diorite")) return true;
        if (name.contains("granite")) return true;
        if (name.contains("deepslate")) return true;

        // Dirt/ground
        if (name.contains("dirt")) return true;
        if (name.contains("sand")) return true;
        if (name.contains("gravel")) return true;
        if (name.contains("clay")) return true;

        // Vegetation
        if (name.contains("leaves")) return true;
        if (name.contains("grass") && !name.equals("minecraft:grass_block")) return true;
        if (name.contains("flower") || name.contains("tulip") || name.contains("daisy")
                || name.contains("poppy") || name.contains("dandelion") || name.contains("orchid")
                || name.contains("allium") || name.contains("bluet") || name.contains("cornflower")
                || name.contains("lily")) return true;
        if (name.contains("fern")) return true;
        if (name.contains("bush")) return true;
        if (name.contains("bamboo")) return true;
        if (name.contains("cactus")) return true;
        if (name.contains("vine")) return true;
        if (name.contains("mushroom")) return true;
        if (name.contains("sugar_cane")) return true;

        // Crops / food
        if (name.contains("wheat")) return true;
        if (name.contains("carrot")) return true;
        if (name.contains("potato")) return true;
        if (name.contains("beetroot")) return true;
        if (name.contains("melon")) return true;
        if (name.contains("pumpkin")) return true;

        // Planks
        if (name.contains("planks")) return true;

        // Cobweb
        if (name.equals("minecraft:cobweb")) return true;

        return false;
    }
}
