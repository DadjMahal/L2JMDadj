// package com.aiplayer.engine;
import java.util.logging.Logger;

public class SiegeExitManager {
    private static final Logger LOGGER = Logger.getLogger(SiegeExitManager.class.getName());

    public static boolean shouldCollectLoot(boolean won, int lootCount) {
        return won && lootCount > 0;
    }

    public static boolean shouldExitSafely(boolean won, boolean isCommander) {
        return won || !isCommander;
    }
}
