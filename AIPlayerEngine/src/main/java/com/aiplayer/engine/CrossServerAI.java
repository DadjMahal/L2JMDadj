package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class CrossServerAI {
    private static final Logger LOGGER = Logger.getLogger(CrossServerAI.class.getName());
    private final Map<String, Integer> serverStats = new HashMap<>();

    public void reportServerStatus(String server, int players, int aiCount, long uptime) {
        serverStats.put(server, aiCount);
        LOGGER.info(String.format("Server %s: %d AI, %d players, %d uptime", server, aiCount, players, uptime));
    }

    public boolean shouldMigrate(int currentLoad, int alternativeLoad) {
        return currentLoad > 80 && alternativeLoad < 50;
    }

    public String selectBestServer(String[] servers) {
        String best = null;
        int minAi = Integer.MAX_VALUE;
        for (String server : servers) {
            int ai = serverStats.getOrDefault(server, 0);
            if (ai < minAi) {
                minAi = ai;
                best = server;
            }
        }
        return best;
    }
}
