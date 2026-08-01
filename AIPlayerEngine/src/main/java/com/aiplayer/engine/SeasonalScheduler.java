package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class SeasonalScheduler {
    private static final Logger LOGGER = Logger.getLogger(SeasonalScheduler.class.getName());
    
    public static Map<ManorAI.ManorSeason, Integer> getSeasonModifier() {
        Map<ManorAI.ManorSeason, Integer> mods = new HashMap<>();
        mods.put(ManorAI.ManorSeason.SPRING, 120);  // Best growth
        mods.put(ManorAI.ManorSeason.SUMMER, 100);  // Normal
        mods.put(ManorAI.ManorSeason.AUTUMN, 150);  // Harvest bonus
        mods.put(ManorAI.ManorSeason.WINTER, 80);   // Slow growth
        return mods;
    }
    
    public static int calculateAdjustedProduction(int base, ManorAI.ManorSeason season) {
        return (int)(base * getSeasonModifier().getOrDefault(season, 100) / 100.0);
    }
    
    public static String getOptimalPlantingTime(ManorAI.ManorSeason season) {
        switch (season) {
            case SPRING: return "NOW";
            case SUMMER: return "DELAY";
            case AUTUMN: return "HARVEST";
            case WINTER: return "STORAGE";
            default: return "CHECK";
        }
    }
}
