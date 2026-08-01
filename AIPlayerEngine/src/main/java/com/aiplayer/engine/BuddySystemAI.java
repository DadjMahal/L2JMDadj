package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class BuddySystemAI {
    private static final Logger LOGGER = Logger.getLogger(BuddySystemAI.class.getName());
    private final Set<String> buddyList = new HashSet<>();
    
    public static class Buddy {
        public final String name;
        public final int level;
        public final long lastOnline;
        
        public Buddy(String n, int l, long online) { name = n; level = l; lastOnline = online; }
    }
    
    public void addBuddy(String playerName, int level) {
        buddyList.add(playerName);
        LOGGER.info("Added buddy: " + playerName);
    }
    
    public boolean shouldHelpBuddy(String buddyName, int buddyLevel, int ownLevel) {
        return buddyList.contains(buddyName) && Math.abs(ownLevel - buddyLevel) <= 10;
    }
    
    public String[] getOnlineBuddies() {
        return buddyList.stream().toArray(String[]::new);
    }
}
