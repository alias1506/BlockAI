package com.blockai.mixin;

import com.blockai.client.hud.AIHudData;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Avatar;
import net.minecraft.client.entity.ClientAvatarEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that overrides the nameplate of the BlockAI player
 * to display a live status HUD with HP, Hunger, STR, Armor.
 */
@Mixin(AvatarRenderer.class)
public abstract class AINameplateMixin<T extends Avatar & ClientAvatarEntity> {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void blockai_overrideNameplate(T avatar, AvatarRenderState renderState, float partialTick, CallbackInfo ci) {
        
        if (!"BlockAI".equals(avatar.getName().getString())) return;

        // Read actual live values from synced AIHudData
        float health = AIHudData.health;
        float maxHealth = AIHudData.maxHealth;
        int hunger = AIHudData.hunger;
        int armorValue = AIHudData.armor;
        double attackDamage = AIHudData.attackDamage;

        // Build the nameplate (Line 1)
        MutableComponent line1 = Component.literal("BlockAI").withStyle(Style.EMPTY.withColor(0x55FFFF).withBold(true));
        
        // Build the stats (Line 2)
        MutableComponent hpPart = Component.literal("HP " + String.format("%.0f", health) + "/" + String.format("%.0f", maxHealth))
                .withStyle(Style.EMPTY.withColor(health < maxHealth * 0.3f ? 0xFF5555 : 0x55FF55));
        MutableComponent hungerPart = Component.literal("  Food " + hunger)
                .withStyle(Style.EMPTY.withColor(hunger <= 6 ? 0xFF5555 : 0xFFAA00));
        MutableComponent strPart = Component.literal("  STR " + String.format("%.1f", attackDamage))
                .withStyle(Style.EMPTY.withColor(0xFFFF55));
        MutableComponent armorPart = Component.literal("  Armor " + armorValue)
                .withStyle(Style.EMPTY.withColor(0xAAAAAA));

        MutableComponent line2 = Component.empty().append(hpPart).append(hungerPart).append(strPart).append(armorPart);

        // Vanilla renderer displays scoreText directly below nameTag
        renderState.nameTag = line1;
        renderState.scoreText = line2;
    }
}
