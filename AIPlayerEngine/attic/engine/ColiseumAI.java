// package com.aiplayer.engine;
import java.util.logging.Logger;
public class ColiseumAI {
    private static final Logger LOGGER = Logger.getLogger(ColiseumAI.class.getName());
    public static boolean shouldParticipate(int level, int teamSize) { return teamSize >= 3; }
    public static String selectClassRestrictions() { return "no_magic"; }
}
