package com.aiplayer.behavior.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CooldownTrackerTest {

    @Test
    public void testSkillNotOnCooldownInitially() {
        CooldownTracker tracker = new CooldownTracker("TestPlayer");
        assertFalse(tracker.isOnCooldown(1177));
    }

    @Test
    public void testSkillOnCooldownAfterUse() {
        CooldownTracker tracker = new CooldownTracker("TestPlayer");
        tracker.putOnCooldown(1177, 5000);
        assertTrue(tracker.isOnCooldown(1177));
        assertTrue(tracker.getRemainingMs(1177) > 0);
        assertTrue(tracker.getRemainingMs(1177) <= 5000);
    }

    @Test
    public void testResetClearsAllCooldowns() {
        CooldownTracker tracker = new CooldownTracker("TestPlayer");
        tracker.putOnCooldown(1177, 5000);
        tracker.putOnCooldown(1178, 5000);
        tracker.reset();
        assertFalse(tracker.isOnCooldown(1177));
        assertFalse(tracker.isOnCooldown(1178));
    }

    @Test
    public void testUnrelatedSkillUnaffected() {
        CooldownTracker tracker = new CooldownTracker("TestPlayer");
        tracker.putOnCooldown(1177, 5000);
        assertFalse(tracker.isOnCooldown(1178), "putting one skill on cooldown must not affect another");
    }
}
