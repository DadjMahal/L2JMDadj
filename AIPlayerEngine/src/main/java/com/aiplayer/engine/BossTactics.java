package com.aiplayer.engine;
import java.util.logging.Logger;
public class BossTactics {
    private static final Logger LOGGER = Logger.getLogger(BossTactics.class.getName());
    public static String[] getTankPositions() { return new String[]{"north", "south"}; }
    public static String getDPSRole() { return "burst"; }
    public static String getHealPosition() { return "central"; }
    public static boolean shouldReleaseNPCDialog() { return true; }
}
