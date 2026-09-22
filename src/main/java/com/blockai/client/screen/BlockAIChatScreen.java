package com.blockai.client.screen;

import com.blockai.groq.GroqClient;
import com.blockai.config.APIKeyManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import com.blockai.inventory.InventoryManager;
import com.blockai.ai.planner.AIPlanner;
import com.blockai.groq.GroqProvider;
import com.blockai.ai.AIPlayerController;
import com.blockai.ai.AIPlayer;
import com.blockai.groq.AIProvider;
import com.blockai.movement.PathFinder;
import com.blockai.ai.planner.RoadmapTask;
import com.blockai.ai.knowledge.DependencyResolver;
import com.blockai.ai.planner.Roadmap;

public class BlockAIChatScreen extends Screen {
    public static boolean isActive = false;
    
    public enum MessageType {
        AI, PLAYER, SYSTEM
    }

    public static class ChatMessage {
        public final String sender;
        public final String text;
        public final MessageType type;
        
        public ChatMessage(String sender, String text, MessageType type) {
            this.sender = sender;
            this.text = text;
            this.type = type;
        }
    }

    private static final List<ChatMessage> chatHistory = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean hasGreeted = false;

    private EditBox chatField;
    private double scrollAmount = 0;
    private double maxScroll = 0;
    private boolean justOpened = true;

    public BlockAIChatScreen() {
        super(Component.literal("BlockAI"));
    }

    @Override
    public void removed() {
        isActive = false;
        super.removed();
    }
    
    @Override
    public void onClose() {
        isActive = false;
        super.onClose();
    }

