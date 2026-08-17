package com.aiplayer.engine;
import java.util.logging.Logger;

public class FishingTournamentAI {
    private static final Logger LOGGER = Logger.getLogger(FishingTournamentAI.class.getName());

    public enum BaitType {
        BASIC_BAIT(1), SWEET_BERRY_BAIT(2), GRAPEFUIT_BAIT(3),
        HONEY_BASTON_BAIT(4), DRAGON_FLOWER_BAIT(5);

        public final int multiplier;
        BaitType(int m) { multiplier = m; }
    }

    public static BaitType selectBestBait(int fishLevel, int timeRemaining) {
        if (timeRemaining < 60) return BaitType.BASIC_BAIT;
        if (fishLevel > 80) return BaitType.DRAGON_FLOWER_BAIT;
        if (fishLevel > 60) return BaitType.HONEY_BASTON_BAIT;
        if (fishLevel > 40) return BaitType.GRAPEFUIT_BAIT;
        return BaitType.SWEET_BERRY_BAIT;
    }

    public static int calculateScore(int fishLevel, BaitType bait, int timeBonus) {
        return (int)(fishLevel * bait.multiplier * (1 + timeBonus / 100.0));
    }

    public static boolean shouldRaiseBid(String baitName) { return true; }
}
