package com.aiplayer.engine;
import java.util.logging.Logger;

public class SoloGroupAI {
    private static final Logger LOGGER = Logger.getLogger(SoloGroupAI.class.getName());

    public static boolean shouldSolo(int level, String className, boolean hasParty) {
        return !hasParty && level < 40;
    }

    public static boolean shouldJoinGroup(int level, int partyLevelAvg, String className) {
        return level > partyLevelAvg - 10;
    }

    public static String getPreferredPlayStyle(int level, boolean isAllyPresent) {
        if (!isAllyPresent && level < 30) return "SOLO";
        if (!isAllyPresent && level >= 30) return "GROUP";
        return "GROUP";
    }
}
