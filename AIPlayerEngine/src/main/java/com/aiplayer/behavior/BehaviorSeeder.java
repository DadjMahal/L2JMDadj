package com.aiplayer.behavior;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

public class BehaviorSeeder {
    private static final Logger LOGGER = Logger.getLogger(BehaviorSeeder.class.getName());
    private final Random seedGenerator = new Random();

    public enum BehaviorStyle { CAUTIOUS, NORMAL, AGGRESSIVE, PLAYFUL, SERIOUS }

    public static Map<BehaviorStyle, Double> getDefaults() {
        Map<BehaviorStyle, Double> defaults = new HashMap<>();
        defaults.put(BehaviorStyle.CAUTIOUS, 0.3);
        defaults.put(BehaviorStyle.NORMAL, 0.5);
        defaults.put(BehaviorStyle.AGGRESSIVE, 0.7);
        defaults.put(BehaviorStyle.PLAYFUL, 0.4);
        defaults.put(BehaviorStyle.SERIOUS, 0.6);
        return defaults;
    }

    public BehaviorStyle seedUniqueBehavior(String playerId) {
        Random specific = new Random(playerId.hashCode());
        double roll = specific.nextDouble();
        if (roll < 0.2) return BehaviorStyle.CAUTIOUS;
        if (roll < 0.5) return BehaviorStyle.NORMAL;
        if (roll < 0.8) return BehaviorStyle.AGGRESSIVE;
        if (roll < 0.9) return BehaviorStyle.PLAYFUL;
        return BehaviorStyle.SERIOUS;
    }

    public double getVariance() {
        return 0.1 + seedGenerator.nextDouble() * 0.3;
    }
}
