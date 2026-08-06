package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class ExpansionHooksAI {
    private static final Logger LOGGER = Logger.getLogger(ExpansionHooksAI.class.getName());
    private final Set<String> availableExansions = new HashSet<>();

    public void registerExpansion(String name) {
        availableExansions.add(name);
        LOGGER.info("Expansion registered: " + name);
    }

    public boolean isExpansionAvailable(String name) {
        return availableExansions.contains(name);
    }

    public void preloadExpansionContent(String name) {
        LOGGER.info("Preloading content for: " + name);
        // Future: load expansion-specific data
    }
}
