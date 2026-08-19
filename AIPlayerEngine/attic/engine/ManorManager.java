// package com.aiplayer.engine;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ManorManager {
    private static final Logger LOGGER = Logger.getLogger(ManorManager.class.getName());
    private final Map<Integer, ManorAI.PlantingPlan> plans = new HashMap<>();

    public void setPlantingPlan(int castleId, ManorAI.PlantingPlan plan) {
        plans.put(castleId, plan);
        LOGGER.info("ManorManager: Set plan for castle " + castleId + " (" + plan.totalProduction + " prod)");
    }

    public ManorAI.PlantingPlan getPlan(int castleId) { return plans.get(castleId); }

    public int getTotalProduction(int castleId, ManorAI.ManorSeason season) {
        ManorAI.PlantingPlan plan = plans.get(castleId);
        if (plan == null) return 0;
        return SeasonalScheduler.calculateAdjustedProduction(plan.totalProduction, season);
    }
}
