package com.aiplayer.engine;
import java.util.logging.Logger;

public class RangedKiteAI {
    private static final Logger LOGGER = Logger.getLogger(RangedKiteAI.class.getName());

    public static double calculateOptimalDistance(int enemyRange) {
        return enemyRange + 50; // Stay 50 units outside enemy range
    }

    public static boolean shouldKite(int healthPercent, int enemyDistance, int maxRange) {
        return healthPercent < 30 && enemyDistance > maxRange - 100;
    }

    public static String[] getKitePattern() {
        return new String[]{"STRAFE_LEFT", "STEP_BACK", "STRAFE_RIGHT", "HEAL"};
    }
}
