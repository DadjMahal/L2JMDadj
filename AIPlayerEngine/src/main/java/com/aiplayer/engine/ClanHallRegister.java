package com.aiplayer.engine;
import java.util.logging.Logger;

public class ClanHallRegister {
    private static final Logger LOGGER = Logger.getLogger(ClanHallRegister.class.getName());
    
    public enum HallType { OWNERSHIP, MERCHANT, WAR_TEAR, HIDDEN_VILLAGE }
    
    public static boolean canAffordRegistration(int clanLevel, int adena) {
        return clanLevel >= 3 && adena > 50000000;
    }
    
    public static int getRegistrationCost(HallType type) {
        switch (type) {
            case OWNERSHIP: return 100000000;
            case MERCHANT: return 30000000;
            case WAR_TEAR: return 80000000;
            case HIDDEN_VILLAGE: return 500000000;
            default: return 0;
        }
    }
}
