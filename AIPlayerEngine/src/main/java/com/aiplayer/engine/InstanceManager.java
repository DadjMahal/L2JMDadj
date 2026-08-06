package com.aiplayer.engine;
import java.util.logging.Logger;
public class InstanceManager {
    private static final Logger LOGGER = Logger.getLogger(InstanceManager.class.getName());
    public static boolean shouldEnter(String instanceType, int partyLevel) { return partyLevel > 40; }
    public static void handleTimer(int minutesLeft) { if (minutesLeft < 5) LOGGER.info("LEAVE_INSTANCE"); }
}
