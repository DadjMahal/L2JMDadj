package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class CastleStrategy {
    private static final Logger LOGGER = Logger.getLogger(CastleStrategy.class.getName());

    public enum Strategy { OFFENSE, DEFENSE, CONCEALED, RAID }
    public enum CastleType { GLUDIO, DIOR, GEFFEN, INNLG, DION, GIRAN, HEINE, SCHOTT, CITY }

    public static Strategy selectStrategy(CastleType castle, int castleLevel, boolean isAlly, int enemyCount) {
        if (isAlly) return Strategy.OFFENSE;
        if (enemyCount > 20) return Strategy.DEFENSE;
        if (castleLevel > 5) return Strategy.RAID;
        return Strategy.CONCEALED;
    }

    public static String[] getRecommendedTroops(Strategy strategy, int castleLevel) {
        switch (strategy) {
            case OFFENSE: return new String[]{"EliteSoldiers", "Archers", "Healers"};
            case DEFENSE: return new String[]{"Defenders", "BoundaryWatchers", "Healers"};
            case CONCEALED: return new String[]{"Assassins", "Scouts", "Snipers"};
            case RAID: return new String[]{"EliteSoldiers", "Artillery", "Healers"};
            default: return new String[]{"Defenders"};
        }
    }

    public static boolean shouldAbandon(CastleType castle, int damagePercent) {
        return damagePercent > 80;
    }
}
