package com.blockai;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blockai.ai.AIPlayerController;
import com.blockai.ai.knowledge.MinecraftKnowledgeBase;
import com.blockai.ai.memory.AgentMemory;
import com.blockai.ai.memory.ResourceMemory;

public class BlockAI implements ModInitializer {
	public static final String MOD_ID = "blockai";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
		com.blockai.ai.AIPlayerController.register();
		com.blockai.network.BlockAINetworking.registerServer();
		
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
		    com.blockai.ai.memory.AgentMemory.load(server);
		    com.blockai.ai.memory.ResourceMemory.load(server);
		    com.blockai.ai.knowledge.MinecraftKnowledgeBase.initialize(server);
		    com.blockai.config.APIKeyManager.load(server);
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
		    // Don't need to explicitly save ResourceMemory here as it saves on every record, but we could
		    com.blockai.ai.memory.AgentMemory.save(server);
		    com.blockai.config.APIKeyManager.clear();
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
