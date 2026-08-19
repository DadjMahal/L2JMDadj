// package com.aiplayer.engine;
import java.util.logging.Logger;
public class EscortAI {
    private static final Logger LOGGER = Logger.getLogger(EscortAI.class.getName());
    public static boolean shouldFollow(int distance) { return distance < 10 && distance > 0; }
    public static void handleInterruption() { LOGGER.info("ESCORT_INTERRUPTED"); }
}
