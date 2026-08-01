package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class BehavioralSimulator {
    private static final Logger LOGGER = Logger.getLogger(BehavioralSimulator.class.getName());
    private final Random random = new Random();
    
    public static class BehaviorProfile {
        public final String type;
        public final double[] probabilities;
        
        public BehaviorProfile(String type, double[] probs) {
            this.type = type; this.probabilities = probs;
        }
    }
    
    public BehaviorProfile generatePersonality(String classType) {
        double[] probs = new double[5];
        Arrays.fill(probs, 0.2); // Default
        
        switch (classType) {
            case "Knight": probs[0] = 0.4; probs[4] = 0.3; break; // Aggressive, Protective
            case "Wizard": probs[2] = 0.5; probs[1] = 0.3; break; // Explorers, Social
            case "Cleric": probs[4] = 0.6; probs[1] = 0.2; break; // Protective, Social
            case "Rogue": probs[0] = 0.5; probs[2] = 0.4; break; // Aggressive, Explorers
        }
        
        return new BehaviorProfile(classType, probs);
    }
    
    public String getNextAction(BehaviorProfile profile, int stress) {
        if (stress > 80) return "HIDE";
        if (stress > 50) return "CLEANSE";
        return "NORMAL";
    }
}
