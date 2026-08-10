package com.aiplayer.phase0.combat;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;

/**
 * Tracks party leader's target for assist logic.
 * Phase 0: Reads from GameStateMirror party data.
 */
public class PartyAssistTracker {
    private final String accountName;

    public PartyAssistTracker(String accountName) {
        this.accountName = accountName;
    }

    /**
     * Returns party leader's current target object ID, or 0 if none/solo.
     */
    public int getPartyLeaderTarget() {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null || self.partyMembers == null || self.partyMembers.isEmpty()) {
            return 0;
        }

        // Find leader (lowest OID usually, or marked leader)
        // Phase 0: assume first party member is leader if not self
        for (GameStateMirror.PartyMemberSnapshot member : self.partyMembers) {
            if (member.isLeader) {
                return member.targetObjId;
            }
        }
        return 0;
    }

    /**
     * Check if AI Player should assist leader instead of picking own target.
     */
    public boolean shouldAssistLeader() {
        int leaderTarget = getPartyLeaderTarget();
        if (leaderTarget == 0) return false;

        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return false;

        // If already attacking leader's target, keep at it
        if (self.targetObjId == leaderTarget) return true;

        // If no target or far from current target, switch to assist
        if (self.targetObjId == 0) return true;

        return false;
    }
}
