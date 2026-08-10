package com.aiplayer.phase0.social;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * Immutable representation of a chat message seen or sent by an AI Player.
 * Tracks channel, sender, content, and timestamp for context-aware responses.
 */
public final class ChatMessage {

    public enum Channel {
        ALL,      // White global chat
        SHOUT,    // ! Orange, radius ~2000
        TELL,     // " PM / whisper
        PARTY,    // # Party chat
        CLAN,     // @ Clan chat
        ALLIANCE, // $ Alliance chat
        TRADE,    // + Trade chat, radius ~2000
        HERO,     // Hero voice
        ANNOUNCEMENT // System announcements
    }

    public final String sender;
    public final String senderAccount; // normalized account name if known
    public final Channel channel;
    public final String text;
    public final long timestamp;
    public final boolean isOutgoing; // true if sent by this AI Player

    public ChatMessage(String sender, String senderAccount, Channel channel,
                       String text, long timestamp, boolean isOutgoing) {
        this.sender = sender;
        this.senderAccount = senderAccount;
        this.channel = channel;
        this.text = text;
        this.timestamp = timestamp;
        this.isOutgoing = isOutgoing;
    }

    public ChatMessage(String sender, Channel channel, String text, boolean isOutgoing) {
        this(sender, null, channel, text, System.currentTimeMillis(), isOutgoing);
    }

    public boolean isRecent(long withinMs) {
        return System.currentTimeMillis() - timestamp <= withinMs;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", channel, sender, text);
    }
}