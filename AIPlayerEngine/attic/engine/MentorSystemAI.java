// package com.aiplayer.engine;
import java.util.logging.Logger;

public class MentorSystemAI {
    private static final Logger LOGGER = Logger.getLogger(MentorSystemAI.class.getName());

    public static boolean canBeMentor(int level) { return level > 50; }
    public static boolean canBeMentee(int level) { return level < 30; }

    public static String[] getMentorRewards(int sessions) {
        if (sessions > 10) return new String[]{"Title", "Skill", "Pet"};
        if (sessions > 5) return new String[]{"Gold", "Items"};
        return new String[]{"Experience"};
    }

    public static boolean shouldMentor(String className, boolean isNew) {
        return !className.equals("Pet") && isNew;
    }
}