    @Override
    protected void init() {
        isActive = true;
        super.init();
        
        int composerMarginX = 40;
        int composerMarginY = 20;
        int composerWidth = this.width - composerMarginX * 2;
        int composerHeight = 28;
        
        int composerX = composerMarginX;
        int composerY = this.height - composerHeight - composerMarginY;
        
        this.chatField = new EditBox(this.font, composerX + 15, composerY + (composerHeight - 10) / 2, composerWidth - 50, 20, Component.literal(""));
        this.chatField.setMaxLength(256);
        this.chatField.setHint(Component.literal("Type a message..."));
        this.chatField.setBordered(false);
        this.addRenderableWidget(this.chatField);
        
        int btnX = composerX + composerWidth - 35;
        int btnY = composerY + (composerHeight - 20) / 2;
        
        net.minecraft.client.gui.components.Button sendButton = net.minecraft.client.gui.components.Button.builder(Component.literal(""), button -> {
            sendMessage();
        }).bounds(btnX, btnY, 20, 20).build();
        this.addWidget(sendButton); // Add as widget but NOT renderable so we can draw it ourselves
        
        this.setInitialFocus(this.chatField);

        if (!hasGreeted) {
            String username = this.minecraft != null && this.minecraft.getUser() != null ? this.minecraft.getUser().getName() : "Player";
            chatHistory.add(new ChatMessage("BlockAI", "Hello " + username + "!!!\nI'm BlockAI, your survival AI companion.\nTell me what you want me to do.", MessageType.AI));
            hasGreeted = true;
        }
        
        scrollToBottom();
    }
    
    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (justOpened) {
            justOpened = false;
            if (event.codepoint() == 'c' || event.codepoint() == 'C') {
                return true; // Consume the keybind event
            }
        }
        return super.charTyped(event);
    }

    private void scrollToBottom() {
        this.scrollAmount = 999999; 
    }

    private void sendMessage() {
        String message = this.chatField.getValue().trim();
        if (message.isEmpty()) return;
        
        this.chatField.setValue("");
        
        if (message.startsWith("/")) {
            handleCommand(message);
        } else {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.matches(".*\\bfollow\\b.*") || lowerMessage.matches(".*\\bcome\\b.*") || lowerMessage.matches(".*\\bstay\\b.*")) {
                addPlayerMessage(message);
                if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                    this.minecraft.getSingleplayerServer().execute(() -> {
                        com.blockai.ai.AIPlayer ai = (com.blockai.ai.AIPlayer) com.blockai.ai.AIPlayerController.getAIPlayer(this.minecraft.getSingleplayerServer());
                        if (ai != null) {
                            ai.agentController.startFollow(ai, this.minecraft.player);
                            this.minecraft.execute(() -> addAIMessage("Okay, I'll follow you."));
                        } else {
                            this.minecraft.execute(() -> addAIMessage("I'm not spawned yet."));
                        }
                    });
                }
                return;
            }
            
            addPlayerMessage(message);
            if (!APIKeyManager.hasKey()) {
                addAIMessage("No Groq API key is registered. Falling back to local deterministic planner.");
            }
            
            chatHistory.removeIf(m -> m.type == MessageType.SYSTEM && m.text.equals("Thinking..."));
            addSystemMessage("Thinking...");
            
            String contextMessage = message;
            if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                com.blockai.ai.AIPlayer ai = (com.blockai.ai.AIPlayer) com.blockai.ai.AIPlayerController.getAIPlayer(this.minecraft.getSingleplayerServer());
                if (ai != null) {
                    StringBuilder ctx = new StringBuilder(message);
                    ctx.append("\n\n--- CURRENT CONTEXT ---\n");
                    ctx.append("Inventory: ");
                    boolean hasItems = false;
                    for (int i = 0; i < ai.getInventory().getContainerSize(); i++) {
                        net.minecraft.world.item.ItemStack stack = ai.getInventory().getItem(i);
                        if (!stack.isEmpty()) {
                            hasItems = true;
                            ctx.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath())
                               .append(" x").append(stack.getCount()).append(", ");
                        }
                    }
                    if (!hasItems) ctx.append("Empty");
                    
                    ctx.append("\nNote: Do not list out crafting prerequisites (e.g. gathering wood to make a pickaxe) as separate tasks in your roadmap. The local decision engine will handle all prerequisites and crafting automatically. Just provide the high-level goal tasks (e.g. just say 'gather 20 stone').");
                    contextMessage = ctx.toString();
                }
            }
            final String finalContextMessage = contextMessage;
            
            String lowerMsg = message.toLowerCase();
            boolean isBasicTask = lowerMsg.startsWith("get ") || lowerMsg.startsWith("gather ") || lowerMsg.startsWith("collect ") || lowerMsg.startsWith("mine ") || lowerMsg.startsWith("craft ") || lowerMsg.startsWith("make ") || lowerMsg.startsWith("build ") || lowerMsg.startsWith("clear ") || lowerMsg.startsWith("excavate ");
            
            com.blockai.groq.AIProvider provider;
            if (isBasicTask || !APIKeyManager.hasKey()) {
                provider = new com.blockai.ai.planner.AIPlanner();
            } else {
                provider = new com.blockai.groq.GroqProvider();
            }
            
            java.util.function.Consumer<com.blockai.ai.planner.Roadmap> handleSuccess = roadmap -> {
                chatHistory.removeIf(m -> m.type == MessageType.SYSTEM && m.text.equals("Thinking..."));
                
                if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                    this.minecraft.getSingleplayerServer().execute(() -> {
                        net.minecraft.server.MinecraftServer server = this.minecraft.getSingleplayerServer();
                        com.blockai.ai.AIPlayer ai = (com.blockai.ai.AIPlayer) com.blockai.ai.AIPlayerController.getAIPlayer(server);
                        if (ai != null) {
                            ai.agentController.start(ai, roadmap);
                            
                            // Re-sync UI state on the client thread
                            this.minecraft.execute(() -> {
                                StringBuilder sb = new StringBuilder("I've created a plan for this task:\n\n");
                                for (com.blockai.ai.planner.RoadmapTask task : roadmap.getTasks()) {
                                    sb.append(task.getId()).append(". ").append(task.getDescription()).append("\n   ○ PENDING\n");
                                }
                                addAIMessage(sb.toString().trim());
                            });
                        } else {
                            this.minecraft.execute(() -> {
                                addAIMessage("I'm waiting for my Minecraft body to become available.");
                            });
                        }
                    });
                } else {
                    addSystemMessage("Error: Not in a singleplayer world.");
                }
            };
            
            provider.sendMessageAsync(finalContextMessage, handleSuccess, error -> {
                if (provider instanceof com.blockai.groq.GroqProvider) {
                    addSystemMessage("Groq API failed (" + error + "). Falling back to local deterministic planner...");
                    new com.blockai.ai.planner.AIPlanner().sendMessageAsync(finalContextMessage, handleSuccess, err -> {
                        chatHistory.removeIf(m -> m.type == MessageType.SYSTEM && m.text.equals("Thinking..."));
                        addSystemMessage("Local Planner Error: " + err);
                    });
                } else {
                    chatHistory.removeIf(m -> m.type == MessageType.SYSTEM && m.text.equals("Thinking..."));
                    addSystemMessage("Error: " + error);
                }
            });
        }
    }

    private void handleCommand(String command) {
        String lowerCmd = command.toLowerCase();
        
        if (lowerCmd.equals("/clear")) {
            chatHistory.clear();
            this.scrollAmount = 0;
            return; // No response bubble
        }
        
        // Render command as a player bubble
        if (lowerCmd.startsWith("/api ")) {
            String newKey = command.substring(5).trim();
            String maskedKey = newKey.length() > 4 ? "gsk_**************" + newKey.substring(newKey.length() - 4) : "gsk_****";
            addPlayerMessage("/api " + maskedKey);
        } else {
            addPlayerMessage(command);
        }
        
        if (lowerCmd.equals("/api")) {
            if (APIKeyManager.hasKey()) {
                String key = APIKeyManager.getKey();
                String masked = key.length() > 4 ? "gsk_**************" + key.substring(key.length() - 4) : "gsk_****";
                addAIMessage("Groq API key is registered.\n" + masked);
            } else {
                addAIMessage("No Groq API key is registered.");
            }
        } else if (lowerCmd.startsWith("/api ")) {
            String newKey = command.substring(5).trim();
            if (!newKey.isEmpty()) {
                if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                    APIKeyManager.save(this.minecraft.getSingleplayerServer(), newKey);
                    addAIMessage("Groq API key registered successfully.");
                } else {
                    addSystemMessage("Error: You must be in a singleplayer world to register an API key.");
                }
            }
        } else if (lowerCmd.equals("/start")) {
            if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                this.minecraft.getSingleplayerServer().execute(() -> {
                    com.blockai.ai.AIPlayer ai = (com.blockai.ai.AIPlayer) com.blockai.ai.AIPlayerController.getAIPlayer(this.minecraft.getSingleplayerServer());
                    if (ai != null) {
                        com.blockai.ai.AgentState state = ai.getAgentState();
                        if (state == com.blockai.ai.AgentState.RUNNING) {
                            this.minecraft.execute(() -> addAIMessage("BlockAI is already running."));
                        } else {
                            ai.agentController.start(ai);
                            
                            this.minecraft.execute(() -> {
                                if (state == com.blockai.ai.AgentState.RECOVERING) {
                                    addAIMessage("BlockAI started. Continuing recovery.");
                                } else if (ai.agentController.getCurrentRoadmap() != null) {
                                    addAIMessage("BlockAI started. Resuming the current task.");
                                } else {
                                    addAIMessage("BlockAI started. I'm ready for your next task.");
                                }
                            });
                        }
                    } else {
                        // AI doesn't exist, spawn it
                        com.blockai.ai.AIPlayerController.forceSpawn(this.minecraft.getSingleplayerServer());
                        this.minecraft.execute(() -> addAIMessage("BlockAI started. Spawning AI player and waiting for your next task."));
                    }
                });
            }
        } else if (lowerCmd.equals("/pause") || lowerCmd.equals("/resume") || lowerCmd.equals("/stop")) {
            if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                this.minecraft.getSingleplayerServer().execute(() -> {
                    com.blockai.ai.AIPlayer ai = (com.blockai.ai.AIPlayer) com.blockai.ai.AIPlayerController.getAIPlayer(this.minecraft.getSingleplayerServer());
                    if (ai != null) {
                        if (lowerCmd.equals("/pause")) ai.agentController.pause(ai);
                        else if (lowerCmd.equals("/resume")) ai.agentController.resume(ai);
                        else ai.agentController.stop(ai);
                        
                        String response = lowerCmd.equals("/pause") ? "BlockAI paused." :
                                          lowerCmd.equals("/resume") ? "BlockAI resumed." :
                                          "BlockAI stopped.";
                        this.minecraft.execute(() -> addAIMessage(response));
                    } else {
                        this.minecraft.execute(() -> addAIMessage("BlockAI is not currently spawned."));
                    }
                });
            }
        } else if (lowerCmd.equals("/blockai_test_move")) {
            if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                this.minecraft.getSingleplayerServer().execute(() -> {
                    com.blockai.ai.AIPlayer ai = (com.blockai.ai.AIPlayer) com.blockai.ai.AIPlayerController.getAIPlayer(this.minecraft.getSingleplayerServer());
                    if (ai != null) {
                        net.minecraft.core.BlockPos target = ai.blockPosition().relative(ai.getDirection(), 5);
                        this.minecraft.execute(() -> addAIMessage("Movement test started. Finding path to " + target));
                        java.util.List<net.minecraft.core.BlockPos> path = com.blockai.movement.PathFinder.findPath(
                            (net.minecraft.server.level.ServerLevel) ai.level(), ai.blockPosition(), target);
                        
                        if (path != null) {
                            ai.movementController.setPath(path);
                            ai.setAgentState(com.blockai.ai.AgentState.RUNNING); // Ensure we process movement ticks
                            this.minecraft.execute(() -> addAIMessage("Path generated. Moving..."));
                        } else {
                            this.minecraft.execute(() -> addAIMessage("Path calculation failed for test move."));
                        }
                    } else {
                        this.minecraft.execute(() -> addAIMessage("BlockAI is not spawned."));
                    }
                });
            }
        } else if (lowerCmd.equals("/testgather")) {
            if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                this.minecraft.getSingleplayerServer().execute(() -> {
                    com.blockai.ai.AIPlayer ai = (com.blockai.ai.AIPlayer) com.blockai.ai.AIPlayerController.getAIPlayer(this.minecraft.getSingleplayerServer());
                    if (ai != null) {
                        this.minecraft.execute(() -> addSystemMessage("[BlockAI][TEST] Starting deterministic wood gathering"));
                        java.util.List<com.blockai.ai.planner.RoadmapTask> tasks = new java.util.ArrayList<>();
                        com.blockai.ai.planner.RoadmapTask gatherTask = new com.blockai.ai.planner.RoadmapTask(1, "resource_gathering", "Collect 4 wooden logs nearby");
                        gatherTask.setResource("WOOD_LOG", null, 4);
                        tasks.add(gatherTask);
                        com.blockai.ai.planner.Roadmap testMap = new com.blockai.ai.planner.Roadmap("Collect 4 wooden logs nearby", "Test deterministic gathering", tasks);
                        ai.agentController.start(ai, testMap);
                    } else {
                        this.minecraft.execute(() -> addAIMessage("BlockAI is not spawned."));
                    }
                });
            }
        } else if (lowerCmd.equals("/status")) {
            if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                this.minecraft.getSingleplayerServer().execute(() -> {
                    com.blockai.ai.AIPlayer ai = (com.blockai.ai.AIPlayer) com.blockai.ai.AIPlayerController.getAIPlayer(this.minecraft.getSingleplayerServer());
                    if (ai != null) {
                        String stateStr = ai.getAgentState().name();
                        com.blockai.ai.planner.Roadmap roadmap = ai.agentController.getCurrentRoadmap();
                        String goalStr = roadmap != null ? roadmap.getGoal() : "None";
                        String taskStr = "None";
                        String progressStr = "0/0";
                        if (roadmap != null) {
                            int idx = roadmap.getCurrentTaskIndex();
                            if (idx < roadmap.getTasks().size()) {
                                taskStr = roadmap.getTasks().get(idx).getDescription();
                                progressStr = (idx) + "/" + roadmap.getTasks().size();
                            } else {
                                progressStr = roadmap.getTasks().size() + "/" + roadmap.getTasks().size();
                            }
                        }
                        
                        int health = (int) ai.getHealth();
                        int maxHealth = (int) ai.getMaxHealth();
                        int food = ai.getFoodData().getFoodLevel();
                        int armor = ai.getArmorValue();
                        
                        String response = "BlockAI is " + (stateStr.equals("RUNNING") ? "running." : "stopped.") + "\n" +
                                "Current goal: " + goalStr + "\n" +
                                "Current task: " + taskStr + "\n" +
                                "Progress: " + progressStr + "\n" +
                                "AI health: " + health + "/" + maxHealth + "\n" +
                                "Food: " + food + "/20\n" +
                                "Armor: " + armor + "\n" +
                                "State: " + stateStr;
                        this.minecraft.execute(() -> addAIMessage(response));
                    } else {
                        this.minecraft.execute(() -> addAIMessage("BlockAI is stopped. AI player is not currently spawned."));
                    }
                });
            }
        } else if (lowerCmd.equals("/inv")) {
            if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                this.minecraft.getSingleplayerServer().execute(() -> {
                    net.minecraft.server.MinecraftServer server = this.minecraft.getSingleplayerServer();
                    net.minecraft.server.level.ServerPlayer human = server.getPlayerList().getPlayer(this.minecraft.player.getUUID());
                    net.minecraft.server.level.ServerPlayer ai = com.blockai.ai.AIPlayerController.getAIPlayer(server);
                    if (human != null && ai != null) {
                        com.blockai.inventory.InventoryManager.openAIInventoryFor(human, ai);
                    }
                });
            }
            this.onClose();
        } else if (lowerCmd.startsWith("/knowledge plan ")) {
            String goalId = command.substring(16).trim();
            if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                this.minecraft.getSingleplayerServer().execute(() -> {
                    net.minecraft.server.MinecraftServer server = this.minecraft.getSingleplayerServer();
                    com.blockai.ai.AIPlayer ai = (com.blockai.ai.AIPlayer) com.blockai.ai.AIPlayerController.getAIPlayer(server);
                    if (ai != null) {
                        com.blockai.ai.planner.RoadmapTask fakeTask = new com.blockai.ai.planner.RoadmapTask(-1, "combat", goalId);
                        java.util.List<com.blockai.ai.planner.RoadmapTask> plan = com.blockai.ai.knowledge.DependencyResolver.resolveDependencies(fakeTask, ai);
                        
                        StringBuilder sb = new StringBuilder("Dependency Graph Output for " + goalId + ":\n");
                        for (int i = 0; i < plan.size(); i++) {
                            sb.append(i + 1).append(". ").append(plan.get(i).getDescription()).append("\n");
                        }
                        this.minecraft.execute(() -> addAIMessage(sb.toString().trim()));
                    }
                });
            }
        } else {
            addSystemMessage("Unknown command.");
        }
    }

    public static void addMessage(String message) {
        // Now redirects system task messages to subtle task cards if they are task updates, or AI message
        if (message.startsWith("Starting task") || message.equals("Roadmap completed successfully.")) {
            chatHistory.add(new ChatMessage("System", message, MessageType.SYSTEM));
        } else {
            chatHistory.add(new ChatMessage("BlockAI", message, MessageType.AI));
        }
    }
    
    private void addPlayerMessage(String text) {
        chatHistory.add(new ChatMessage("You", text, MessageType.PLAYER));
        scrollToBottom();
    }
    
    private void addAIMessage(String text) {
        chatHistory.add(new ChatMessage("BlockAI", text, MessageType.AI));
        scrollToBottom();
    }
    
    private void addSystemMessage(String text) {
        chatHistory.add(new ChatMessage("System", text, MessageType.SYSTEM));
        scrollToBottom();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollAmount -= scrollY * 20;
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Render main WhatsApp-style background
        context.fill(0, 0, this.width, this.height, 0xD0111111); // Dark translucent
        
        // Render Header
        int headerHeight = 35;
        context.fill(0, 0, this.width, headerHeight, 0xFF1C1C1C); // Solid dark header
        context.fill(0, headerHeight, this.width, headerHeight + 1, 0xFF333333); // Border
        
        // Header DP
        drawAvatar(context, 15, 7, 22, MessageType.AI);
        
        context.text(this.font, "BlockAI", 45, 8, 0xFFFFFFFF);
        context.text(this.font, "Survival AI Companion", 45, 18, 0xFFAAAAAA);
        
        String status = "● Online";
        int statusWidth = this.font.width(status);
        context.text(this.font, status, this.width - statusWidth - 20, 13, 0xFF55FF55);
        
        // Render Chat Area
        int yOffset = 10;
        int maxBubbleWidth = (int)(this.width * 0.7);
        List<RenderBubble> bubbles = new ArrayList<>();
        
        for (ChatMessage msg : chatHistory) {
            List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(Component.literal(msg.text), maxBubbleWidth);
            
            // Calculate accurate text width so the bubble fits tightly
            int textWidth = 0;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                int lw = this.font.width(line);
                if (lw > textWidth) textWidth = lw;
            }
            
            int bubbleHeight = lines.size() * 10 + 12; // padding
            bubbles.add(new RenderBubble(msg, lines, textWidth, yOffset, bubbleHeight));
            yOffset += bubbleHeight + 15; // spacing between messages
        }
        
        int contentHeight = yOffset;
        int viewportHeight = this.height - headerHeight - 50; // 50 for input area
        
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollAmount = Math.max(0, Math.min(scrollAmount, maxScroll));
        
        context.enableScissor(0, headerHeight + 1, this.width, this.height - 50);
        
        int drawY = headerHeight + 10 - (int)scrollAmount;
        
        for (RenderBubble b : bubbles) {
            if (drawY + b.yOffset + b.height < headerHeight || drawY + b.yOffset > this.height - 50) {
                continue; // Culling
            }
            
            int bubbleY = drawY + b.yOffset;
            
            if (b.msg.type == MessageType.AI) {
                int dpSize = 20;
                int startX = 15;
                drawAvatar(context, startX, bubbleY, dpSize, MessageType.AI);
                
                int bubbleX = startX + dpSize + 10;
                int paddedWidth = b.textWidth + 16;
                
                // Draw rounded bubble for AI
                context.fill(bubbleX, bubbleY, bubbleX + paddedWidth, bubbleY + b.height, 0xFF2A2A2A);
                context.fill(bubbleX + 1, bubbleY - 1, bubbleX + paddedWidth - 1, bubbleY, 0xFF2A2A2A);
                context.fill(bubbleX + 1, bubbleY + b.height, bubbleX + paddedWidth - 1, bubbleY + b.height + 1, 0xFF2A2A2A);
                
                for (int i = 0; i < b.lines.size(); i++) {
                    context.text(this.font, b.lines.get(i), bubbleX + 8, bubbleY + 6 + (i * 10), 0xFFDDDDDD);
                }
            } else if (b.msg.type == MessageType.PLAYER) {
                int dpSize = 20;
                int rightMargin = this.width - 15;
                
                int paddedWidth = b.textWidth + 16;
                int bubbleX = rightMargin - dpSize - 10 - paddedWidth;
                
                // Draw rounded bubble for Player (Greenish like WhatsApp)
                context.fill(bubbleX, bubbleY, bubbleX + paddedWidth, bubbleY + b.height, 0xFF005C4B);
                context.fill(bubbleX + 1, bubbleY - 1, bubbleX + paddedWidth - 1, bubbleY, 0xFF005C4B);
                context.fill(bubbleX + 1, bubbleY + b.height, bubbleX + paddedWidth - 1, bubbleY + b.height + 1, 0xFF005C4B);
                
                for (int i = 0; i < b.lines.size(); i++) {
                    context.text(this.font, b.lines.get(i), bubbleX + 8, bubbleY + 6 + (i * 10), 0xFFFFFFFF);
                }
                
                drawAvatar(context, rightMargin - dpSize, bubbleY, dpSize, MessageType.PLAYER);
            } else {
                // System message (Task Progress)
                int paddedWidth = b.textWidth + 20;
                int startX = (this.width - paddedWidth) / 2;
                context.fill(startX, bubbleY, startX + paddedWidth, bubbleY + b.height, 0xAA222222);
                
                for (int i = 0; i < b.lines.size(); i++) {
                    context.text(this.font, b.lines.get(i), startX + 10, bubbleY + 6 + (i * 10), 0xFFFFFF55);
                }
            }
        }
        
        context.disableScissor();
        
        // Render unified WhatsApp-style message composer
        int composerMarginX = 40;
        int composerMarginY = 20;
        int composerWidth = this.width - composerMarginX * 2;
        int composerHeight = 28;
        
        int composerX = composerMarginX;
        int composerY = this.height - composerHeight - composerMarginY;
        
        // Composer background
        context.fill(composerX, composerY, composerX + composerWidth, composerY + composerHeight, 0xD0222222);
        // Soft rounded borders (pseudo-antialiasing)
        context.fill(composerX + 2, composerY - 1, composerX + composerWidth - 2, composerY, 0xD0222222);
        context.fill(composerX + 2, composerY + composerHeight, composerX + composerWidth - 2, composerY + composerHeight + 1, 0xD0222222);
        context.fill(composerX - 1, composerY + 2, composerX, composerY + composerHeight - 2, 0xD0222222);
        context.fill(composerX + composerWidth, composerY + 2, composerX + composerWidth + 1, composerY + composerHeight - 2, 0xD0222222);
        
        // Composer outline/border
        int borderColor = this.chatField.isFocused() ? 0xFF777777 : 0xFF444444;
        
        // Top and Bottom straight edges
        context.fill(composerX + 2, composerY - 2, composerX + composerWidth - 2, composerY - 1, borderColor);
        context.fill(composerX + 2, composerY + composerHeight + 1, composerX + composerWidth - 2, composerY + composerHeight + 2, borderColor);
        
        // Left and Right straight edges
        context.fill(composerX - 2, composerY + 2, composerX - 1, composerY + composerHeight - 2, borderColor);
        context.fill(composerX + composerWidth + 1, composerY + 2, composerX + composerWidth + 2, composerY + composerHeight - 2, borderColor);
        
        // Top-Left corner
        context.fill(composerX, composerY - 1, composerX + 2, composerY, borderColor);
        context.fill(composerX - 1, composerY, composerX, composerY + 2, borderColor);
        
        // Top-Right corner
        context.fill(composerX + composerWidth - 2, composerY - 1, composerX + composerWidth, composerY, borderColor);
        context.fill(composerX + composerWidth, composerY, composerX + composerWidth + 1, composerY + 2, borderColor);
        
        // Bottom-Left corner
        context.fill(composerX, composerY + composerHeight, composerX + 2, composerY + composerHeight + 1, borderColor);
        context.fill(composerX - 1, composerY + composerHeight - 2, composerX, composerY + composerHeight, borderColor);
        
        // Bottom-Right corner
        context.fill(composerX + composerWidth - 2, composerY + composerHeight, composerX + composerWidth, composerY + composerHeight + 1, borderColor);
        context.fill(composerX + composerWidth, composerY + composerHeight - 2, composerX + composerWidth + 1, composerY + composerHeight, borderColor);
        
        // Draw Send Button icon integrated into composer
        int btnX = composerX + composerWidth - 35;
        int btnY = composerY + (composerHeight - 20) / 2;
        boolean canSend = !this.chatField.getValue().trim().isEmpty();
        
        int iconColor = canSend ? 0xFF00C896 : 0xFF666666;
        context.text(this.font, "➤", btnX + 6, btnY + 6, iconColor);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    
    private void drawAvatar(GuiGraphicsExtractor context, int x, int y, int size, MessageType type) {
        // Draw a colored rounded box with an initial
        int color = type == MessageType.AI ? 0xFF0088FF : 0xFFFF8800;
        String letter = type == MessageType.AI ? "AI" : "P";
        
        String username = this.minecraft != null && this.minecraft.getUser() != null ? this.minecraft.getUser().getName() : "";
        if (type == MessageType.PLAYER && !username.isEmpty()) {
            letter = username.substring(0, 1).toUpperCase();
        }
        
        context.fill(x, y + 1, x + size, y + size - 1, color);
        context.fill(x + 1, y, x + size - 1, y + size, color);
        
        int strWidth = this.font.width(letter);
        int textY = y + (size / 2) - 4;
        int textX = x + (size - strWidth) / 2;
        context.text(this.font, letter, textX, textY, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            sendMessage();
            return true;
        }
        return super.keyPressed(event);
    }
    
    private static class RenderBubble {
        final ChatMessage msg;
        final List<net.minecraft.util.FormattedCharSequence> lines;
        final int textWidth;
        final int yOffset;
        final int height;
        
        RenderBubble(ChatMessage msg, List<net.minecraft.util.FormattedCharSequence> lines, int textWidth, int yOffset, int height) {
            this.msg = msg;
            this.lines = lines;
            this.textWidth = textWidth;
            this.yOffset = yOffset;
            this.height = height;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
