package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class NoblesseAI {
    private static final Logger LOGGER = Logger.getLogger(NoblesseAI.class.getName());

    public enum NoblesseStage { UNAVAILABLE, QUESTS_ACCEPTED, QUESTS_COMPLETED, ELIGIBLE, NOBLESSE }

    public static class NoblesseQuest {
        public final String questId;
        public final int requiredLevel;
        public final String[] prerequisites;

        public NoblesseQuest(String id, int lvl, String[] pre) {
            questId = id; requiredLevel = lvl; prerequisites = pre;
        }
    }

    public static boolean shouldAttemptNoblesse(int level, NoblesseStage stage, Set<String> completed) {
        if (stage != NoblesseStage.ELIGIBLE) return false;
        return level >= 180 && completed.contains("NOBLESSE_PART1") && completed.contains("NOBLESSE_PART2");
    }

    public static String[] getNoblesseQuests() {
        return new String[]{"NOBLESSE_PART1", "NOBLESSE_PART2", "NOBLESSE_PART3"};
    }
}
