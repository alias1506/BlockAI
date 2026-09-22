package com.blockai.ai;

import com.blockai.ai.AIPlayer;

import com.blockai.world.WorldScanner;
import com.blockai.gathering.ResourceResolver;
import com.blockai.execution.TaskResult;
import com.blockai.ai.planner.RoadmapTaskType;
import com.blockai.client.screen.BlockAIChatScreen;
import com.blockai.ai.knowledge.DependencyResolver;
import com.blockai.gathering.ResourceCategory;
import com.blockai.ai.memory.ResourceMemory;
import com.blockai.execution.TaskState;
import com.blockai.ai.planner.RoadmapTask;
import com.blockai.ai.planner.Roadmap;

public class AgentController {

    private Roadmap currentRoadmap = null;
    private net.minecraft.world.entity.player.Player followTarget = null;

    public void start(AIPlayer player, Roadmap roadmap) {
        this.currentRoadmap = roadmap;
        this.currentRoadmap.setState(TaskState.RUNNING);
        this.currentRoadmap.setCurrentTaskIndex(0);
        player.setAgentState(AgentState.RUNNING);
        System.out.println("[BlockAI] Roadmap execution started. Goal: " + roadmap.getGoal());
    }

    public void startFollow(AIPlayer player, net.minecraft.world.entity.player.Player target) {
        this.followTarget = target;
        player.setAgentState(AgentState.FOLLOW_PLAYER);
        stopAllControllers(player);
        System.out.println("[BlockAI] Started following player: " + target.getName().getString());
    }

    public void start(AIPlayer player) {
        AgentState currentState = player.getAgentState();
        if (currentState == AgentState.RECOVERING) {
            System.out.println("[BlockAI] Agent state: RECOVERING -> RUNNING (Continuing recovery)");
            // We just let it continue its recovery tick
            return;
        }
        
        if (currentState == AgentState.DYING || currentState == AgentState.DEAD) {
            System.out.println("[BlockAI] Cannot start agent while DEAD or DYING.");
            return;
        }

        if (this.currentRoadmap != null) {
            System.out.println("[BlockAI] Agent state: " + currentState + " -> RUNNING");
            this.currentRoadmap.setState(TaskState.RUNNING);
            player.setAgentState(AgentState.RUNNING);
        } else {
            System.out.println("[BlockAI] Agent state: " + currentState + " -> RUNNING (Idle, waiting for task)");
            player.setAgentState(AgentState.RUNNING);
        }
    }

    public void pause(AIPlayer player) {
        if (this.currentRoadmap != null && this.currentRoadmap.getState() == TaskState.RUNNING) {
            this.currentRoadmap.setState(TaskState.PAUSED);
        }
        player.setAgentState(AgentState.PAUSED);
        stopAllControllers(player);
        System.out.println("[BlockAI] Roadmap execution paused.");
    }

    public void resume(AIPlayer player) {
        if (this.currentRoadmap != null && (this.currentRoadmap.getState() == TaskState.PAUSED || this.currentRoadmap.getState() == TaskState.STOPPED)) {
            this.currentRoadmap.setState(TaskState.RUNNING);
        }
        player.setAgentState(AgentState.RUNNING);
        System.out.println("[BlockAI] Roadmap execution resumed.");
    }

    public void stop(AIPlayer player) {
        if (this.currentRoadmap != null) {
            this.currentRoadmap.setState(TaskState.STOPPED);
        }
        player.setAgentState(AgentState.STOPPED);
        stopAllControllers(player);
        System.out.println("[BlockAI] Roadmap execution stopped.");
    }

    public void stopAllControllers(AIPlayer player) {
        if (player != null) {
            player.movementController.stop();
            player.gatheringController.stop(player);
            player.clearAreaController.stop();
            player.excavationController.stop(player);
        }
    }

    public Roadmap getCurrentRoadmap() {
        return currentRoadmap;
    }

