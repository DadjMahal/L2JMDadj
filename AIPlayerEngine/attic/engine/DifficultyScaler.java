// package com.aiplayer.engine;
import java.util.logging.Logger;

public class DifficultyScaler {
    private static final Logger LOGGER = Logger.getLogger(DifficultyScaler.class.getName());

    public enum Difficulty { EASY, NORMAL, HARD, NIGHTMARE, CUSTOM }

    public static Difficulty scaleFromPerformance(double successRate, double uptime) {
        if (successRate > 0.95 && uptime > 0.98) return Difficulty.NIGHTMARE;
        if (successRate > 0.85) return Difficulty.HARD;
        if (successRate > 0.70) return Difficulty.NORMAL;
        return Difficulty.EASY;
    }

    public static double adjustAggression(Difficulty diff) {
        switch (diff) {
            case EASY: return 0.6;
            case NORMAL: return 1.0;
            case HARD: return 1.4;
            case NIGHTMARE: return 2.0;
            default: return 1.0;
        }
    }
}
