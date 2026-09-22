package com.blockai.gathering;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class TemporaryBlockRecord {
    public BlockPos position;
    public BlockState originalState;
    public ItemStack placedStack;
    public String taskId;
    public BlockPos targetPosition;
    public boolean confirmedPlaced;

    public TemporaryBlockRecord(BlockPos position, BlockState originalState, ItemStack placedStack, String taskId, BlockPos targetPosition) {
        this.position = position;
        this.originalState = originalState;
        this.placedStack = placedStack;
        this.taskId = taskId;
        this.targetPosition = targetPosition;
        this.confirmedPlaced = false;
    }
}
