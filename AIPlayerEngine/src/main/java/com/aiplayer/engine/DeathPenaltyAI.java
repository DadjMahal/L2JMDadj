package com.aiplayer.engine;
import java.util.logging.Logger;

public class DeathPenaltyAI {
    private static final Logger LOGGER = Logger.getLogger(DeathPenaltyAI.class.getName());

    public static int calculateDeathPenalty(int level, int deathsToday, boolean isHero) {
        double basePenalty = 0.02 * level; // 2% per level
        double dailyBonus = deathsToday * 0.005; // Extra penalty for dying
        if (isHero) basePenalty *= 0.5; // Heroes get reduced penalty
        return (int)(basePenalty + dailyBonus);
    }

    public static boolean shouldAcceptDeath(int xpValue, int penalty) {
        return xpValue > penalty; // Worth more than penalty
    }

    public static String getDeathPenaltyInfo(int level, int deaths, boolean hero) {
        int penalty = calculateDeathPenalty(level, deaths, hero);
        return "Death penalty: " + penalty + " XP (level " + level + ", " + deaths + " today)";
    }
}
