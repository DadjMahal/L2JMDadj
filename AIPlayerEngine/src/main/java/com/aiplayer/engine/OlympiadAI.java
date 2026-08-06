package com.aiplayer.engine;

import java.util.logging.Logger;

/**
 * Task 101: Olympiad participation AI
 */
public class OlympiadAI {
    private static final Logger LOGGER = Logger.getLogger(OlympiadAI.class.getName());

    public static class OlympiadState {
        String playerName; int currentPoints; int targetPoints; int winStreak;
        public OlympiadState(String name, int target) {
            this.playerName = name; this.currentPoints = 0; this.targetPoints = target; this.winStreak = 0;
        }
    }

    public static boolean shouldRegister(OlympiadState state) {
        return state.currentPoints < state.targetPoints && state.winStreak < 5;
    }
}
