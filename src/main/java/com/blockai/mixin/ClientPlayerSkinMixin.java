package com.blockai.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class ClientPlayerSkinMixin {
    
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void blockai_getSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer)(Object)this;
        if ("BlockAI".equals(player.getName().getString())) {
            
            ClientAsset.Texture customTexture = new ClientAsset.Texture() {
                @Override
                public Identifier id() { return Identifier.parse("blockai:textures/entity/blockai.png"); }
                @Override
                public Identifier texturePath() { return Identifier.parse("blockai:textures/entity/blockai.png"); }
            };
            
            // Provide our local texture for the AI player
            PlayerSkin skin = new PlayerSkin(
                customTexture, 
                null, 
                null, 
                PlayerModelType.WIDE, 
                true
            );
            cir.setReturnValue(skin);
        }
    }
}
