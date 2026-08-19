package com.aiplayer.behavior.combat;

import java.util.logging.Logger;

/**
 * Combat State Management
 * Tracks current combat status for AI players
 *
 * Enhanced to integrate with PacketLogger for real-time state tracking
 */
public class CombatState {
    private static final Logger LOGGER = Logger.getLogger(CombatState.class.getName());

    // Core combat state
    private boolean inCombat = false;
    private String target = null;
    private long startTime = 0;
    private long combatDuration = 0;

    // Health/Mana tracking
    private int health = 100;
    private int maxHealth = 100;
    private int mana = 100;
    private int maxMana = 100;

    // Combat statistics
    private int killCount = 0;
    private int damageDealt = 0;
    private int damageTaken = 0;
    private int blocksSuccessful = 0;

    // Combat context
    private int hostileEntitiesNearby = 0;
    private long lastDamageTime = 0;
    private String lastDamageSource = null;

    public CombatState() {
        reset();
    }

    /**
     * Reset combat state to initial values
     */
    public void reset() {
        inCombat = false;
        target = null;
        startTime = 0;
        combatDuration = 0;
        health = 100;
        maxHealth = 100;
        mana = 100;
        maxMana = 100;
        killCount = 0;
        damageDealt = 0;
        damageTaken = 0;
        blocksSuccessful = 0;
        hostileEntitiesNearby = 0;
        lastDamageTime = 0;
        lastDamageSource = null;
    }

    /**
     * Begin combat with a target
     */
    public void startCombat(String targetId) {
        this.inCombat = true;
        this.target = targetId;
        this.startTime = System.currentTimeMillis();
        LOGGER.info("[COMBAT] Started combat with target: " + targetId);
    }

    /**
     * End combat
     */
    public void endCombat(String reason) {
        long endTime = System.currentTimeMillis();
        this.combatDuration = endTime - startTime;
        this.inCombat = false;
        LOGGER.info("[COMBAT] Ended combat: " + reason + " (duration: " + combatDuration + "ms)");
    }

    // Enhanced health methods with percentage tracking
    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(health, maxHealth));
    }

    public void setHealthPercent(int percent) {
        this.health = (maxHealth * percent) / 100;
    }

    public double getHealthPercentage() {
        return maxHealth > 0 ? (double) health / maxHealth * 100 : 0;
    }

    public boolean isHealthy() {
        return getHealthPercentage() > 20;
    }

    public boolean isHealthCritical() {
        return getHealthPercentage() <= 20;
    }

    public boolean isDead() {
        return health <= 0;
    }

    // Mana methods with percentage tracking
    public void setMana(int mana) {
        this.mana = Math.max(0, Math.min(mana, maxMana));
    }

    public void setManaPercent(int percent) {
        this.mana = (maxMana * percent) / 100;
    }

    public double getManaPercentage() {
        return maxMana > 0 ? (double) mana / maxMana * 100 : 0;
    }

    public boolean isManaCritical() {
        return getManaPercentage() <= 15;
    }

    // Getter/setter methods
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public void setMaxHealth(int maxHealth) { this.maxHealth = maxHealth; }
    public int getMana() { return mana; }
    public int getMaxMana() { return maxMana; }
    public void setMaxMana(int maxMana) { this.maxMana = maxMana; }

    // Combat management methods
    public boolean isInCombat() { return inCombat; }
    public void setInCombat(boolean inCombat) { this.inCombat = inCombat; }
    public String getTarget() { return target; }
    public long getStartTime() { return startTime; }
    public long getCombatDuration() { return combatDuration; }

    // Convenience setters
    public void setTarget(String target) { this.target = target; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    // Combat statistics
    public void addDamageDealt(int amount) { this.damageDealt += amount; }
    public void addDamageTaken(int amount) { this.damageTaken += amount; }
    public void incrementBlock() { this.blocksSuccessful++; }
    public void incrementKillCount() { this.killCount++; }

    public int getKillCount() { return killCount; }
    public int getDamageDealt() { return damageDealt; }
    public int getDamageTaken() { return damageTaken; }
    public int getBlocksSuccessful() { return blocksSuccessful; }

    // Combat context
    public void setHostileEntitiesNearby(int count) { this.hostileEntitiesNearby = count; }
    public int getHostileEntitiesNearby() { return hostileEntitiesNearby; }
    public void setLastDamageSource(String source) { this.lastDamageSource = source; }
    public String getLastDamageSource() { return lastDamageSource; }
    public long getLastDamageTime() { return lastDamageTime; }

    public float getDPS() {
        if (combatDuration > 0) {
            return (float) damageDealt / (combatDuration / 1000.0f);
        }
        return 0;
    }

    // Reset statistics
    public void resetStats() {
        killCount = 0;
        damageDealt = 0;
        damageTaken = 0;
        blocksSuccessful = 0;
    }

    /**
     * Add damage dealt (for combo tracking)
     */
    public void addDamage(int amount) {
        this.damageDealt += amount;
    }

    /**
     * Increment combo counter
     */
    public void incrementCombo() {
        // Could be enhanced with combo timing logic
        addDamage(100);
    }

    /**
     * Get combat summary for telemetry
     */
    public String getCombatSummary() {
        return String.format("[COMBAT] kills=%d dealt=%d taken=%d dps=%.1f duration=%dms hp=%d/%d (%.0f%%)",
            killCount, damageDealt, damageTaken, getDPS(), combatDuration,
            health, maxHealth, getHealthPercentage());
    }
}
