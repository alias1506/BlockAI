package com.blockai.gathering;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

import java.util.*;

public enum ResourceCategory {

    WOOD_LOG("wood", "log", "tree", "timber"),
    WOOD_PLANK("plank", "planks"),
    STONE("stone", "cobblestone", "rock"),
    COAL_ORE("coal"),
    IRON_ORE("iron"),
    COPPER_ORE("copper"),
    GOLD_ORE("gold"),
    DIAMOND_ORE("diamond"),
    DIRT("dirt", "earth"),
    SAND("sand"),
    GRAVEL("gravel"),
    FOOD("food", "eat", "hunger");

    private final List<String> keywords;

    ResourceCategory(String... keywords) {
        this.keywords = List.of(keywords);
    }

    public boolean matches(String blockIdStr) {
        Identifier loc = Identifier.tryParse(blockIdStr);
        if (loc == null) return false;
        
        Optional<Block> optBlock = BuiltInRegistries.BLOCK.getOptional(loc);
        if (optBlock.isEmpty()) return false;
        Block block = optBlock.get();

        switch (this) {
            case WOOD_LOG:
                return block.builtInRegistryHolder().is(BlockTags.LOGS);
            case WOOD_PLANK:
                return block.builtInRegistryHolder().is(BlockTags.PLANKS);
            case STONE:
                return blockIdStr.equals("minecraft:stone") || 
                       blockIdStr.equals("minecraft:cobblestone") || 
                       blockIdStr.equals("minecraft:diorite") || 
                       blockIdStr.equals("minecraft:andesite") || 
                       blockIdStr.equals("minecraft:granite");
            case COAL_ORE:
                return blockIdStr.equals("minecraft:coal_ore") || blockIdStr.equals("minecraft:deepslate_coal_ore");
            case IRON_ORE:
                return blockIdStr.equals("minecraft:iron_ore") || blockIdStr.equals("minecraft:deepslate_iron_ore");
            case COPPER_ORE:
                return blockIdStr.equals("minecraft:copper_ore") || blockIdStr.equals("minecraft:deepslate_copper_ore");
            case GOLD_ORE:
                return blockIdStr.equals("minecraft:gold_ore") || blockIdStr.equals("minecraft:deepslate_gold_ore");
            case DIAMOND_ORE:
                return blockIdStr.equals("minecraft:diamond_ore") || blockIdStr.equals("minecraft:deepslate_diamond_ore");
            case DIRT:
                return block.builtInRegistryHolder().is(BlockTags.DIRT);
            case SAND:
                return block.builtInRegistryHolder().is(BlockTags.SAND);
            case GRAVEL:
                return blockIdStr.equals("minecraft:gravel");
            case FOOD:
                return blockIdStr.equals("minecraft:sweet_berry_bush") ||
                       blockIdStr.equals("minecraft:melon") ||
                       blockIdStr.equals("minecraft:pumpkin") ||
                       blockIdStr.equals("minecraft:wheat") ||
                       blockIdStr.equals("minecraft:carrots") ||
                       blockIdStr.equals("minecraft:potatoes") ||
                       blockIdStr.equals("minecraft:beetroots");
            default:
                return false;
        }
    }

    public static ResourceCategory fromDescription(String description) {
        if (description == null) return null;
        String lower = description.toLowerCase();

        for (ResourceCategory cat : values()) {
            for (String keyword : cat.keywords) {
                if (lower.contains(keyword)) {
                    return cat;
                }
            }
        }
        return null;
    }

    public static ResourceCategory fromBlockId(String blockId) {
        for (ResourceCategory cat : values()) {
            if (cat.matches(blockId)) {
                return cat;
            }
        }
        return null;
    }

    public static ResourceCategory fromName(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fromDescription(name);
        }
    }
}
