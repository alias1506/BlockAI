package com.blockai.ai;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class TargetFailureMemory {
    
    private static final Map<BlockPos, FailureRecord> failures = new HashMap<>();
    
    public static class FailureRecord {
        public long timestamp;
        public int attempts;
        public String reason;
    }
    
    public static void recordFailure(BlockPos pos, String reason) {
        cleanExpired();
        FailureRecord record = failures.computeIfAbsent(pos, k -> new FailureRecord());
        record.timestamp = System.currentTimeMillis();
        record.attempts++;
        record.reason = reason;
        
        System.out.println("[BlockAI] TargetFailureMemory: Blacklisted target at " + pos + " due to " + reason + " (Attempts: " + record.attempts + ")");
    }
    
    public static boolean isBlacklisted(BlockPos pos) {
        FailureRecord record = failures.get(pos);
        if (record == null) return false;
        
        // Cooldown based on reason or attempts
        long cooldown = 15000; // 15 seconds default
        if ("PATH_BLOCKED".equals(record.reason)) {
            cooldown = 45000; // 45 seconds
        } else if ("NO_PATH".equals(record.reason)) {
            cooldown = 60000; // 60 seconds
        }
        
        if ((System.currentTimeMillis() - record.timestamp) >= cooldown) {
            failures.remove(pos);
            return false;
        }
        
        return true;
    }
    
    private static void cleanExpired() {
        long now = System.currentTimeMillis();
        failures.entrySet().removeIf(entry -> {
            FailureRecord record = entry.getValue();
            long cooldown = "PATH_BLOCKED".equals(record.reason) ? 45000 : 15000;
            return (now - record.timestamp) >= cooldown;
        });
    }
    
    public static FailureRecord getRecord(BlockPos pos) {
        return failures.get(pos);
    }
    
    public static void clear() {
        failures.clear();
    }
}
