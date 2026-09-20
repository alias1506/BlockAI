package com.blockai.client.keybind;

import com.blockai.client.screen.BlockAIChatScreen;
import com.blockai.network.BlockAINetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class BlockAIKeybinds {

    private static KeyMapping openChatKey;
    private static KeyMapping openInventoryKey;

    public static void register() {
        try {
            java.lang.reflect.Constructor<?> targetCtor = null;
            for (java.lang.reflect.Constructor<?> ctor : KeyMapping.class.getConstructors()) {
                System.out.println("[BlockAI] Found KeyMapping constructor: " + ctor);
                if (ctor.getParameterCount() == 4) {
                    targetCtor = ctor;
                } else if (ctor.getParameterCount() == 3 && targetCtor == null) {
                    targetCtor = ctor;
                }
            }

            if (targetCtor != null) {
                if (targetCtor.getParameterCount() == 4) {
                    Class<?> p3 = targetCtor.getParameterTypes()[3];
                    Object arg3 = null;
                    if (p3 == String.class) {
                        arg3 = "category.blockai";
                    } else if (p3.isEnum()) {
                        Object[] enums = p3.getEnumConstants();
                        if (enums.length > 0) {
                            arg3 = enums[0];
                            for (Object e : enums) {
                                if (e.toString().contains("UI") || e.toString().contains("MISC")) {
                                    arg3 = e;
                                    break;
                                }
                            }
                        }
                    }

                    // (String name, InputConstants.Type type, int code, Category category)
                    openChatKey = KeyMappingHelper.registerKeyMapping((KeyMapping) targetCtor.newInstance(
                            "blockai.open_chat",
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_C,
                            arg3
                    ));

                    openInventoryKey = KeyMappingHelper.registerKeyMapping((KeyMapping) targetCtor.newInstance(
                            "blockai.open_inventory",
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_V,
                            arg3
                    ));
                } else if (targetCtor.getParameterCount() == 3) {
                    System.out.println("[BlockAI] Falling back to 3-arg constructor: " + targetCtor);
                    Class<?> p1 = targetCtor.getParameterTypes()[1];
                    Class<?> p2 = targetCtor.getParameterTypes()[2];
                    
                    Object arg1 = (p1 == int.class) ? GLFW.GLFW_KEY_C : InputConstants.Type.KEYSYM;
                    
                    Object arg2 = null;
                    if (p2 == String.class) {
                        arg2 = "category.blockai";
                    } else if (p2.isEnum()) {
                        Object[] enums = p2.getEnumConstants();
                        if (enums.length > 0) {
                            arg2 = enums[0]; // Just use the first available category (likely movement or UI)
                            for (Object e : enums) {
                                if (e.toString().contains("UI") || e.toString().contains("MISC")) {
                                    arg2 = e;
                                    break;
                                }
                            }
                        }
                    }
                    
                    openChatKey = KeyMappingHelper.registerKeyMapping((KeyMapping) targetCtor.newInstance(
                            "blockai.open_chat", arg1, arg2
                    ));
                    
                    Object arg1_inv = (p1 == int.class) ? GLFW.GLFW_KEY_V : InputConstants.Type.KEYSYM;
                    openInventoryKey = KeyMappingHelper.registerKeyMapping((KeyMapping) targetCtor.newInstance(
                            "blockai.open_inventory", arg1_inv, arg2
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (openChatKey == null || openInventoryKey == null) {
            System.err.println("[BlockAI] FATAL: Failed to initialize keybindings!");
            return;
        }

        final boolean[] wasCPressed = {false};
        final boolean[] wasVPressed = {false};

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean isCPressedRaw = false;
            boolean isVPressedRaw = false;
            
            if (client.getWindow() != null) {
                isCPressedRaw = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_C);
                isVPressedRaw = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_V);
            }
            
            boolean triggerC = openChatKey.consumeClick() || (isCPressedRaw && !wasCPressed[0]);
            boolean triggerV = openInventoryKey.consumeClick() || (isVPressedRaw && !wasVPressed[0]);
            
            wasCPressed[0] = isCPressedRaw;
            wasVPressed[0] = isVPressedRaw;

            if (triggerC) {
                System.out.println("[BlockAI] C key triggered! isActive=" + BlockAIChatScreen.isActive);
                if (client.player != null && client.level != null && !BlockAIChatScreen.isActive) {
                    try {
                        System.out.println("[BlockAI] Attempting to open BlockAIChatScreen...");
                        client.setScreenAndShow(new BlockAIChatScreen());
                        System.out.println("[BlockAI] Screen should now be open.");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            if (triggerV) {
                if (client.player != null && client.level != null && !BlockAIChatScreen.isActive && client.getConnection() != null) {
                    try {
                        ClientPlayNetworking.send(new BlockAINetworking.OpenInvPayload());
                    } catch (Exception e) {
                        System.out.println("[BlockAI] Failed to send inventory packet: " + e.getMessage());
                    }
                }
            }
        });
    }
}
