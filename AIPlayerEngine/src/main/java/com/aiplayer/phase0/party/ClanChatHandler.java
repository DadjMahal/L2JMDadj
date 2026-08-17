package com.aiplayer.phase0.party;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import com.aiplayer.phase0.humanize.AntiDetectionEngine;
import com.aiplayer.phase0.humanize.TimingJitter;
import com.aiplayer.phase0.social.ChatPersonality;
import com.aiplayer.phase0.social.SocialBehaviorEngine;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Integrates AI Players with clan chat, announcements, and events.
 * Handles siege coordination messages, clan war alerts, and
 * social bonding through clan channel.
 *
 * Phase 0: Chat participation only. Siege commands are stubs.
 */
public final class ClanChatHandler {

    private final String accountName;
    private final AntiDetectionEngine anti;
    private final SocialBehaviorEngine social;
    private final ChatPersonality personality;

    private final Queue<ClanMessage> messageQueue = new ConcurrentLinkedQueue<>();
    private boolean isInClan = false;
    private int clanId = 0;
    private String clanName = null;
    private int clanLevel = 0;
    private long lastChatTime = 0;
    private long nextChatTime = 0;

    // Siege state
    private boolean siegeActive = false;
    private String siegeCastle = null;

    public ClanChatHandler(String accountName, AntiDetectionEngine anti,
                         SocialBehaviorEngine social, ChatPersonality personality) {
        this.accountName = accountName;
        this.anti = anti;
        this.social = social;
        this.personality = personality;
    }

    /**
     * Update clan membership state.
     */
    public void updateClanState(int clanId, String clanName, int clanLevel, boolean hasCastle) {
        this.clanId = clanId;
        this.clanName = clanName;
        this.clanLevel = clanLevel;
        this.isInClan = clanId > 0;
    }

    /**
     * Main tick — process outgoing clan chat.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        if (now < nextChatTime) return;
        if (!isInClan) return;

        ClanMessage msg = messageQueue.poll();
        if (msg != null) {
            sendClanChat(msg.text);
            lastChatTime = now;
            nextChatTime = now + anti.getDelay(TimingJitter.ActionContext.SOCIAL_CHAT);
        }
    }

    /**
n     * Receive clan chat message — may trigger response.
     */
    public void onClanChat(String sender, String text) {
        if (sender.equalsIgnoreCase(accountName)) return;

        // Greeting responses
        if (isGreeting(text) && anti.getRandom().nextDouble() < personality.getSocialTendency()) {
            queueMessage(pickGreetingResponse(sender));
        }

        // Siege coordination
        if (siegeActive && isSiegeCommand(text)) {
            handleSiegeCommand(sender, text);
        }

        // Help requests
        if (isHelpRequest(text) && anti.getRandom().nextDouble() < personality.getHelpfulness()) {
            queueMessage("On my way / I'm farming right now, be there soon");
        }
    }

    /**
     * Called when siege starts/ends.
     */
    public void onSiegeStateChange(boolean active, String castleName) {
        this.siegeActive = active;
        this.siegeCastle = castleName;

        if (active) {
            queueMessage("Good luck everyone at " + castleName + " siege!");
        }
    }

    /**
     * Queue a message for sending with human delay.
     */
    public void queueMessage(String text) {
        if (!isInClan) return;
        messageQueue.offer(new ClanMessage(text, System.currentTimeMillis()));
    }

    /**
     * Send clan announcement (leader/officer only).
     */
    public void sendAnnouncement(String text) {
        if (!isInClan) return;
        // In real integration: protocol.sendClanNotice(text)
    }

    // ================================================================
    // SIEGE STUBS (Phase 0 — framework only)
    // ================================================================

    private void handleSiegeCommand(String commander, String text) {
        // Phase 0: acknowledge commands but don't act
        if (text.contains("attack") || text.contains("defend")) {
            // Could set a flag for SiegeParticipationStub
        }
    }

    public boolean isSiegeActive() {
        return siegeActive;
    }

    public String getSiegeCastle() {
        return siegeCastle;
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private boolean isGreeting(String text) {
        String lower = text.toLowerCase();
        return lower.contains("hello") || lower.contains("hi ") || lower.contains("hey") ||
               lower.contains("gm") || lower.contains("good morning");
    }

    private boolean isHelpRequest(String text) {
        String lower = text.toLowerCase();
        return lower.contains("help") || lower.contains("need") || lower.contains("assist") ||
               lower.contains("buff") || lower.contains("rez");
    }

    private boolean isSiegeCommand(String text) {
        String lower = text.toLowerCase();
        return lower.contains("siege") || lower.contains("castle") || lower.contains("attack") ||
               lower.contains("defend") || lower.contains("flag");
    }

    private String pickGreetingResponse(String sender) {
        String[] responses = {
            "Hey " + sender,
            "Hello",
            "Hi",
            "Heya",
            "o/",
            "Hey " + sender + " :)"
        };
        return responses[anti.getRandom().nextInt(responses.length)];
    }

    private void sendClanChat(String text) {
        // In real integration: protocol.sendClanChat(text)
    }

    private static final class ClanMessage {
        final String text;
        final long queuedAt;
        ClanMessage(String text, long queuedAt) {
            this.text = text;
            this.queuedAt = queuedAt;
        }
    }

    public String getStatusReport() {
        return String.format("Clan[%s: clan=%s Lv%d siege=%s queue=%d]",
            accountName, clanName, clanLevel, siegeActive, messageQueue.size());
    }
}
