package com.aiplayer.engine;

/**
 * Combat State Management
 * Tracks current combat status for AI players
 */
public class CombatState {
    private boolean inCombat;
    private String target;
    private long startTime;
    private int health;
    private int maxHealth;
    private int mana;
    private int maxMana;
    
    public CombatState() {
        this.inCombat = false;
        this.target = null;
        this.startTime = 0;
        this.health = 100;
        this.maxHealth = 100;
        this.mana = 100;
        this.maxMana = 100;
    }
    
    public boolean isInCombat() {
        return inCombat;
    }
    
    public void setInCombat(boolean inCombat) {
        this.inCombat = inCombat;
    }
    
    public String getTarget() {
        return target;
    }
    
    public void setTarget(String target) {
        this.target = target;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public int getHealth() {
        return health;
    }
    
    public void setHealth(int health) {
        this.health = health;
    }
    
    public int getMaxHealth() {
        return maxHealth;
    }
    
    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }
    
    public int getMana() {
        return mana;
    }
    
    public void setMana(int mana) {
        this.mana = mana;
    }
    
    public int getMaxMana() {
        return maxMana;
    }
    
    public void setMaxMana(int maxMana) {
        this.maxMana = maxMana;
    }
    
    public void incrementCombo() {
        // Placeholder for combo tracking
    }
}