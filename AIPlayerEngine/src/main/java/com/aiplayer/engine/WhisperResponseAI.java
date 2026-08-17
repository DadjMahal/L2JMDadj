package com.aiplayer.engine;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class WhisperResponseAI {
    private static final Logger LOGGER = Logger.getLogger(WhisperResponseAI.class.getName());
    private final Set<String> friends = new HashSet<>();

    public static String generateResponse(String sender, String message, boolean isFriend, int relationLevel) {
        if (!isFriend) return null; // No response to strangers

        if (message.toLowerCase().contains("trade")) return "Sure, what do you offer?";
        if (message.toLowerCase().contains("help")) return "What do you need?";
        if (message.toLowerCase().contains("party")) return "I'm interested!";
        if (relationLevel > 50) return "Hey! How's it going?";
        return "Hello!";
    }

    public static boolean shouldRespond(String sender, boolean isFriend, long lastMessageTime) {
        if (!isFriend) return false;
        return System.currentTimeMillis() - lastMessageTime < 300000; // 5 min window
    }
}
