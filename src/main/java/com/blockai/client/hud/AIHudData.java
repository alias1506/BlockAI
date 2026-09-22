package com.blockai.client.hud;

/**
 * Client-side holder for the AI player's live HUD stats,
 * synced from the server via HudSyncPayload.
 */
public class AIHudData {
    public static float health = 20.0f;
    public static float maxHealth = 20.0f;
    public static int hunger = 20;
    public static int armor = 0;
    public static double attackDamage = 1.0;
    
    public static void update(float h, float maxH, int food, int arm, double dmg) {
        health = h;
        maxHealth = maxH;
        hunger = food;
        armor = arm;
        attackDamage = dmg;
    }
}
