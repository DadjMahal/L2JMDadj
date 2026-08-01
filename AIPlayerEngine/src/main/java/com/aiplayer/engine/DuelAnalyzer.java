package com.aiplayer.engine;
import java.util.logging.Logger;

public class DuelAnalyzer {
    private static final Logger LOGGER = Logger.getLogger(DuelAnalyzer.class.getName());
    
    public enum DuelFormat { DUEL_1V1, DUEL_2V2, DUEL_4V4 }
    
    public static class ClassAbility {
        public final String className;
        public final String[] strengths;
        public final String[] weaknesses;
        public final String recommendedCounter;
        
        public ClassAbility(String name, String[] strs, String[] weaks, String counter) {
            className = name; strengths = strs; weaknesses = weaks; recommendedCounter = counter;
        }
    }
    
    public static String getClassRecommendation(DuelFormat format) {
        switch (format) {
            case DUEL_1V1: return "Warrior or Rogue";
            case DUEL_2V2: return "Tank + Healer";
            case DUEL_4V4: return "Optimal party composition varies";
            default: return "Warrior";
        }
    }
}
