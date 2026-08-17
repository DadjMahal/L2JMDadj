package com.aiplayer.phase0.party;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.phase0.humanize.AntiDetectionEngine;
import com.aiplayer.phase0.humanize.TimingJitter;

import java.util.HashMap;
import java.util.Map;

/**
 * Tactical coordination engine for party combat.
 * Assigns crowd control, coordinates burst windows,
 * manages healer triage, and optimizes positioning.
 *
 * Called from CombatAI when in party mode.
 */
public final class PartyCoordinationEngine {

    private final PartyManager party;
    private final AntiDetectionEngine anti;

    // Cooldown tracking for coordinated abilities
    private final Map<String, Long> ccCooldowns = new HashMap<>();
    private final Map<String, Long> burstCooldowns = new HashMap<>();
    private long lastTriageTime = 0;
    private static final long TRIAGE_INTERVAL_MS = 1500;

    public PartyCoordinationEngine(PartyManager party, AntiDetectionEngine anti) {
        this.party = party;
        this.anti = anti;
    }

    /**
     * Main coordination tick — call before CombatAI decision.
     */
    public void tick(BotStateSnapshot self) {
        if (!party.isInParty()) return;

        long now = System.currentTimeMillis();

        // Periodic triage assessment
        if (now - lastTriageTime > TRIAGE_INTERVAL_MS) {
            assessTriage();
            lastTriageTime = now;
        }
    }

    /**
     * Should this AI Player use crowd control now?
     * Prevents CC overlap with party members.
     */
    public boolean shouldUseCC(int mobObjectId) {
        if (!party.isInParty()) return true;

        String mobKey = "cc_" + mobObjectId;
        Long expire = ccCooldowns.get(mobKey);
        if (expire != null && System.currentTimeMillis() < expire) {
            return false; // Another member already CC'd this mob
        }

        // Claim CC with jittered duration
        long duration = 3000 + anti.getJitter(2000);
        ccCooldowns.put(mobKey, System.currentTimeMillis() + duration);
        return true;
    }

    /**
     * Should this AI Player burst now?
     * Coordinates burst windows for maximum effect.
     */
    public boolean shouldBurst() {
        if (!party.isInParty()) return true;

        // If leader called target, burst immediately
        // Otherwise, check if party is already bursting
        String burstKey = "party_burst";
        Long expire = burstCooldowns.get(burstKey);
        if (expire != null && System.currentTimeMillis() < expire) {
            return true; // Join the burst window
        }

        // Start a burst window
        burstCooldowns.put(burstKey, System.currentTimeMillis() + 8000);
        return true;
    }

    /**
     * Get healer triage priority for this member.
     * Returns 0-100, higher = needs heal more urgently.
     */
    public int getHealPriority(BotStateSnapshot self) {
        PartyMemberInfo me = findSelfInParty(self);
        if (me == null) return 50;

        double hp = (me.hpMax > 0 ? me.hpCurrent * 100.0 / me.hpMax : 100.0);
        if (me.isCritical()) return 100;
        if (hp < 50) return 75;
        if (hp < 70) return 50;
        if (hp < 90) return 25;
        return 0;
    }

    /**
     * Should this AI Player pull the next mob?
     * Only tanks and leaders should pull.
     */
    public boolean shouldPull(BotStateSnapshot self) {
        PartyRole role = party.getMyRole(self);
        if (!role.shouldPull) return false;

        // Don't pull if party members are low
        for (PartyMemberInfo m : party.getMembers()) {
            if ((m.hpMax > 0 ? m.hpCurrent * 100.0 / m.hpMax : 100.0) < 50 && !m.isDead) return false;
            if (m.isDead) return false;
        }

        return true;
    }

    /**
     * Get optimal combat position for this role.
     */
    public int[] getOptimalPosition(BotStateSnapshot self, int targetX, int targetY) {
        PartyRole role = party.getMyRole(self);
        int distance = role.preferredPosition.preferredDistance;

        // Calculate position at preferred distance from target
        double angle = Math.atan2(self.y - targetY, self.x - targetX);
        int px = targetX + (int) (distance * Math.cos(angle));
        int py = targetY + (int) (distance * Math.sin(angle));

        // Add humanization jitter
        int jitter = anti.getJitter(50);
        px += jitter;
        py += jitter;

        return new int[]{px, py, self.z};
    }

    /**
     * Check if we should assist another member under attack.
     */
    public boolean shouldAssistAlly(PartyMemberInfo allyUnderAttack) {
        if (allyUnderAttack == null) return false;
        PartyRole myRole = party.getMyRole(null); // cached role
        return myRole == PartyRole.TANK || myRole == PartyRole.DAMAGE_DEALER;
    }

    // ================================================================
    // INTERNAL
    // ================================================================

    private void assessTriage() {
        // Clean expired cooldowns
        long now = System.currentTimeMillis();
        ccCooldowns.entrySet().removeIf(e -> e.getValue() < now);
        burstCooldowns.entrySet().removeIf(e -> e.getValue() < now);
    }

    private PartyMemberInfo findSelfInParty(BotStateSnapshot self) {
        for (PartyMemberInfo m : party.getMembers()) {
            if (m.objId == self.objId) return m;
        }
        return null;
    }
}
