package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class HumanBehaviorSimulator {
    private static final Logger LOGGER = Logger.getLogger(HumanBehaviorSimulator.class.getName());
    private final Random random = new Random();
    
    public static class ReactionTiming {
        public final long minDelay;
        public final long maxDelay;
        
        public ReactionTiming(long min, long max) { minDelay = min; maxDelay = max; }
    }
    
    public long getHumanReaction() {
        // 150-300ms human reaction + 50-100ms thinking time
        return 150 + random.nextInt(150) + 50 + random.nextInt(100);
    }
    
    public boolean shouldEmote(int activityLevel) {
        return activityLevel > 30 && random.nextDouble() < 0.1;
    }
    
    public String getEmote() {
        String[] emotes = {"hello", "yes", "no", "attack", "relax"};
        return emotes[random.nextInt(emotes.length)];
    }
}
