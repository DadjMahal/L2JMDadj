package com.aiplayer.engine;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class VendettaTrackingAI {
    private static final Logger LOGGER = Logger.getLogger(VendettaTrackingAI.class.getName());
    private final Map<String, Integer> vendettaCount = new HashMap<>();

    public static class VendettaRecord {
        public final String playerName;
        public final int count;
        public final long lastIncident;

        public VendettaRecord(String name, int count, long time) {
            playerName = name; this.count = count; lastIncident = time;
        }
    }

    public void recordIncident(String playerName) {
        vendettaCount.merge(playerName, 1, Integer::sum);
    }

    public boolean shouldSeekRevenge(String playerName) {
        return vendettaCount.getOrDefault(playerName, 0) >= 3;
    }

    public VendettaRecord getRecord(String playerName) {
        return new VendettaRecord(playerName, vendettaCount.getOrDefault(playerName, 0), System.currentTimeMillis());
    }
}
