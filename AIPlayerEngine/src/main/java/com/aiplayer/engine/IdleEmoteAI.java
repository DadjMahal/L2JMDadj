package com.aiplayer.engine;
import java.util.logging.Logger;

public class IdleEmoteAI {
    private static final Logger LOGGER = Logger.getLogger(IdleEmoteAI.class.getName());
    private int idleTicks = 0;

    public enum Emote { NONE, WAVE, GREET, LAUGH, THINK, SCARED, ANGRY, HAPPY, SAD }

    public static Emote suggestEmote(int idleTime, Emote currentMood) {
        if (idleTime > 300) return Emote.WAVE;
        if (idleTime > 180) return currentMood;
        return Emote.NONE;
    }

    public static boolean shouldSocialize(int idleTime, boolean inTown, int nearbyPlayers) {
        if (!inTown) return false;
        return idleTime > 120 && nearbyPlayers > 0;
    }

    public static String[] getSocialEmotes() {
        return new String[]{"wave", "hello", "praise", "agree", "laugh"};
    }
}
