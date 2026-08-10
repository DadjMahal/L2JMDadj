package com.aiplayer.phase0.social;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Manages cooldowns, random intervals, and rate-limiting for all social actions.
 * Ensures AI Players don't spam chat or invites in detectable patterns.
 */
public final class SocialTimer {

    // Minimum intervals between actions (ms)
    private static final long MIN_SHOUT_INTERVAL_MS = 45000;      // 45s between shouts
    private static final long MIN_TRADE_CHAT_INTERVAL_MS = 30000; // 30s between trade msgs
    private static final long MIN_PM_REPLY_INTERVAL_MS = 5000;    // 5s between PM replies
    private static final long MIN_PARTY_INVITE_INTERVAL_MS = 60000; // 1m between invites
    private static final long MIN_PARTY_REPLY_INTERVAL_MS = 8000;   // 8s to respond to invite
    private static final long MIN_RANDOM_EMOTE_INTERVAL_MS = 120000; // 2m between emotes
    private static final long MIN_GLOBAL_CHAT_INTERVAL_MS = 15000;   // 15s between global/all

    // Jitter ranges (adds +/- randomness to intervals)
    private static final double JITTER_FACTOR = 0.3; // +/- 30%

    private final String accountName;
    private final Random rng;
    private final Map<String, Long> lastAction = new HashMap<>();
    private final Map<String, Long> nextAllowed = new HashMap<>();

    public SocialTimer(String accountName, Random rng) {
        this.accountName = accountName;
        this.rng = rng;
    }

    public boolean canShout() {
        return isAllowed("shout", MIN_SHOUT_INTERVAL_MS);
    }

    public boolean canTradeChat() {
        return isAllowed("trade_chat", MIN_TRADE_CHAT_INTERVAL_MS);
    }

    public boolean canPmReply() {
        return isAllowed("pm_reply", MIN_PM_REPLY_INTERVAL_MS);
    }

    public boolean canPartyInvite() {
        return isAllowed("party_invite", MIN_PARTY_INVITE_INTERVAL_MS);
    }

    public boolean canRespondToPartyInvite() {
        return isAllowed("party_reply", MIN_PARTY_REPLY_INTERVAL_MS);
    }

    public boolean canRandomEmote() {
        return isAllowed("random_emote", MIN_RANDOM_EMOTE_INTERVAL_MS);
    }

    public boolean canGlobalChat() {
        return isAllowed("global_chat", MIN_GLOBAL_CHAT_INTERVAL_MS);
    }

    public void markShout() {
        mark("shout", MIN_SHOUT_INTERVAL_MS);
    }

    public void markTradeChat() {
        mark("trade_chat", MIN_TRADE_CHAT_INTERVAL_MS);
    }

    public void markPmReply() {
        mark("pm_reply", MIN_PM_REPLY_INTERVAL_MS);
    }

    public void markPartyInvite() {
        mark("party_invite", MIN_PARTY_INVITE_INTERVAL_MS);
    }

    public void markPartyReply() {
        mark("party_reply", MIN_PARTY_REPLY_INTERVAL_MS);
    }

    public void markRandomEmote() {
        mark("random_emote", MIN_RANDOM_EMOTE_INTERVAL_MS);
    }

    public void markGlobalChat() {
        mark("global_chat", MIN_GLOBAL_CHAT_INTERVAL_MS);
    }

    public long getRemainingMs(String actionKey) {
        Long next = nextAllowed.get(actionKey);
        if (next == null) return 0;
        return Math.max(0, next - System.currentTimeMillis());
    }

    /**
     * Reset all cooldowns (e.g., after respawn or zone change).
     */
    public void resetAll() {
        lastAction.clear();
        nextAllowed.clear();
    }

    // ------------------------------------------------------------------

    private boolean isAllowed(String key, long minInterval) {
        Long next = nextAllowed.get(key);
        if (next == null) return true;
        return System.currentTimeMillis() >= next;
    }

    private void mark(String key, long baseInterval) {
        long now = System.currentTimeMillis();
        lastAction.put(key, now);
        long jitter = (long) (baseInterval * JITTER_FACTOR * (2.0 * rng.nextDouble() - 1.0));
        nextAllowed.put(key, now + baseInterval + jitter);
    }
}