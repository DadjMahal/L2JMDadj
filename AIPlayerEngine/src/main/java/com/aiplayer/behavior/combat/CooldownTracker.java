package com.aiplayer.behavior.combat;

/** MODE: COMPLETE. Was missing entirely; written fresh, in-memory, no external deps. */

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.aiplayer.behavior.combat.CombatAI;

/**
 * Per-AI-Player skill cooldown tracking.
 *
 * This file did not exist anywhere in the four Kimi Task 1 transcript files —
 * FighterRotation.java (and the CombatAI integration patch) reference a
 * cooldownTracker field of this type, but the class itself was never
 * delivered. Caught by an external review (DeepSeek), verified by checking
 * the actual extraction output, confirmed genuinely absent, and written here
 * from scratch rather than guessed from usage patterns alone.
 *
 * In-memory, not Redis-backed: this project's integration rules for the
 * behavior package are zero new Maven dependencies and no new infrastructure
 * at this scale. A ConcurrentHashMap per AI Player is sufficient — there is
 * no cross-process sharing requirement for a single-JVM engine.
 */
public final class CooldownTracker {
    private final String accountName;
    private final Map<Integer, Long> cooldownExpiryMs = new ConcurrentHashMap<>();

    public CooldownTracker(String accountName) {
        this.accountName = accountName;
    }

    /**
     * Put a skill on cooldown for the given duration, starting now.
     */
    public void putOnCooldown(int skillId, long cooldownMs) {
        cooldownExpiryMs.put(skillId, System.currentTimeMillis() + cooldownMs);
    }

    /**
     * True if the skill is still on cooldown.
     */
    public boolean isOnCooldown(int skillId) {
        Long expiry = cooldownExpiryMs.get(skillId);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            cooldownExpiryMs.remove(skillId); // expired, clean up
            return false;
        }
        return true;
    }

    /**
     * Milliseconds remaining on this skill's cooldown, or 0 if not on cooldown.
     */
    public long getRemainingMs(int skillId) {
        Long expiry = cooldownExpiryMs.get(skillId);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * Clear all tracked cooldowns (e.g. on death/respawn).
     */
    public void reset() {
        cooldownExpiryMs.clear();
    }

    public String getAccountName() {
        return accountName;
    }
}
