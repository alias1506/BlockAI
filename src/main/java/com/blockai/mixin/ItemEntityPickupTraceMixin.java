package com.blockai.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupTraceMixin extends Entity {

    public ItemEntityPickupTraceMixin(net.minecraft.world.entity.EntityType<?> entityType, net.minecraft.world.level.Level level) {
        super(entityType, level);
    }

    @org.spongepowered.asm.mixin.Shadow private int pickupDelay;
    @org.spongepowered.asm.mixin.Shadow private java.util.UUID target;

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void onPlayerTouchEnter(Player player, CallbackInfo ci) {
        boolean isAI = player.getClass().getName().contains("AIPlayer");
        boolean isHuman = player.getClass().getName().equals("net.minecraft.server.level.ServerPlayer");
        
        if (isAI || isHuman) {
            ItemEntity itemEntity = (ItemEntity) (Object) this;
            ItemStack itemStack = itemEntity.getItem();
            String playerName = isAI ? "AIPlayer" : "Human";
            
            System.out.println("[BlockAI][VANILLA_PICKUP_TRACE] ItemEntity.playerTouch() ENTER - " + playerName);
            System.out.println("  item=" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack.getItem()) + " x" + itemStack.getCount());
            
            boolean isClient = this.level().isClientSide();
            System.out.println("  condition_clientSide=" + isClient + " (needs false)");
            System.out.println("  condition_pickupDelay=" + this.pickupDelay + " (needs 0)");
            boolean targetMatch = (this.target == null || this.target.equals(player.getUUID()));
            System.out.println("  condition_target=" + this.target + " matches=" + targetMatch + " (needs true)");
            
            System.out.println("  inventory_class=" + player.getInventory().getClass().getName());
        }
    }
    
    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void onPlayerTouchReturn(Player player, CallbackInfo ci) {
        boolean isAI = player.getClass().getName().contains("AIPlayer");
        boolean isHuman = player.getClass().getName().equals("net.minecraft.server.level.ServerPlayer");
        
        if (isAI || isHuman) {
            System.out.println("[BlockAI][VANILLA_PICKUP_TRACE] ItemEntity.playerTouch() RETURN");
            System.out.println("  item_alive=" + this.isAlive());
        }
    }
}
