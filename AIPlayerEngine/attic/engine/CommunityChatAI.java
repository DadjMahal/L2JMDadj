// package com.aiplayer.engine;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public class CommunityChatAI {
    private static final Logger LOGGER = Logger.getLogger(CommunityChatAI.class.getName());
    private final List<String> chatPatterns = new ArrayList<>();

    public void addChatPattern(String pattern) {
        chatPatterns.add(pattern.toLowerCase());
    }

    public boolean shouldJoinDiscussion(String message, String channel) {
        String lowerMsg = message.toLowerCase();
        for (String pattern : chatPatterns) {
            if (lowerMsg.contains(pattern)) return true;
        }
        return channel.equals("general") || channel.equals("help");
    }

    public String generateResponse(String trigger) {
        Random rand = new Random();
        String[] responses = {"Agreed!", "Good point!", "Thanks for the info!", "I didn't know that"};
        return responses[rand.nextInt(responses.length)];
    }
}
