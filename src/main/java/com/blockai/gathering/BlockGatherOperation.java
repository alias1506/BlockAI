package com.blockai.gathering;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class BlockGatherOperation {
    public enum OperationState {
        MINING,
        BLOCK_BREAK_CONFIRMED,
        WAITING_FOR_DROP,
        MOVING_TO_DROP,
        WAITING_FOR_VANILLA_PICKUP,
        VERIFYING_INVENTORY,
        PICKUP_FAILED,
        COMPLETE,
        FAILED
    }

    public final String operationId;
    public final BlockPos blockPosition;
    public final BlockState originalBlockState;
    public final List<ItemStack> expectedDrops;
    
    public Map<String, Integer> preBreakInventory = new HashMap<>();
    public Set<Integer> preBreakItemEntityIds = new HashSet<>();
    public Queue<ItemEntity> discoveredDrops = new LinkedList<>();
    public ItemEntity currentDrop = null;
    
    public OperationState state = OperationState.MINING;
    public long startTick;
    public long miningCompletedTick;
    public long pickupStartTick;
    public int retryCount = 0;

    private static int idCounter = 1;

    public BlockGatherOperation(BlockPos blockPosition, BlockState originalBlockState, List<ItemStack> expectedDrops, long startTick) {
        this.operationId = "BO_" + String.format("%03d", idCounter++);
        this.blockPosition = blockPosition;
        this.originalBlockState = originalBlockState;
        this.expectedDrops = new ArrayList<>(expectedDrops);
        this.startTick = startTick;
    }
}
