package com.aiplayer.behavior.social;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.core.GameStateMirror;
import com.aiplayer.core.GameStateMirror.BotStateSnapshot;
import com.aiplayer.protocol.L2JProtocol;

import java.util.Random;

import java.io.IOException;
import com.aiplayer.core.BotSnapshot;
import com.aiplayer.behavior.social.ChatMessage.Channel;

/**
 * Main orchestrator for all social behavior.
 * Integrates chat, party, emotes, and proactive social actions.
 *
 * Tick flow:
 * 1. Process incoming chat queue
 * 2. Evaluate proactive chat opportunities
 * 3. Evaluate party invites
 * 4. Random emote check
 */
public final class SocialBehaviorEngine {

    private static final long TICK_INTERVAL_MS = 2000;
    private static final double RANDOM_EMOTE_CHANCE = 0.02; // 2% per tick

    private final String accountName;
    private final L2JProtocol protocol;
    private final ChatPersonality personality;
    private final ChatHistory history;
    private final SocialTimer timer;
    private final ChatResponder responder;
    private final ChatFilter filter;
    private final PartyInviteHandler partyHandler;
    private final Random rng;

    private volatile long lastTick = 0;
    private volatile boolean enabled = true;

    public SocialBehaviorEngine(String accountName, L2JProtocol protocol) {
        this.accountName = accountName;
        this.protocol = protocol;
        this.personality = ChatPersonality.fromAccount(accountName);
        this.history = new ChatHistory(accountName);
        this.timer = new SocialTimer(accountName, personality.getRng());
        this.responder = new ChatResponder(accountName, protocol, personality, history, timer);
        this.filter = new ChatFilter(accountName);
        this.partyHandler = new PartyInviteHandler(accountName, protocol, personality, responder, timer);
        this.rng = personality.getRng();
    }

    /**
     * Call when a chat packet arrives from server.
     */
    public void onChatMessage(String sender, int channelId, String text) {
        if (!enabled) return;

        ChatMessage.Channel channel = mapChannel(channelId);
        if (channel == null) return;

        // Filter check
        if (filter.shouldIgnore(sender, text)) {
            return;
        }

        // Process through responder
        String reply = responder.onIncomingMessage(sender, channel, text);
        if (reply != null) {
            String safeReply = filter.normalizeOutgoing(reply);
            sendChat(channel, safeReply);
        }
    }

    /**
     * Call when party invite packet arrives.
     */
    public void onPartyInvite(String inviterName, int inviterLevel) {
        partyHandler.onPartyInviteReceived(inviterName, inviterLevel);
    }

    /**
     * Call when party list updates.
     */
    public void onPartyListUpdate(String[] members, String leader) {
        partyHandler.onPartyUpdate(members, leader);
    }

    /**
     * Main tick — call every 1-2 seconds.
     */
    public void tick() {
        if (!enabled) return;

        long now = System.currentTimeMillis();
        if (now - lastTick < TICK_INTERVAL_MS) return;
        lastTick = now;

        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null || (self.hpMax > 0 ? self.hpCurrent * 100 / self.hpMax : 100) <= 0) return;

        // Party handling
        partyHandler.tick();

        // Proactive chat (only when not in combat, not moving aggressively)
        if (!self.isInCombat && !self.isMoving) {
            evaluateProactiveChat();
        }

        // Random emote
        if (timer.canRandomEmote() && rng.nextDouble() < RANDOM_EMOTE_CHANCE) {
            doRandomEmote();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ChatHistory getHistory() {
        return history;
    }

    public ChatPersonality getPersonality() {
        return personality;
    }

    public PartyInviteHandler getPartyHandler() {
        return partyHandler;
    }

    // ------------------------------------------------------------------

    private void evaluateProactiveChat() {
        // Trade chat (trader personality)
        if (personality.rollTrade() && timer.canTradeChat()) {
            String msg = responder.generateProactive(ChatMessage.Channel.TRADE);
            if (msg != null) {
                sendChat(ChatMessage.Channel.TRADE, msg);
                return;
            }
        }

        // Random shout (social personalities)
        if (personality.rollSocialAggression() && timer.canShout()) {
            String msg = responder.generateProactive(ChatMessage.Channel.SHOUT);
            if (msg != null) {
                sendChat(ChatMessage.Channel.SHOUT, msg);
                return;
            }
        }

        // General social (all channels)
        if (personality.rollTalkative() && timer.canGlobalChat()) {
            String msg = responder.generateProactive(ChatMessage.Channel.ALL);
            if (msg != null) {
                sendChat(ChatMessage.Channel.ALL, msg);
            }
        }
    }

    private void doRandomEmote() {
        String[] emotes = {"/sit", "/stand", "/bow", "/wave", "/clap", "/dance"};
        String emote = emotes[rng.nextInt(emotes.length)];
        try {
            protocol.sendSay(emote); // emotes are sent as say commands
            timer.markRandomEmote();
        } catch (IOException e) {
            // best-effort; a dead socket is surfaced upstream
        }
    }

    private void sendChat(ChatMessage.Channel channel, String text) {
        if (text == null || text.isEmpty()) return;

        try {
            switch (channel) {
                case ALL:
                    protocol.sendSay(text);
                    break;
                case SHOUT:
                    protocol.sendShout(text);
                    break;
                case TELL:
                    // PMs handled in responder directly
                    break;
                case TRADE:
                    protocol.sendTradeChat(text);
                    break;
                case PARTY:
                    protocol.sendPartyChat(text);
                    break;
                case CLAN:
                    protocol.sendClanChat(text);
                    break;
                default:
                    protocol.sendSay(text);
                    break;
            }
        } catch (IOException e) {
            // best-effort chat send; non-fatal for the decision loop
        }
    }

    private ChatMessage.Channel mapChannel(int channelId) {
        // L2JMobius channel IDs
        switch (channelId) {
            case 0: return ChatMessage.Channel.ALL;
            case 1: return ChatMessage.Channel.SHOUT;
            case 2: return ChatMessage.Channel.TELL;
            case 3: return ChatMessage.Channel.PARTY;
            case 4: return ChatMessage.Channel.CLAN;
            case 5: return ChatMessage.Channel.ALLIANCE;
            case 8: return ChatMessage.Channel.TRADE;
            case 9: return ChatMessage.Channel.HERO;
            case 10: return ChatMessage.Channel.ANNOUNCEMENT;
            default: return ChatMessage.Channel.ALL;
        }
    }
}
