package com.blockai.network;

import com.blockai.ai.AIPlayerController;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import com.blockai.inventory.InventoryManager;
import com.blockai.client.hud.AIHudData;

public class BlockAINetworking {

    public static final Identifier OPEN_INV_ID = Identifier.parse("blockai:open_inventory");
    public static final Identifier HUD_SYNC_ID = Identifier.parse("blockai:hud_sync");
    
    // C2S Payload: Open Inventory
    public record OpenInvPayload() implements CustomPacketPayload {
        public static final Type<OpenInvPayload> TYPE = new Type<>(OPEN_INV_ID);
        public static final StreamCodec<FriendlyByteBuf, OpenInvPayload> CODEC = StreamCodec.unit(new OpenInvPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    
    // S2C Payload: HUD Sync
    public record HudSyncPayload(float health, float maxHealth, int hunger, int armor, double attackDamage) implements CustomPacketPayload {
        public static final Type<HudSyncPayload> TYPE = new Type<>(HUD_SYNC_ID);
        public static final StreamCodec<FriendlyByteBuf, HudSyncPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeFloat(payload.health);
                    buf.writeFloat(payload.maxHealth);
                    buf.writeInt(payload.hunger);
                    buf.writeInt(payload.armor);
                    buf.writeDouble(payload.attackDamage);
                },
                buf -> new HudSyncPayload(
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readDouble()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerServer() {
        // Register C2S (received by server)
        PayloadTypeRegistry.serverboundPlay().register(OpenInvPayload.TYPE, OpenInvPayload.CODEC);
        
        // Register S2C (sent by server)
        PayloadTypeRegistry.clientboundPlay().register(HudSyncPayload.TYPE, HudSyncPayload.CODEC);
        
        ServerPlayNetworking.registerGlobalReceiver(OpenInvPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer humanPlayer = context.player();
                System.out.println("[BlockAI] AI inventory request received from " + humanPlayer.getName().getString());
                
                ServerPlayer aiPlayer = AIPlayerController.getAIPlayer(context.server());
                
                if (aiPlayer != null) {
                    System.out.println("[BlockAI] AI entity found: " + aiPlayer.getUUID());
                    System.out.println("[BlockAI] AI inventory size: " + aiPlayer.getInventory().getContainerSize());
                    
                    // Log non-empty slots
                    for (int i = 0; i < aiPlayer.getInventory().getContainerSize(); i++) {
                        var stack = aiPlayer.getInventory().getItem(i);
                        if (!stack.isEmpty()) {
                            System.out.println("[BlockAI]   Slot " + i + ": " + stack.getItem() + " x" + stack.getCount());
                        }
                    }
                    
                    com.blockai.inventory.InventoryManager.openAIInventoryFor(humanPlayer, aiPlayer);
                    System.out.println("[BlockAI] Inventory opened for " + humanPlayer.getName().getString());
                } else {
                    System.out.println("[BlockAI] Cannot open inventory: AI player not found");
                    humanPlayer.sendSystemMessage(Component.literal("BlockAI companion is not currently spawned."));
                }
            });
        });
        
        System.out.println("[BlockAI] Networking registered on Server.");
    }
    
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(HudSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                com.blockai.client.hud.AIHudData.update(
                        payload.health(),
                        payload.maxHealth(),
                        payload.hunger(),
                        payload.armor(),
                        payload.attackDamage()
                );
            });
        });
        System.out.println("[BlockAI] Networking registered on Client.");
    }
}
