package com.blockai.survival;

import com.blockai.ai.AIPlayer;
import net.minecraft.world.damagesource.DamageSource;

import com.blockai.ai.memory.DeathMemory;
import com.blockai.ai.planner.Roadmap;

public class SurvivalController {
    
    public static void handleDeath(AIPlayer player, DamageSource damageSource) {
        player.setAgentState(com.blockai.ai.AgentState.DEAD);
        
        // Stop current execution gracefully
        player.agentController.stopAllControllers(player);
        
        // Record death information
        DeathMemory record = new DeathMemory();
        record.dimension = player.level().dimension().toString();
        record.position = player.blockPosition();
        record.cause = damageSource.getMsgId();
        
        com.blockai.ai.planner.Roadmap currentMap = player.agentController.getCurrentRoadmap();
        if (currentMap != null) {
            record.goal = currentMap.getGoal();
            player.agentController.pause(player); // Pause roadmap so we can resume later
        }
        
        // Transition to recovery sequence by handing off to RespawnManager
        RespawnManager.scheduleRespawn(player, record);
    }
}
