package com.aiplayer.engine;
import java.util.logging.Logger;

public class AntiGriefing {
    private static final Logger LOGGER = Logger.getLogger(AntiGriefing.class.getName());
    private int griefLevel = 0;
    
    public boolean shouldGrieve() { return griefLevel < 3; }
    public void increaseGriefLevel() { griefLevel = Math.min(griefLevel + 1, 10); }
    public void resetGriefLevel() { griefLevel = 0; }
    public int getGriefLevel() { return griefLevel; }
}
