package com.aiplayer.engine;
import java.util.logging.Logger;

public class CastleTaxPlanner {
    private static final Logger LOGGER = Logger.getLogger(CastleTaxPlanner.class.getName());

    public static int calculateOptimalTax(int castleLevel, int taxRate, int population) {
        int base = 10 + (castleLevel * 2);
        int populationFactor = Math.min(population / 100, 15);
        return base + populationFactor;
    }

    public static boolean shouldLowerTax(int recentRaids, int taxRate) {
        return recentRaids > 3 && taxRate > 5;
    }
}
