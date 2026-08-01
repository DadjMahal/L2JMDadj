package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class ClanMateAI {
    private static final Logger LOGGER = Logger.getLogger(ClanMateAI.class.getName());
    private final Set<String> clanMates = new HashSet<>();
    
    public static class GreetEvent {
        public final String playerName;
        public final String greeting;
        public final long timestamp;
        
        public GreetEvent(String name, String greet) {
            playerName = name; greeting = greet; timestamp = System.currentTimeMillis();
        }
    }
    
    public void greetClanMate(String playerName) {
        clanMates.add(playerName);
        LOGGER.info("Greeting: " + playerName);
    }
    
    public boolean shouldGreet(String playerName) {
        return clanMates.contains(playerName);
    }
}
