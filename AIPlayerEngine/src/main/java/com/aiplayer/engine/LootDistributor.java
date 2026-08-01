package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;
public class LootDistributor {
    private static final Logger LOGGER = Logger.getLogger(LootDistributor.class.getName());
    private int turn = 0;
    public String getNextLooter(String[] party) {
        String loser = party[turn % party.length];
        turn++;
        return loser;
    }
}
