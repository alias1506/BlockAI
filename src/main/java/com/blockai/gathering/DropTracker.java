package com.blockai.gathering;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

public class DropTracker {
    public final BlockPos sourceBlock;
    public final Item expectedItem;
    public final long createdAtTick;
    public int entityId = -1;

    public DropTracker(BlockPos sourceBlock, Item expectedItem, long createdAtTick) {
        this.sourceBlock = sourceBlock;
        this.expectedItem = expectedItem;
        this.createdAtTick = createdAtTick;
    }
}
