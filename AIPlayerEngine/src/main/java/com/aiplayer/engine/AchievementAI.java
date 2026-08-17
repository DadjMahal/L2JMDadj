package com.aiplayer.engine;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class AchievementAI {
    private static final Logger LOGGER = Logger.getLogger(AchievementAI.class.getName());
    private final Set<String> completedAchievements = new HashSet<>();

    public static class Achievement {
        public final String id;
        public final String name;
        public final int requiredCompletion;

        public Achievement(String id, String name, int req) {
            this.id = id; this.name = name; requiredCompletion = req;
        }
    }

    public boolean hasAchievement(String id) {
        return completedAchievements.contains(id);
    }

    public void completeAchievement(String id) {
        completedAchievements.add(id);
        LOGGER.info("Achievement completed: " + id);
    }

    public String[] getRecommendedAchievements(int level) {
        if (level < 30) return new String[]{"BEGINNER", "QUEST beginner"};
        if (level < 60) return new String[]{"ADVENTURER", "KILL_BOSS"};
        if (level < 90) return new String[]{"WARRIOR", "CASTLE_SIEGE"};
        return new String[]{"LEGENDARY", "KILL_ALL_BOSSES"};
    }
}
