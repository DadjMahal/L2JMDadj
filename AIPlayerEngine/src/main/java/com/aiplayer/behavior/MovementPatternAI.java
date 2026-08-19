package com.aiplayer.behavior;
import java.util.logging.Logger;

public class MovementPatternAI {
    private static final Logger LOGGER = Logger.getLogger(MovementPatternAI.class.getName());

    public enum WalkStyle {
        CONFIDENT(0.7, 0.3),
        CAUTIOUS(0.4, 0.6),
        HURRIED(0.9, 0.8),
        CASUAL(0.5, 0.5),
        TERRIFIED(0.2, 0.9);

        public final double walkRatio;
        public final double runRatio;

        WalkStyle(double walk, double run) { walkRatio = walk; runRatio = run; }
    }

    public static double[] getMovementVector(double baseSpeed, WalkStyle style) {
        return new double[]{baseSpeed * style.walkRatio, baseSpeed * style.runRatio};
    }

    public static String getNextWaypoint(String current, WalkStyle style) {
        if (style == WalkStyle.CONFIDENT) return current + "_CONFIDENT";
        if (style == WalkStyle.CAUTIOUS) return current + "_CAUTION";
        if (style == WalkStyle.HURRIED) return current + "_HURRY";
        return current + "_NORMAL";
    }
}