    public void tick(AIPlayer player) {
        if (!player.canExecuteAction()) {
            return;
        }
        
        if (player.getAgentState() == AgentState.FOLLOW_PLAYER) {
            if (followTarget != null && followTarget.isAlive() && followTarget.level() == player.level()) {
                double distSq = player.distanceToSqr(followTarget);
                if (distSq > 9.0) { // More than 3 blocks away
                    if (!player.movementController.hasPath() || player.tickCount % 20 == 0) {
                        player.movementController.setPath(java.util.List.of(followTarget.blockPosition()), "FOLLOW_" + followTarget.getUUID());
                    }
                } else {
                    player.movementController.stop();
                }
            } else {
                player.setAgentState(AgentState.STOPPED); // Target lost
            }
            return;
        }

        if (currentRoadmap == null) return;

        if (currentRoadmap.getState() == TaskState.STOPPED
                || currentRoadmap.getState() == TaskState.COMPLETED
                || currentRoadmap.getState() == TaskState.FAILED) {
            stopAllControllers(player);
            return;
        }

        if (currentRoadmap.getState() != TaskState.RUNNING) {
            return;
        }

        int index = currentRoadmap.getCurrentTaskIndex();
        if (index >= currentRoadmap.getTasks().size()) {
            currentRoadmap.setState(TaskState.COMPLETED);
            System.out.println("[BlockAI] Roadmap completed after all tasks were verified.");
            com.blockai.client.screen.BlockAIChatScreen.addMessage("Roadmap completed successfully.");
            stopAllControllers(player);

            // Record task success in learning memory
            ResourceMemory.getInstance().recordTaskOutcome(
                    currentRoadmap.getGoal(), true, "completed", "All tasks done"
            );
            return;
        }

        RoadmapTask currentTask = currentRoadmap.getTasks().get(index);

        if (currentTask.getStatus() == RoadmapTaskType.PENDING) {
            System.out.println("[BlockAI] Analyzing task: " + currentTask.getDescription());
            
            // Resolve dependencies dynamically using the local knowledge base
            java.util.List<RoadmapTask> expandedTasks = com.blockai.ai.knowledge.DependencyResolver.resolveDependencies(currentTask, player);
            
            if (expandedTasks.size() > 1) {
                System.out.println("[BlockAI] Task requires prerequisites. Expanding into " + expandedTasks.size() + " sub-tasks.");
                // Replace current task with the expanded plan
                currentRoadmap.getTasks().remove(index);
                currentRoadmap.getTasks().addAll(index, expandedTasks);
                
                // Re-fetch the first sub-task which is now at the current index
                currentTask = currentRoadmap.getTasks().get(index);
            }
            
            System.out.println("[BlockAI] Task " + (index + 1) + "/" + currentRoadmap.getTasks().size() + " started");
            System.out.println("[BlockAI] Task type: " + currentTask.getType());
            System.out.println("[BlockAI] Task description: " + currentTask.getDescription());
            currentTask.setStatus(RoadmapTaskType.IN_PROGRESS);
            taskTickCount = 0;
            com.blockai.client.screen.BlockAIChatScreen.addMessage("Starting task " + (index + 1) + ": " + currentTask.getDescription());
        }

        if (currentTask.getStatus() == RoadmapTaskType.IN_PROGRESS) {
            String type = currentTask.getType().toLowerCase();
            boolean isFinished = false;
            boolean isFailed = false;

            boolean hasResource = currentTask.getResourceCategory() != null || currentTask.getSpecificItem() != null || currentTask.getQuantity() > 0;
            boolean isGather = type.contains("mine") || type.contains("gather") || type.contains("collect") || type.contains("resource") || 
                               (type.contains("excavate") && hasResource && currentTask.getDescription().toLowerCase().contains("collect"));

            // CLEAR_AREA — dedicated controller
            if (type.contains("clear")) {
                if (!player.clearAreaController.isActive()) {
                    int radius = currentTask.getRadius();
                    boolean allowsExcavation = currentTask.isAllowsExcavation();
                    player.clearAreaController.start(player, radius, allowsExcavation);
                }

                TaskResult state = player.clearAreaController.tick(player);
                if (state == TaskResult.SUCCESS) {
                    isFinished = true;
                } else if (state == TaskResult.FAILED) {
                    taskTickCount++;
                    if (taskTickCount > 200) {
                        isFailed = true;
                    }
                }
            }
            // EXCAVATE — physical layer-by-layer digging
            else if (type.contains("excavate")) {
                if (!player.excavationController.isActive()) {
                    player.excavationController.start(player);
                }

                TaskResult state = player.excavationController.tick(player);
                if (state == TaskResult.SUCCESS) {
                    isFinished = true;
                } else if (state == TaskResult.FAILED) {
                    taskTickCount++;
                    if (taskTickCount > 200) {
                        isFailed = true;
                    }
                }
            }
            // GATHERING — category-based resource collection
            else if (isGather) {
                if (!player.gatheringController.isActive()) {
                    ResourceResolver req = buildResourceRequirement(currentTask);
                    System.out.println("[BlockAI] Resource requirement: " + req);
                    System.out.println("[BlockAI]   Accepted blocks: " + req.getAcceptedBlocks());
                    System.out.println("[BlockAI]   Already in inventory: " + req.countInInventory(player) + "/" + req.getQuantity());
                    player.gatheringController.start(player, req, String.valueOf(currentTask.getId()));
                }

                TaskResult state = player.gatheringController.tick(player);
                if (state == TaskResult.SUCCESS) {
                    isFinished = true;
                } else if (state == TaskResult.NO_TARGET || state == TaskResult.FAILED) {
                    taskTickCount++;
                    if (taskTickCount > 200) {
                        isFailed = true;
                    }
                }
            }
            // CRAFT / BUILD
            else if (type.contains("craft") || type.contains("make") || type.contains("build")) {
                if (player.craftingController.isIdle() && taskTickCount == 0) {
                    // Try to extract item name from the specificItem field or description
                    String target = currentTask.getSpecificItem();
                    if (target == null) target = "minecraft:air";
                    int qty = currentTask.getQuantity();
                    
                    player.craftingController.craft(target, qty);
                }
                
                player.craftingController.tick(player);
                
                if (player.craftingController.getState() == TaskResult.SUCCESS) {
                    isFinished = true;
                } else {
                    taskTickCount++;
                    if (taskTickCount > 100) {
                        isFinished = true; // Fallback timeout
                    }
                }
            }
            // OBSERVE / VERIFY / EXPLORE
            else if (type.contains("observe") || type.contains("verify") || type.contains("explore") || type.contains("scan")) {
                // Perform an actual scan
                if (taskTickCount == 0) {
                    System.out.println("[BlockAI] Observation task: Scanning environment...");
                    WorldScanner.scanLocalArea(player, 32);

                    // Log what was found
                    var memory = WorldScanner.MEMORY;
                    System.out.println("[BlockAI] Observation results:");
                    for (String blockType : memory.getAll().keySet()) {
                        int count = memory.getKnownBlocks(blockType).size();
                        if (count > 0) {
                            System.out.println("[BlockAI]   " + blockType + " x" + count);
                        }
                    }
                }
                taskTickCount++;
                if (taskTickCount > 45) {
                    isFinished = true;
                }
            }
            // UNKNOWN
            else {
                taskTickCount++;
                if (taskTickCount > 60) {
                    isFinished = true;
                }
            }

            if (isFinished) {
                System.out.println("[BlockAI] Task " + (index + 1) + "/" + currentRoadmap.getTasks().size() + " verified DONE");
                currentTask.setStatus(RoadmapTaskType.DONE);
                currentRoadmap.setCurrentTaskIndex(index + 1);
                taskTickCount = 0;
                player.gatheringController.stop(player);
                player.clearAreaController.stop();
                player.excavationController.stop(player);
            } else if (isFailed) {
                currentTask.setStatus(RoadmapTaskType.FAILED);
                currentRoadmap.setState(TaskState.FAILED);
                System.out.println("[BlockAI] Roadmap failed at task " + (index + 1));
                stopAllControllers(player);

                // Record failure
                ResourceMemory.getInstance().recordTaskOutcome(
                        currentRoadmap.getGoal(), false,
                        currentTask.getDescription(),
                        "Failed at task " + (index + 1)
                );
            }
        }
    }

    /**
     * Build a ResourceResolver from a RoadmapTask.
     * Priority: task metadata > description parsing > fallback
     */
    private ResourceResolver buildResourceRequirement(RoadmapTask task) {
        // 1. Try task metadata from Groq
        String catName = task.getResourceCategory();
        String specific = task.getSpecificItem();
        int qty = task.getQuantity();

        ResourceCategory category = null;

        if (catName != null && !catName.isEmpty()) {
            category = ResourceCategory.fromName(catName);
        }

        // 2. Fallback: parse from description
        if (category == null) {
            category = ResourceCategory.fromDescription(task.getDescription());
        }

        // 3. Ultimate fallback
        if (category == null) {
            category = ResourceCategory.WOOD_LOG;
            System.out.println("[BlockAI] WARNING: Could not determine resource category, defaulting to WOOD_LOG");
        }

        return new ResourceResolver(category, specific, qty);
    }

    private int taskTickCount = 0;
}
