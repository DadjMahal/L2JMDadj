package com.aiplayer.engine;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ManorAI {
    private static final Logger LOGGER = Logger.getLogger(ManorAI.class.getName());

    public enum SeedType {
        LOW_LEVEL(1, 100),
        MID_LEVEL(2, 500),
        HIGH_LEVEL(3, 2000),
        EXPERT_LEVEL(4, 10000);

        public final int seedId;
        public final int production;

        SeedType(int id, int prod) { seedId = id; production = prod; }
    }

    public enum ManorSeason { SPRING, SUMMER, AUTUMN, WINTER }

    public static class PlantingPlan {
        public final List<SeedType> seeds;
        public final int soilQuality;
        public final int totalProduction;

        public PlantingPlan(List<SeedType> seeds, int quality) {
            this.seeds = seeds; this.soilQuality = quality;
            int prod = 0;
            for (SeedType s : seeds) prod += s.production;
            this.totalProduction = prod * quality / 100;
        }
    }

    public PlantingPlan generateOptimalPlan(int availableAdena, int soilLevel, ManorSeason season) {
        List<SeedType> plan = new ArrayList<>();
        int budget = availableAdena;
        SeedType best = SeedType.values()[SeedType.values().length - 1];
        for (int i = 0; i < 5; i++) {
            if (budget >= 10000) { plan.add(best); budget -= 10000; }
            else if (budget >= 1000) { plan.add(SeedType.MID_LEVEL); budget -= 1000; }
            else break;
        }
        return new PlantingPlan(plan, soilLevel);
    }

    public static boolean shouldReplant(ManorSeason current, ManorSeason previous) {
        return current != previous;
    }
}
