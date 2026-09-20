package com.blockai.ai.survival;

import com.blockai.player.AIPlayer;

public class RespawnManager {
    
    public static void scheduleRespawn(AIPlayer player, DeathRecord record) {
        net.minecraft.server.MinecraftServer server = ((net.minecraft.server.level.ServerLevel)player.level()).getServer();
        server.execute(() -> {
            player.setAgentState(com.blockai.ai.AgentState.RESPAWNING);
            System.out.println("[BlockAI] Respawning AI Player...");
            
            // Native Minecraft server respawn logic
            com.blockai.player.AIPlayer newPlayer = (com.blockai.player.AIPlayer) server.getPlayerList().respawn(player, true, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
            
            // The PlayerList#respawn creates a NEW player entity object.
            // We need to transfer the agent state and controllers over.
            if (newPlayer != null) {
                newPlayer.setAgentState(com.blockai.ai.AgentState.RECOVERING);
                com.blockai.client.screen.BlockAIChatScreen.addMessage("I died while trying to " + record.goal + "...");
                com.blockai.client.screen.BlockAIChatScreen.addMessage("Respawning at my last respawn point...");
                
                // Initialize the recovery process on the newly respawned player instance
                newPlayer.recoveryController.startRecovery(record);
            }
        });
    }
}
