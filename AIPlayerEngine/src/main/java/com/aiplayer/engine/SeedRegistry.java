package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class SeedRegistry {
    private static final Logger LOGGER = Logger.getLogger(SeedRegistry.class.getName());

    public enum CropType {
        GRASS_SEED(1001, 100, 1.5, 10),   // id, price, yield, growthRate
        BROKEN_GRASS_SEED(1002, 50, 1.0, 5),
        COMPLEX_GRASS_SEED(1003, 200, 2.0, 15),
        ELITE_SEED(1004, 1000, 3.0, 30),
        ANCIENT_SEED(1005, 5000, 5.0, 60);

        public final int seedId;
        public final int price;
        public final double yieldFactor;
        public final int growthDays;

        CropType(int id, int price, double yield, int growth) {
            seedId = id; this.price = price; yieldFactor = yield; growthDays = growth;
        }
    }

    public static CropType getBestCrop(int playerLevel, int castleLevel) {
        if (playerLevel > 70 && castleLevel > 5) return CropType.ANCIENT_SEED;
        if (playerLevel > 50) return CropType.ELITE_SEED;
        if (playerLevel > 30) return CropType.COMPLEX_GRASS_SEED;
        return CropType.GRASS_SEED;
    }

    public static int getExpectedHarvest(CropType crop, int quantity, int soilLevel) {
        return (int)(quantity * crop.yieldFactor * (1 + soilLevel/100.0));
    }
}
