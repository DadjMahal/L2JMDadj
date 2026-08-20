package com.aiplayer.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * AcquireCooldown (ACQUIRE-failure cooldown) — once the fleet abandons the same geo-unreachable
 * ACQUIRE route maxUnreachable times, ACQUIRE goals must be suppressed for the cooldown window so
 * the bot falls back to plain farming instead of re-planning the dead ocean-hop forever. Pure
 * clock-injected tests (no sleeps, no wall-clock dependency except the no-arg-note test).
 */
class AcquireCooldownTest
{
    private static final long NOW = 1_000_000L;
    private static final long COOLDOWN_MS = 1_000L;

    @Test
    void belowThresholdIsNotSuppressed()
    {
        AcquireCooldown cd = new AcquireCooldown(2, COOLDOWN_MS);
        cd.noteUnreachableAbandon(NOW);
        assertFalse(cd.isSuppressed(NOW), "a single abort is below the 2-abort threshold");
        assertFalse(cd.isSuppressed(NOW + COOLDOWN_MS + 10));
        assertEquals(1, cd.unreachableCount());
        assertEquals(0, cd.cooldownUntilMs(), "no window armed below the threshold");
    }

    @Test
    void atThresholdIsSuppressedUntilCooldownExpires()
    {
        AcquireCooldown cd = new AcquireCooldown(2, COOLDOWN_MS);
        cd.noteUnreachableAbandon(NOW);
        cd.noteUnreachableAbandon(NOW);
        assertEquals(NOW + COOLDOWN_MS, cd.cooldownUntilMs());
        assertTrue(cd.isSuppressed(NOW), "at threshold the ACQUIRE goal must be suppressed");
        assertTrue(cd.isSuppressed(NOW + COOLDOWN_MS), "still suppressed exactly at the window end");
        assertFalse(cd.isSuppressed(NOW + COOLDOWN_MS + 1), "no longer suppressed past the window");
    }

    @Test
    void resetClearsCountAndTimestamp()
    {
        AcquireCooldown cd = new AcquireCooldown(2, COOLDOWN_MS);
        cd.noteUnreachableAbandon(NOW);
        cd.noteUnreachableAbandon(NOW);
        assertTrue(cd.isSuppressed(NOW));
        cd.reset();
        assertEquals(0, cd.unreachableCount());
        assertEquals(0, cd.cooldownUntilMs());
        assertFalse(cd.isSuppressed(NOW), "reset must lift the suppression immediately");
    }

    @Test
    void suppressionExpiresAsNowPassesCooldownUntil()
    {
        AcquireCooldown cd = new AcquireCooldown(2, COOLDOWN_MS);
        cd.noteUnreachableAbandon(NOW);
        cd.noteUnreachableAbandon(NOW);
        // Walk a clock forward second by second: suppressed through cooldownUntilMs, then clear.
        for (long t = NOW; t <= NOW + COOLDOWN_MS; t += 100)
        {
            assertTrue(cd.isSuppressed(t), "suppressed at t=" + t);
        }
        assertFalse(cd.isSuppressed(NOW + COOLDOWN_MS + 1), "expired just past cooldownUntilMs");
        assertFalse(cd.isSuppressed(NOW + 10 * COOLDOWN_MS), "still expired well after");
    }

    @Test
    void abandoningDuringWindowDoesNotExtendIt()
    {
        AcquireCooldown cd = new AcquireCooldown(2, COOLDOWN_MS);
        cd.noteUnreachableAbandon(NOW);
        cd.noteUnreachableAbandon(NOW);                         // window [NOW, NOW+COOLDOWN_MS]
        cd.noteUnreachableAbandon(NOW + COOLDOWN_MS / 2);       // third abort mid-window
        assertTrue(cd.isSuppressed(NOW + COOLDOWN_MS / 2));
        assertFalse(cd.isSuppressed(NOW + COOLDOWN_MS + 1),
            "a mid-window abort must not extend the suppression deadline");
    }

    @Test
    void repeatedFailuresRearmAfterWindowExpires()
    {
        AcquireCooldown cd = new AcquireCooldown(2, COOLDOWN_MS);
        cd.noteUnreachableAbandon(NOW);
        cd.noteUnreachableAbandon(NOW);                         // 2/2 -> armed until NOW+COOLDOWN_MS
        long afterExpiry = NOW + COOLDOWN_MS + 1;
        assertFalse(cd.isSuppressed(afterExpiry), "window has fully lapsed");
        cd.noteUnreachableAbandon(afterExpiry);                 // repeated failure pattern -> re-arm
        assertTrue(cd.isSuppressed(afterExpiry), "re-arms immediately on the next abort");
        assertTrue(cd.isSuppressed(afterExpiry + COOLDOWN_MS / 2), "suppressed for a fresh window");
        assertFalse(cd.isSuppressed(afterExpiry + COOLDOWN_MS + 1), "fresh window eventually expires");
    }

    @Test
    void recordUnreachableAbortBehavesLikeNote()
    {
        AcquireCooldown cd = new AcquireCooldown(2, COOLDOWN_MS);
        cd.recordUnreachableAbort(NOW);
        cd.recordUnreachableAbort(NOW);
        assertTrue(cd.isSuppressed(NOW));
        assertEquals(2, cd.unreachableCount());
        assertEquals(NOW + COOLDOWN_MS, cd.cooldownUntilMs());
    }

    @Test
    void defaultsAreTwoAbortsAndFiveMinutes()
    {
        AcquireCooldown cd = new AcquireCooldown();
        assertEquals(2, cd.maxUnreachable());
        assertEquals(5L * 60 * 1000L, cd.cooldownMs());
        assertFalse(cd.isSuppressed(NOW), "fresh cooldown starts un-suppressed");
    }

    @Test
    void noArgNoteReadsTheWallClock()
    {
        AcquireCooldown cd = new AcquireCooldown(1, COOLDOWN_MS);
        cd.noteUnreachableAbandon();
        assertEquals(1, cd.unreachableCount());
        assertTrue(cd.cooldownUntilMs() > 0, "window armed by the wall-clock variant");
        assertTrue(cd.isSuppressed(System.currentTimeMillis()), "just-armed window suppresses now");
    }

    @Test
    void suppressionBoundariesAroundTheWindow()
    {
        // Tiny public-constructor window (10ms) + injected clock: pure boundary probe, no sleeps.
        AcquireCooldown cd = new AcquireCooldown(1, 10);
        assertFalse(cd.isSuppressed(NOW), "fresh instance starts un-suppressed");
        cd.noteUnreachableAbandon(NOW);
        long until = cd.cooldownUntilMs();
        assertEquals(NOW + 10, until, "window armed at now + cooldownMs");
        assertTrue(cd.isSuppressed(until - 1), "suppressed just before cooldownUntilMs");
        assertTrue(cd.isSuppressed(until), "still suppressed exactly at cooldownUntilMs");
        assertFalse(cd.isSuppressed(until + 1), "no longer suppressed after the window");
    }

}