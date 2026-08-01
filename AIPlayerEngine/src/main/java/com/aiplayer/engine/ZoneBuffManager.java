package com.aiplayer.engine;
import java.util.logging.Logger;

public class ZoneBuffManager {
    private static final Logger LOGGER = Logger.getLogger(ZoneBuffManager.class.getName());
    private boolean chaosActive = false;
    private boolean blessingActive = false;
    
    public void applyBuff(String buffType) {
        if ("chaos".equalsIgnoreCase(buffType)) { chaosActive = true; }
        if ("blessing".equalsIgnoreCase(buffType)) { blessingActive = true; }
    }
    
    public boolean shouldFight() { return chaosActive; }
    public boolean isProtected() { return blessingActive; }
}
