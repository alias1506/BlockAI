package com.blockai.ai.survival;

import com.blockai.player.AIPlayer;
import net.minecraft.world.damagesource.DamageSource;

public class DeathHandler {
    
    public static void handleDeath(AIPlayer player, DamageSource damageSource) {
        player.setAgentState(com.blockai.ai.AgentState.DEAD);
        
        // Stop current execution gracefully
        player.agentController.stopAllControllers(player);
        
        // Record death information
        DeathRecord record = new DeathRecord();
        record.dimension = player.level().dimension().toString();
        record.position = player.blockPosition();
        record.cause = damageSource.getMsgId();
        
        com.blockai.ai.Roadmap currentMap = player.agentController.getCurrentRoadmap();
        if (currentMap != null) {
            record.goal = currentMap.getGoal();
            player.agentController.pause(player); // Pause roadmap so we can resume later
        }
        
        // Transition to recovery sequence by handing off to RespawnManager
        RespawnManager.scheduleRespawn(player, record);
    }
}
