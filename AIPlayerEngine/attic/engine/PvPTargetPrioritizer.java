// package com.aiplayer.engine;

/**
 * Task 99: PvP target prioritization - weakest enemy, healers first
 *
 * Prioritizes PvP targets based on threat level, class, health, and role.
 */
public class PvPTargetPrioritizer {
    public enum TargetPriority {
        HEALER(1.5),
        DPS(1.2),
        TANK(1.0),
        MAGIC(1.3);

        private final double multiplier;
        TargetPriority(double mult) { this.multiplier = mult; }
        public double getMultiplier() { return multiplier; }
    }

    public static class PvPTarget {
        public final String name;
        public final String className;
        public final int health;
        public final int maxHealth;
        public final int karma;

        public PvPTarget(String name, String className, int health, int maxHealth, int karma) {
            this.name = name; this.className = className;
            this.health = health; this.maxHealth = maxHealth; this.karma = karma;
        }

        public double getHealthPercent() {
            return maxHealth > 0 ? (double) health / maxHealth : 0;
        }

        public boolean isHealer() {
            return className.equals("Wizard") || className.equals("Cleric");
        }
    }

    public static double calculatePriority(PvPTarget target) {
        double score = 1.0;
        if (target.isHealer()) score *= 2.0;
        double hp = target.getHealthPercent();
        if (hp < 0.3) score *= 2.0;
        else if (hp < 0.5) score *= 1.5;
        if (target.karma < 0) score *= 1.2;
        return score;
    }
}
