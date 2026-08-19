// package com.aiplayer.engine;
import java.util.logging.Logger;

public class ClanHallSiegeAI {
    private static final Logger LOGGER = Logger.getLogger(ClanHallSiegeAI.class.getName());

    public enum HallType { OWNERSHIP, MERCHANT, WAR_TEAR, HIDDEN_VILLAGE, ELYANS, ANGEL, REWARD }

    public static class SiegePlan {
        public final HallType hallType;
        public final int requiredPlayers;
        public final int estimatedDuration;
        public SiegePlan(HallType type, int players, int duration) {
            hallType = type; requiredPlayers = players; estimatedDuration = duration;
        }
    }

    public static SiegePlan getOptimalPlan(HallType hall, int clanLevel) {
        int players = Math.max(5, clanLevel * 2);
        int duration;
        switch (hall) {
            case OWNERSHIP: duration = 30; break;
            case MERCHANT: duration = 45; break;
            case WAR_TEAR: duration = 60; break;
            case HIDDEN_VILLAGE: duration = 120; break;
            default: duration = 60; break;
        }
        return new SiegePlan(hall, players, duration);
    }

    public static boolean shouldSiege(HallType hallType, long lastSiegeTime, int cooldownHours) {
        return System.currentTimeMillis() - lastSiegeTime > cooldownHours * 3600000L;
    }
}
