package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class VendettaTracker {
    private static final Logger LOGGER = Logger.getLogger(VendettaTracker.class.getName());
    private Map<String, Integer> vendettaCount = new HashMap<>();

    public void recordGrief(String playerName) {
        vendettaCount.merge(playerName, 1, Integer::sum);
    }
    public boolean hasVendetta(String playerName) {
        return vendettaCount.getOrDefault(playerName, 0) >= 3;
    }
    public int getVendettaCount(String playerName) { return vendettaCount.getOrDefault(playerName, 0); }
}
