package com.aiplayer.engine;
import java.util.logging.Logger;
public class HuntingRotation {
    private static final Logger LOGGER = Logger.getLogger(HuntingRotation.class.getName());
    public static String getOptimalHunt(int level) {
        if (level < 30) return "DARK_GARDENS";
        if (level < 50) return "MONSTER_TRACK";
        return "RAID_BOSS_ARENA";
    }
}
