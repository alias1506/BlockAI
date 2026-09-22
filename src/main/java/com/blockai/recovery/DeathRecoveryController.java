package com.blockai.recovery;

import com.blockai.ai.AIPlayer;
import com.blockai.ai.AgentState;
import net.minecraft.core.BlockPos;
import com.blockai.ai.memory.DeathMemory;
import com.blockai.client.screen.BlockAIChatScreen;
import com.blockai.movement.PathFinder;

public class DeathRecoveryController {
    private com.blockai.ai.memory.DeathMemory deathRecord;
    private RecoveryPhase phase = RecoveryPhase.IDLE;

    private enum RecoveryPhase {
        IDLE, TRAVELING, SWEEPING, RESUMING
    }

    public void startRecovery(com.blockai.ai.memory.DeathMemory record) {
        this.deathRecord = record;
        this.phase = RecoveryPhase.TRAVELING;
        System.out.println("[BlockAI] DeathRecoveryController: Initiating recovery to " + record.position);
    }

    public boolean isIdle() {
        return phase == RecoveryPhase.IDLE;
    }

    public void tick(AIPlayer player) {
        if (isIdle()) return;

        switch (phase) {
            case TRAVELING:
                if (!player.movementController.hasPath()) {
                    double dist = deathRecord.position.distManhattan(player.blockPosition());
                    if (dist <= 4) {
                        System.out.println("[BlockAI] DeathRecoveryController: Reached death location! Sweeping for items.");
                        com.blockai.client.screen.BlockAIChatScreen.addMessage("Arrived at my death location. Looking for my dropped items...");
                        phase = RecoveryPhase.SWEEPING;
                    } else {
                        var path = com.blockai.movement.PathFinder.findPath((net.minecraft.server.level.ServerLevel) player.level(), player.blockPosition(), deathRecord.position);
                        if (path != null) {
                            player.movementController.setPath(path);
                        } else {
                            System.out.println("[BlockAI] DeathRecoveryController: Cannot path to death location. Abandoning drops.");
                            com.blockai.client.screen.BlockAIChatScreen.addMessage("I can't find a path back to my items. They are lost.");
                            phase = RecoveryPhase.RESUMING;
                        }
                    }
                }
                break;
            case SWEEPING:
                // Scan for ItemEntities nearby and walk to them
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
                net.minecraft.world.phys.AABB searchBox = player.getBoundingBox().inflate(10.0);
                java.util.List<net.minecraft.world.entity.item.ItemEntity> items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, searchBox);

                if (items.isEmpty()) {
                    com.blockai.client.screen.BlockAIChatScreen.addMessage("No more dropped items found here.");
                    phase = RecoveryPhase.RESUMING;
                    break;
                }

                // Path to the closest item
                net.minecraft.world.entity.item.ItemEntity targetItem = items.get(0);
                if (!player.movementController.hasPath()) {
                    var path = com.blockai.movement.PathFinder.findPath(level, player.blockPosition(), targetItem.blockPosition());
                    if (path != null) {
                        player.movementController.setPath(path);
                    } else {
                        // Skip this item if unreachable (e.g. dropped in a closed hole)
                        targetItem.discard();
                    }
                }
                break;
            case RESUMING:
                System.out.println("[BlockAI] DeathRecoveryController: Resuming roadmap.");
                player.setAgentState(AgentState.RUNNING);
                player.agentController.resume(player);
                com.blockai.client.screen.BlockAIChatScreen.addMessage("Resuming my previous task.");
                phase = RecoveryPhase.IDLE;
                this.deathRecord = null;
                break;
        }
    }
}
