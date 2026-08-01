package com.aiplayer.engine;
import java.util.logging.Logger;

public class SiegeTiming {
    private static final Logger LOGGER = Logger.getLogger(SiegeTiming.class.getName());
    
    public enum SiegePhase { PREPARATION, REGISTRATION, ENGAGE, REINFORCEMENTS, FINAL_BREACH, VICTORY, SAFE_EXIT }
    
    public static SiegePhase determinePhase(long siegeStartTime, long now, int damage) {
        long elapsed = now - siegeStartTime;
        if (elapsed < 3600000) return SiegePhase.PREPARATION;
        if (damage < 20) return SiegePhase.ENGAGE;
        if (damage < 50) return SiegePhase.REINFORCEMENTS;
        if (damage < 80) return SiegePhase.FINAL_BREACH;
        if (damage >= 100) return SiegePhase.VICTORY;
        return SiegePhase.ENGAGE;
    }
    
    public static boolean shouldReinforce(SiegePhase phase) {
        return phase == SiegePhase.REINFORCEMENTS;
    }
}
