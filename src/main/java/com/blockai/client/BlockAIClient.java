package com.blockai.client;

import com.blockai.BlockAI;
import com.blockai.config.BlockAIConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class BlockAIClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockAI.LOGGER.info("Initializing BlockAI Client");
        com.blockai.network.BlockAINetworking.registerClient();
        com.blockai.client.keybind.BlockAIKeybinds.register();
    }
}
