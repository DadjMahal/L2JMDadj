package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class SiegeBuffCoordinator {
    private static final Logger LOGGER = Logger.getLogger(SiegeBuffCoordinator.class.getName());

    public enum BuffType { WAR_CRY, BLOOD_ANSER, FOCUS_CONCENTRATION, VITALITY }

    public static class BuffAssignment {
        public final BuffType buff;
        public final String targetClass;
        public final int priority;
        public final long cooldown;

        public BuffAssignment(BuffType b, String tc, int p, long c) {
            buff = b; targetClass = tc; priority = p; cooldown = c;
        }
    }

    public static BuffAssignment[] getSiegeBuffs(String leaderClass, boolean isDefender) {
        if (isDefender) {
            return new BuffAssignment[] {
                new BuffAssignment(BuffType.WAR_CRY, "Warrior", 1, 300000),
                new BuffAssignment(BuffType.BLOOD_ANSER, "Cleric", 2, 0),
                new BuffAssignment(BuffType.VITALITY, "All", 3, 180000)
            };
        }
        return new BuffAssignment[] {
            new BuffAssignment(BuffType.FOCUS_CONCENTRATION, "Wizard", 1, 0),
            new BuffAssignment(BuffType.BLOOD_ANSER, "Cleric", 2, 0),
            new BuffAssignment(BuffType.WAR_CRY, "Warrior", 3, 300000)
        };
    }

    public static boolean shouldCast(BuffType buff, long sinceLastCast, boolean isInCombat) {
        if (!isInCombat) return false;
        if (buff == BuffType.WAR_CRY && sinceLastCast < 300000) return false;
        return true;
    }
}
