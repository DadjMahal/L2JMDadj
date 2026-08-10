package com.aiplayer.phase0.social;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.protocol.L2JProtocol;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Manages party invite sending and responding.
 * Uses personality-driven decisions with level/proximity checks.
 */
public final class PartyInviteHandler {

    private static final int MAX_PARTY_SIZE = 9;
    private static final int MIN_LEVEL_DIFF = 5;
    private static final int INVITE_RADIUS = 1500;
    private static final long INVITE_TIMEOUT_MS = 30000;

    private final String accountName;
    private final L2JProtocol protocol;
    private final ChatPersonality personality;
    private final ChatResponder responder;
    private final SocialTimer timer;
    private final Random rng;

    private final Set<String> pendingInvites = new HashSet<>();
    private final Set<String> ignoredPlayers = new HashSet<>();
    private volatile long lastInviteSent = 0;
    private volatile String currentPartyLeader = null;
    private volatile boolean inParty = false;

    public PartyInviteHandler(String accountName, L2JProtocol protocol,
                              ChatPersonality personality, ChatResponder responder,
                              SocialTimer timer) {
        this.accountName = accountName;
        this.protocol = protocol;
        this.personality = personality;
        this.responder = responder;
        this.timer = timer;
        this.rng = personality.getRng();
    }

    /**
     * Call when another player sends a party invite.
     */
    public void onPartyInviteReceived(String inviterName, int inviterLevel) {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        if (ignoredPlayers.contains(inviterName)) {
            try {
                protocol.sendAnswerJoinParty(0); // decline
            } catch (java.io.IOException e) {
                // best-effort
            }
            return;
        }

        boolean accept = responder.shouldReplyToPartyInvite(inviterName);
        if (accept) {
            // Level check: don't join if level gap too big
            if (Math.abs(inviterLevel - self.level) > MIN_LEVEL_DIFF * 2) {
                accept = false;
            }
        }

        if (accept) {
            try {
                protocol.sendAnswerJoinParty(1);
                currentPartyLeader = inviterName;
                inParty = true;
            } catch (java.io.IOException e) {
                // best-effort
            }
        } else {
            try {
                protocol.sendAnswerJoinParty(0);
            } catch (java.io.IOException e) {
                // best-effort
            }
            // 20% chance to ignore future invites from this player
            if (rng.nextDouble() < 0.2) {
                ignoredPlayers.add(inviterName);
            }
        }
    }

    /**
     * Call periodically to evaluate sending party invites.
     */
    public void tick() {
        if (inParty) return;
        if (!responder.shouldSendPartyInvite()) return;

        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        // Find nearby players without parties
        // Phase 0: stub — Phase 1 queries GameStateMirror for nearby players
        // For now, rely on external trigger or random chance
        if (rng.nextDouble() < 0.05) { // 5% chance per tick when conditions met
            sendRandomPartyInvite();
        }
    }

    /**
     * Call when party member list updates.
     */
    public void onPartyUpdate(String[] members, String leader) {
        this.currentPartyLeader = leader;
        this.inParty = members.length > 1;
    }

    /**
     * Call when leaving party or kicked.
     */
    public void onPartyLeave() {
        inParty = false;
        currentPartyLeader = null;
    }

    public boolean isInParty() {
        return inParty;
    }

    public String getPartyLeader() {
        return currentPartyLeader;
    }

    // ------------------------------------------------------------------

    private void sendRandomPartyInvite() {
        // Phase 0: No actual player list — this is a stub
        // Phase 1: Query GameStateMirror for nearby unpartied players
        // and send RequestJoinParty packets
        lastInviteSent = System.currentTimeMillis();
        timer.markPartyInvite();
    }
}