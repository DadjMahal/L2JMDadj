package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class VitalityManager {
    private static final Logger LOGGER = Logger.getLogger(VitalityManager.class.getName());
    
    public static class VitalityState {
        public int bonusCount;
        public long lastReset;
        public boolean isActive;
        
        public VitalityState() { bonusCount = 0; lastReset = System.currentTimeMillis(); isActive = false; }
    }
    
    public static boolean shouldResetVitality(long lastReset, int bonusCount) {
        return (System.currentTimeMillis() - lastReset) > 86400000 || bonusCount > 12; // 24 hours or max bonuses
    }
    
    public static int getVitalityBonus(int bonusCount, int level) {
        int percent = 10 + bonusCount * 2; // 10-34% bonus
        return (int)(level * percent / 100.0);
    }
    
    public static boolean shouldActivate(VitalityState state, int currentLevelGain) {
        return state.bonusCount > 5 && currentLevelGain > 1000;
    }
}
