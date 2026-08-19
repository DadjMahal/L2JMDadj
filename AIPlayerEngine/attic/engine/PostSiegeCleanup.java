// package com.aiplayer.engine;
import java.util.logging.Logger;

public class PostSiegeCleanup {
    private static final Logger LOGGER = Logger.getLogger(PostSiegeCleanup.class.getName());

    public static String[] getCleanupItems(boolean won) {
        if (won) {
            return new String[]{"Castle Items", "Gold", "Scrolls", "Equipment"};
        }
        return new String[]{"Salvaged Materials", "Minor Loot"};
    }

    public static boolean shouldLeaveSafely(boolean won, int friendlyCount) {
        if (won) return friendlyCount > 0;
        return true;
    }
}
