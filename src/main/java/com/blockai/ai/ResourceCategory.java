package com.blockai.ai;

import java.util.*;

/**
 * Defines generic resource categories that map natural-language intent
 * to all valid concrete Minecraft block IDs.
 *
 * "gather wood" → WOOD_LOG → any log type
 * "gather 16 oak logs" → WOOD_LOG + specificItem = minecraft:oak_log
 */
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
    private static final Map<ResourceCategory, List<String>> ACCEPTED_BLOCKS = new EnumMap<>(ResourceCategory.class);

    static {
        ACCEPTED_BLOCKS.put(WOOD_LOG, List.of(
                "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
                "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
                "minecraft:mangrove_log", "minecraft:cherry_log",
                "minecraft:stripped_oak_log", "minecraft:stripped_spruce_log",
                "minecraft:stripped_birch_log", "minecraft:stripped_jungle_log",
                "minecraft:stripped_acacia_log", "minecraft:stripped_dark_oak_log",
                "minecraft:stripped_mangrove_log", "minecraft:stripped_cherry_log"
        ));
        ACCEPTED_BLOCKS.put(WOOD_PLANK, List.of(
                "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
                "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
                "minecraft:mangrove_planks", "minecraft:cherry_planks", "minecraft:bamboo_planks"
        ));
        ACCEPTED_BLOCKS.put(STONE, List.of(
                "minecraft:stone", "minecraft:cobblestone", "minecraft:andesite",
                "minecraft:diorite", "minecraft:granite"
        ));
        ACCEPTED_BLOCKS.put(COAL_ORE, List.of(
                "minecraft:coal_ore", "minecraft:deepslate_coal_ore"
        ));
        ACCEPTED_BLOCKS.put(IRON_ORE, List.of(
                "minecraft:iron_ore", "minecraft:deepslate_iron_ore"
        ));
        ACCEPTED_BLOCKS.put(COPPER_ORE, List.of(
                "minecraft:copper_ore", "minecraft:deepslate_copper_ore"
        ));
        ACCEPTED_BLOCKS.put(GOLD_ORE, List.of(
                "minecraft:gold_ore", "minecraft:deepslate_gold_ore"
        ));
        ACCEPTED_BLOCKS.put(DIAMOND_ORE, List.of(
                "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"
        ));
        ACCEPTED_BLOCKS.put(DIRT, List.of(
                "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt"
        ));
        ACCEPTED_BLOCKS.put(SAND, List.of(
                "minecraft:sand", "minecraft:red_sand"
        ));
        ACCEPTED_BLOCKS.put(GRAVEL, List.of(
                "minecraft:gravel"
        ));
        ACCEPTED_BLOCKS.put(FOOD, List.of(
                // Food is primarily item-based, not block-based
                // But crops/bushes can be gathered as blocks
                "minecraft:sweet_berry_bush", "minecraft:melon", "minecraft:pumpkin",
                "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes",
                "minecraft:beetroots"
        ));
    }

    ResourceCategory(String... keywords) {
        this.keywords = List.of(keywords);
    }

    /**
     * All block IDs that satisfy this category.
     */
    public List<String> getAcceptedBlocks() {
        return ACCEPTED_BLOCKS.getOrDefault(this, List.of());
    }

    /**
     * Does the given block ID belong to this category?
     */
    public boolean matches(String blockId) {
        return getAcceptedBlocks().contains(blockId);
    }

    /**
     * Parse a natural-language description into a resource category.
     * Returns null if no category matches.
     */
    public static ResourceCategory fromDescription(String description) {
        if (description == null) return null;
        String lower = description.toLowerCase();

        // Check each category's keywords
        for (ResourceCategory cat : values()) {
            for (String keyword : cat.keywords) {
                if (lower.contains(keyword)) {
                    return cat;
                }
            }
        }
        return null;
    }

    /**
     * Find which category a specific block ID belongs to.
     * Returns null if the block doesn't belong to any category.
     */
    public static ResourceCategory fromBlockId(String blockId) {
        for (ResourceCategory cat : values()) {
            if (cat.matches(blockId)) {
                return cat;
            }
        }
        return null;
    }

    /**
     * Try to parse a category name string (e.g. "WOOD_LOG") into the enum.
     */
    public static ResourceCategory fromName(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try description-based fallback
            return fromDescription(name);
        }
    }
}
