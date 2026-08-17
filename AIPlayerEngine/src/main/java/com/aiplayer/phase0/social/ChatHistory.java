package com.aiplayer.phase0.social;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Thread-safe ring buffer of chat messages per AI Player.
 * Provides context for responses and spam detection.
 */
public final class ChatHistory {

    private static final int MAX_HISTORY = 200; // per channel aggregate
    private static final long CONTEXT_WINDOW_MS = 120000; // 2 min context window

    private final CopyOnWriteArrayList<ChatMessage> messages = new CopyOnWriteArrayList<>();
    private final String accountName;

    public ChatHistory(String accountName) {
        this.accountName = accountName;
    }

    public void add(ChatMessage msg) {
        messages.add(msg);
        trim();
    }

    public void addOutgoing(ChatMessage.Channel channel, String text) {
        add(new ChatMessage(accountName, channel, text, true));
    }

    public void addIncoming(String sender, ChatMessage.Channel channel, String text) {
        add(new ChatMessage(sender, channel, text, false));
    }

    public List<ChatMessage> getRecent(long withinMs) {
        long cutoff = System.currentTimeMillis() - withinMs;
        return messages.stream()
                .filter(m -> m.timestamp >= cutoff)
                .collect(Collectors.toList());
    }

    public List<ChatMessage> getRecentByChannel(ChatMessage.Channel channel, long withinMs) {
        long cutoff = System.currentTimeMillis() - withinMs;
        return messages.stream()
                .filter(m -> m.channel == channel && m.timestamp >= cutoff)
                .collect(Collectors.toList());
    }

    public List<ChatMessage> getRecentIncoming(long withinMs) {
        long cutoff = System.currentTimeMillis() - withinMs;
        return messages.stream()
                .filter(m -> !m.isOutgoing && m.timestamp >= cutoff)
                .collect(Collectors.toList());
    }

    public List<ChatMessage> getContextForResponse(String sender) {
        // Return last 5 messages involving this sender for contextual replies
        long cutoff = System.currentTimeMillis() - CONTEXT_WINDOW_MS;
        List<ChatMessage> context = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0 && context.size() < 5; i--) {
            ChatMessage m = messages.get(i);
            if (m.timestamp < cutoff) break;
            if (m.sender.equalsIgnoreCase(sender) || m.isOutgoing) {
                context.add(m);
            }
        }
        Collections.reverse(context);
        return context;
    }

    public boolean wasRecentlyAddressed(long withinMs) {
        long cutoff = System.currentTimeMillis() - withinMs;
        return messages.stream()
                .filter(m -> !m.isOutgoing && m.timestamp >= cutoff)
                .anyMatch(m -> m.text.toLowerCase().contains(accountName.toLowerCase()));
    }

    public int countOutgoingInWindow(ChatMessage.Channel channel, long windowMs) {
        long cutoff = System.currentTimeMillis() - windowMs;
        return (int) messages.stream()
                .filter(m -> m.isOutgoing && m.channel == channel && m.timestamp >= cutoff)
                .count();
    }

    public boolean isSpammyChannel(ChatMessage.Channel channel) {
        // Detect if we or others are spamming this channel (>5 msgs/min)
        long oneMinute = 60000;
        int outgoingCount = countOutgoingInWindow(channel, oneMinute);
        int incomingCount = (int) messages.stream()
                .filter(m -> !m.isOutgoing && m.channel == channel)
                .filter(m -> System.currentTimeMillis() - m.timestamp <= oneMinute)
                .count();
        return outgoingCount > 5 || incomingCount > 15;
    }

    public void clear() {
        messages.clear();
    }

    private void trim() {
        while (messages.size() > MAX_HISTORY) {
            messages.remove(0);
        }
    }
}
