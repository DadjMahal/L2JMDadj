// package com.aiplayer.engine;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class PvPVendettaAI {
    private static final Logger LOGGER = Logger.getLogger(PvPVendettaAI.class.getName());
    private final Map<String, Integer> vendettaScore = new HashMap<>();

    public void addVendetta(String playerName) {
        vendettaScore.merge(playerName, 100, Integer::sum);
    }

    public boolean shouldAttack(String playerName) {
        return vendettaScore.getOrDefault(playerName, 0) > 150;
    }

    public void reduceVendetta(String playerName) {
        vendettaScore.merge(playerName, -50, Math::max);
    }
}
