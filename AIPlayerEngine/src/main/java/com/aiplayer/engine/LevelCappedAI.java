package com.aiplayer.engine;
import java.util.logging.Logger;

public class LevelCappedAI {
    private static final Logger LOGGER = Logger.getLogger(LevelCappedAI.class.getName());
    
    public enum PlayStyle { TWIN, ALT_MAIN, SOLO_LV, RAID_LV }
    
    public static PlayStyle determineStyle(int maxLevel, int currentLevel) {
        if (currentLevel < maxLevel / 2) return PlayStyle.TWIN;
        if (currentLevel < maxLevel * 0.9) return PlayStyle.ALT_MAIN;
        return PlayStyle.RAID_LV;
    }
    
    public static String[] getBehaviors(PlayStyle style) {
        switch (style) {
            case TWIN: return new String[]{"CONSERVATIVE", "SAFE"};
            case ALT_MAIN: return new String[]{"CASUAL", "SUPPORT"};
            case SOLO_LV: return new String[]{"SOLO_FOCUSED", "GEAR_GRIND"};
            case RAID_LV: return new String[]{"GROUP_ORIENTED", "PROGRESS"};
            default: return new String[]{"NORMAL"};
        }
    }
}
