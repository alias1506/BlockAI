package com.blockai.mixin;

import com.blockai.ai.AIPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Player.class)
public abstract class PlayerPickupTraceMixin extends net.minecraft.world.entity.LivingEntity {

    protected PlayerPickupTraceMixin(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity> entityType, net.minecraft.world.level.Level level) {
        super(entityType, level);
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getHealth()F"))
    private void onCollisionCheck(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        boolean isAI = player.getClass().getName().contains("AIPlayer");
        boolean isHuman = player.getClass().getName().equals("net.minecraft.server.level.ServerPlayer");
        
        if ((isAI || isHuman) && player.tickCount % 20 == 0) {
            float health = this.getHealth();
            boolean spectator = this.isSpectator();
            
            // Only print if there's an ItemEntity nearby to avoid spam
            AABB pickupArea = this.getBoundingBox().inflate(1.0, 0.5, 1.0);
            List<Entity> items = this.level().getEntities((Entity)this, pickupArea, e -> e instanceof net.minecraft.world.entity.item.ItemEntity);
            
            if (!items.isEmpty()) {
                System.out.println("[BlockAI][VANILLA_PICKUP_TRACE] Player.aiStep() collision logic");
                System.out.println("player=" + (isAI ? "AIPlayer" : "Human") + " uuid=" + player.getUUID());
                System.out.println("health=" + health + " (needs >0)");
                System.out.println("spectator=" + spectator + " (needs false)");
                System.out.println("nearby_items=" + items.size());
                for (Entity e : items) {
                    System.out.println("  -> item_id=" + e.getId() + " removed=" + e.isRemoved());
                }
            }
        }
    }
}
