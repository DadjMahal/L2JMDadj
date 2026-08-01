package com.aiplayer.engine;
import java.util.logging.Logger;

public class ClanHallDefense {
    private static final Logger LOGGER = Logger.getLogger(ClanHallDefense.class.getName());
    
    public static boolean shouldDefendHall(int hallType, int attackers, int defenders) {
        if (hallType == 1) return attackers > defenders;
        return false;
    }
}
