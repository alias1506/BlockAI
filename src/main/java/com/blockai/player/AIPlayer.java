package com.blockai.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public class AIPlayer extends ServerPlayer {

    public final com.blockai.ai.pathing.MovementController movementController = new com.blockai.ai.pathing.MovementController();
    public final com.blockai.ai.GatheringController gatheringController = new com.blockai.ai.GatheringController();
    public final com.blockai.ai.ClearAreaController clearAreaController = new com.blockai.ai.ClearAreaController();
    public final com.blockai.ai.CraftingController craftingController = new com.blockai.ai.CraftingController();
    public final com.blockai.ai.BuildingExecutor buildingExecutor = new com.blockai.ai.BuildingExecutor();
    public final com.blockai.ai.RecoveryController recoveryController = new com.blockai.ai.RecoveryController();
    public final com.blockai.ai.ExcavationController excavationController = new com.blockai.ai.ExcavationController();
    public final com.blockai.ai.AgentController agentController = new com.blockai.ai.AgentController();
    public final com.blockai.ai.AgentBrain agentBrain = new com.blockai.ai.AgentBrain();
    
    private com.blockai.ai.AgentState agentState = com.blockai.ai.AgentState.STOPPED;
    
    public com.blockai.ai.AgentState getAgentState() {
        return agentState;
    }
    
    public void setAgentState(com.blockai.ai.AgentState state) {
        this.agentState = state;
    }
    
    public boolean canExecuteAction() {
        return isAlive() && agentState == com.blockai.ai.AgentState.RUNNING;
    }

    public AIPlayer(MinecraftServer server, ServerLevel world, GameProfile profile, ClientInformation clientInfo) {
        super(server, world, profile, clientInfo);

        // Dummy connection and listener to prevent crashes when the server tries to send packets
        net.minecraft.network.Connection dummyConnection = new net.minecraft.network.Connection(PacketFlow.SERVERBOUND) {
            @Override
            public boolean isConnected() {
                return false;
            }
        };
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, clientInfo, false);
        this.connection = new ServerGamePacketListenerImpl(server, dummyConnection, this, cookie) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {
                // Ignore all outgoing packets
            }
        };
    }

    private int syncTickCounter = 0;

    @Override
    public void tick() {
        super.tick();
        this.agentBrain.tick(this);
        com.blockai.ai.WorldObserver.tick(this);
        this.movementController.tick(this);
        this.gatheringController.tick(this);
        this.clearAreaController.tick(this);
        this.excavationController.tick(this);
        this.craftingController.tick(this);
        this.buildingExecutor.tick(this);
        this.recoveryController.tick(this);
        this.agentController.tick(this);
        
        syncTickCounter++;
        if (syncTickCounter % 20 == 0) {
            syncHudData();
        }
    }
    
    private void syncHudData() {
        float health = this.getHealth();
        float maxHealth = this.getMaxHealth();
        int hunger = this.getFoodData().getFoodLevel();
        int armor = this.getArmorValue();
        double attackDamage = 1.0;
        try {
            attackDamage = this.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        } catch (Exception ignored) {}
        
        com.blockai.network.BlockAINetworking.HudSyncPayload payload = new com.blockai.network.BlockAINetworking.HudSyncPayload(
                health, maxHealth, hunger, armor, attackDamage
        );
        
        // Broadcast to all players on the server
        for (ServerPlayer player : ((ServerLevel)this.level()).getServer().getPlayerList().getPlayers()) {
            if (!(player instanceof AIPlayer)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
            }
        }
    }
    
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource damageSource) {
        System.out.println("[BlockAI] AIPlayer died at " + this.blockPosition());
        this.setAgentState(com.blockai.ai.AgentState.DYING);
        
        com.blockai.ai.survival.DeathHandler.handleDeath(this, damageSource);
        
        this.agentBrain.clearMemory();
        super.die(damageSource);
    }
}
