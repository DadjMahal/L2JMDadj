// package com.aiplayer.engine;
import java.util.logging.Logger;

public class HuntingPreferenceAI {
    private static final Logger LOGGER = Logger.getLogger(HuntingPreferenceAI.class.getName());

    public enum HuntingStyle { AGGRESSIVE, CAREFUL, STEALTHY, METHODICAL }
    public enum ZonePreference { MONSTER_INNER, MONSTER_OUTER, RAID_ARENA, SEASONAL }

    public static ZonePreference predictHuntingZone(HuntingStyle style, int level, int zoneId) {
        if (level < 30) return ZonePreference.MONSTER_INNER;
        if (level < 60) return ZonePreference.MONSTER_OUTER;
        if (level < 90) return ZonePreference.RAID_ARENA;
        return ZonePreference.SEASONAL;
    }

    public static String[] getRecommendedHunts(int level, HuntingStyle style) {
        if (level < 30) return new String[]{"Low Level Dungeons", "Tutorial Areas"};
        if (level < 60) return new String[]{"Mid Level Zones", "Hunting Grounds"};
        if (level < 90) return new String[]{"High Level Zones", "Raid Areas"};
        return new String[]{"Epic Raids", "World Bosses"};
    }
}
