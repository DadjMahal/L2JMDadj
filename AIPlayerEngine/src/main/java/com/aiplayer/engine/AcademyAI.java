package com.aiplayer.engine;
import java.util.logging.Logger;

public class AcademyAI {
    private static final Logger LOGGER = Logger.getLogger(AcademyAI.class.getName());

    public enum RelationshipStatus { NEW, TRAINING, GRADUATED, MASTER }

    public static class MentorInfo {
        public final String mentorName;
        public final RelationshipStatus status;
        public final int trainingLevel;
        public final String[] assignedTasks;

        public MentorInfo(String name, RelationshipStatus stat, int level, String[] tasks) {
            mentorName = name; status = stat; trainingLevel = level; assignedTasks = tasks;
        }
    }

    public static boolean shouldAcceptMentor(String className, RelationshipStatus status) {
        return status == RelationshipStatus.NEW && !className.equals("Pet");
    }

    public static String[] getRecommendedTasks(int level, String className) {
        if (level < 20) return new String[]{"LEVEL_GRIND", "QUEST_COMPLETION"};
        if (className.equals("Siege") || className.equals("Raid")) return new String[]{"GEAR_UPGRADE", "SKILL_MASTERY"};
        return new String[]{"EXPERIENCE_GRIND", "DUNGEON_RUNS"};
    }
}
