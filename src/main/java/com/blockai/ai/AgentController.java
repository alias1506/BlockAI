package com.blockai.ai;

import com.blockai.player.AIPlayer;

public class AgentController {

    private Roadmap currentRoadmap = null;

    public void start(AIPlayer player, Roadmap roadmap) {
        this.currentRoadmap = roadmap;
        this.currentRoadmap.setState(ExecutionState.RUNNING);
        this.currentRoadmap.setCurrentTaskIndex(0);
        player.setAgentState(AgentState.RUNNING);
        System.out.println("[BlockAI] Roadmap execution started. Goal: " + roadmap.getGoal());
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
            this.currentRoadmap.setState(ExecutionState.RUNNING);
            player.setAgentState(AgentState.RUNNING);
        } else {
            System.out.println("[BlockAI] Agent state: " + currentState + " -> RUNNING (Idle, waiting for task)");
            player.setAgentState(AgentState.RUNNING);
        }
    }

    public void pause(AIPlayer player) {
        if (this.currentRoadmap != null && this.currentRoadmap.getState() == ExecutionState.RUNNING) {
            this.currentRoadmap.setState(ExecutionState.PAUSED);
        }
        player.setAgentState(AgentState.PAUSED);
        stopAllControllers(player);
        System.out.println("[BlockAI] Roadmap execution paused.");
    }

    public void resume(AIPlayer player) {
        if (this.currentRoadmap != null && (this.currentRoadmap.getState() == ExecutionState.PAUSED || this.currentRoadmap.getState() == ExecutionState.STOPPED)) {
            this.currentRoadmap.setState(ExecutionState.RUNNING);
        }
        player.setAgentState(AgentState.RUNNING);
        System.out.println("[BlockAI] Roadmap execution resumed.");
    }

    public void stop(AIPlayer player) {
        if (this.currentRoadmap != null) {
            this.currentRoadmap.setState(ExecutionState.STOPPED);
        }
        player.setAgentState(AgentState.STOPPED);
        stopAllControllers(player);
        System.out.println("[BlockAI] Roadmap execution stopped.");
    }

    public void stopAllControllers(AIPlayer player) {
        if (player != null) {
            player.movementController.stop();
            player.gatheringController.stop();
            player.clearAreaController.stop();
            player.excavationController.stop();
        }
    }

    public Roadmap getCurrentRoadmap() {
        return currentRoadmap;
    }

    public void tick(AIPlayer player) {
        if (currentRoadmap == null) return;
        
        if (!player.canExecuteAction()) {
            return;
        }

        if (currentRoadmap.getState() == ExecutionState.STOPPED
                || currentRoadmap.getState() == ExecutionState.COMPLETED
                || currentRoadmap.getState() == ExecutionState.FAILED) {
            stopAllControllers(player);
            return;
        }

        if (currentRoadmap.getState() != ExecutionState.RUNNING) {
            return;
        }

        int index = currentRoadmap.getCurrentTaskIndex();
        if (index >= currentRoadmap.getTasks().size()) {
            currentRoadmap.setState(ExecutionState.COMPLETED);
            System.out.println("[BlockAI] Roadmap completed after all tasks were verified.");
            com.blockai.client.screen.BlockAIChatScreen.addMessage("Roadmap completed successfully.");
            stopAllControllers(player);

            // Record task success in learning memory
            LearningMemory.getInstance().recordTaskOutcome(
                    currentRoadmap.getGoal(), true, "completed", "All tasks done"
            );
            return;
        }

        RoadmapTask currentTask = currentRoadmap.getTasks().get(index);

        if (currentTask.getStatus() == TaskStatus.PENDING) {
            System.out.println("[BlockAI] Analyzing task: " + currentTask.getDescription());
            
            // Resolve dependencies dynamically using the local knowledge base
            java.util.List<RoadmapTask> expandedTasks = com.blockai.knowledge.DependencyResolver.resolveDependencies(currentTask, player);
            
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
            currentTask.setStatus(TaskStatus.IN_PROGRESS);
            taskTickCount = 0;
            com.blockai.client.screen.BlockAIChatScreen.addMessage("Starting task " + (index + 1) + ": " + currentTask.getDescription());
        }

        if (currentTask.getStatus() == TaskStatus.IN_PROGRESS) {
            String type = currentTask.getType().toLowerCase();
            boolean isFinished = false;
            boolean isFailed = false;

            // CLEAR_AREA — dedicated controller
            if (type.contains("clear")) {
                if (!player.clearAreaController.isActive()) {
                    int radius = currentTask.getRadius();
                    boolean allowsExcavation = currentTask.isAllowsExcavation();
                    player.clearAreaController.start(player, radius, allowsExcavation);
                }

                ControllerState state = player.clearAreaController.tick(player);
                if (state == ControllerState.SUCCESS) {
                    isFinished = true;
                } else if (state == ControllerState.FAILED) {
                    taskTickCount++;
                    if (taskTickCount > 200) {
                        isFailed = true;
                    }
                }
            }
            // EXCAVATE — physical layer-by-layer digging
            else if (type.contains("excavate")) {
                if (!player.excavationController.isActive()) {
                    int width = 1;
                    int length = 1;
                    String desc = currentTask.getDescription().toLowerCase();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)x(\\d+)").matcher(desc);
                    if (m.find()) {
                        width = Integer.parseInt(m.group(1));
                        length = Integer.parseInt(m.group(2));
                    } else {
                        // Fallback
                        width = currentTask.getRadius() > 0 ? currentTask.getRadius() : 1;
                        length = width;
                    }
                    player.excavationController.start(player, width, length);
                }

                ControllerState state = player.excavationController.tick(player);
                if (state == ControllerState.SUCCESS) {
                    isFinished = true;
                } else if (state == ControllerState.FAILED) {
                    taskTickCount++;
                    if (taskTickCount > 200) {
                        isFailed = true;
                    }
                }
            }
            // GATHERING — category-based resource collection
            else if (type.contains("mine") || type.contains("gather") || type.contains("collect") || type.contains("resource")) {
                if (player.gatheringController.isIdle()) {
                    ResourceRequirement req = buildResourceRequirement(currentTask);
                    System.out.println("[BlockAI] Resource requirement: " + req);
                    System.out.println("[BlockAI]   Accepted blocks: " + req.getAcceptedBlocks());
                    System.out.println("[BlockAI]   Already in inventory: " + req.countInInventory(player) + "/" + req.getQuantity());
                    player.gatheringController.setRequirement(req);
                }

                ControllerState state = player.gatheringController.tick(player);
                if (state == ControllerState.SUCCESS) {
                    isFinished = true;
                } else if (state == ControllerState.NO_TARGET || state == ControllerState.FAILED) {
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
                
                if (player.craftingController.getState() == ControllerState.SUCCESS) {
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
                    WorldObserver.scanLocalArea(player, 32);

                    // Log what was found
                    var memory = WorldObserver.MEMORY;
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
                currentTask.setStatus(TaskStatus.DONE);
                currentRoadmap.setCurrentTaskIndex(index + 1);
                taskTickCount = 0;
                player.gatheringController.stop();
                player.clearAreaController.stop();
                player.excavationController.stop();
            } else if (isFailed) {
                currentTask.setStatus(TaskStatus.FAILED);
                currentRoadmap.setState(ExecutionState.FAILED);
                System.out.println("[BlockAI] Roadmap failed at task " + (index + 1));
                stopAllControllers(player);

                // Record failure
                LearningMemory.getInstance().recordTaskOutcome(
                        currentRoadmap.getGoal(), false,
                        currentTask.getDescription(),
                        "Failed at task " + (index + 1)
                );
            }
        }
    }

    /**
     * Build a ResourceRequirement from a RoadmapTask.
     * Priority: task metadata > description parsing > fallback
     */
    private ResourceRequirement buildResourceRequirement(RoadmapTask task) {
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

        return new ResourceRequirement(category, specific, qty);
    }

    private int taskTickCount = 0;
}
