package com.blockai.mixin;

import com.blockai.ai.AIPlayer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTickTraceMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickEnter(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        
        // Identify if this is the AIPlayer by checking its class hierarchy
        boolean isAI = player.getClass().getName().contains("AIPlayer");
        
        if (isAI && player.tickCount % 20 == 0) {
            System.out.println("[BlockAI][AI_SERVERPLAYER_TICK]");
            System.out.println("class=" + player.getClass().getName());
            System.out.println("superclass=" + player.getClass().getSuperclass().getName());
            System.out.println("uuid=" + player.getUUID());
            System.out.println("name=" + player.getGameProfile());
            System.out.println("level=" + player.level());
            System.out.println("alive=" + player.isAlive());
            System.out.println("removed=" + player.isRemoved());
            System.out.println("spectator=" + player.isSpectator());
            System.out.println("noPhysics=" + player.noPhysics);
            System.out.println("position=" + player.position());
        }
    }
}
