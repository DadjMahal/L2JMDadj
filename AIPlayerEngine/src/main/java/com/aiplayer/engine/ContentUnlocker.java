package com.aiplayer.engine;
import java.util.logging.Logger;

public class ContentUnlocker {
    private static final Logger LOGGER = Logger.getLogger(ContentUnlocker.class.getName());

    public enum ContentLevel { BEGINNER, INTERMEDIATE, ADVANCED, EXPERT, LEGENDARY }

    public static class UnlockInfo {
        public final int level;
        public final String[] content;

        public UnlockInfo(int lvl, String[] cont) { level = lvl; content = cont; }
    }

    public static ContentLevel getContentLevel(int playerLevel) {
        if (playerLevel < 30) return ContentLevel.BEGINNER;
        if (playerLevel < 50) return ContentLevel.INTERMEDIATE;
        if (playerLevel < 70) return ContentLevel.ADVANCED;
        if (playerLevel < 90) return ContentLevel.EXPERT;
        return ContentLevel.LEGENDARY;
    }

    public static boolean shouldProgress(int currentLevel, ContentLevel contentLevel) {
        switch (contentLevel) {
            case BEGINNER: return currentLevel >= 20;
            case INTERMEDIATE: return currentLevel >= 40;
            case ADVANCED: return currentLevel >= 60;
            case EXPERT: return currentLevel >= 80;
            case LEGENDARY: return currentLevel >= 100;
            default: return false;
        }
    }

    public static String[] getAvailableContent(int level) {
        ContentLevel cl = getContentLevel(level);
        switch (cl) {
            case BEGINNER: return new String[]{"Basic Quests", "Tutorial Dungeons"};
            case INTERMEDIATE: return new String[]{"Intermediate Dungeons", "Basic PvP"};
            case ADVANCED: return new String[]{"Hard Dungeons", "Siege Participation"};
            case EXPERT: return new String[]{"Raid Bosses", "Clan Halls"};
            case LEGENDARY: return new String[]{"Epic Raids", "Castle Ownership"};
            default: return new String[]{"Basic Quests"};
        }
    }
}
