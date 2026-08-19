// package com.aiplayer.engine;
import java.util.logging.Logger;

public class HeroTitleOptimizer {
    private static final Logger LOGGER = Logger.getLogger(HeroTitleOptimizer.class.getName());

    public static boolean shouldCompete(int winRate, int currentScore, int targetScore) {
        return winRate > 50 && (targetScore - currentScore) > 100;
    }

    public static String selectBestTime(int timeOfDay) {
        if (timeOfDay >= 18 && timeOfDay <= 22) return "PRIME_HERO_HOURS";
        return "NORMAL_HOURS";
    }
}
