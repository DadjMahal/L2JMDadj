package com.aiplayer.engine;
import java.util.logging.Logger;

public class TaxCalculator {
    private static final Logger LOGGER = Logger.getLogger(TaxCalculator.class.getName());
    
    public static int calculateManorTax(int production, int taxRate, int castleLevel) {
        int base = production * taxRate / 100;
        int bonus = castleLevel * 5;  // Castle ownership bonus
        return base + bonus;
    }
    
    public static int calculateCastleTax(int dailyProduction, int taxRate, boolean hasFlag) {
        int tax = (int)(dailyProduction * taxRate / 100.0);
        if (hasFlag) tax += 5000;  // Flag bonus
        return Math.max(0, tax);
    }
    
    public static boolean shouldLowerTax(int recentLosses, int currentRate) {
        return recentLosses > 2 && currentRate > 10;
    }
}
