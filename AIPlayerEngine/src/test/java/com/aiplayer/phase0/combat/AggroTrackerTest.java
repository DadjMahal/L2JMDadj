package com.aiplayer.phase0.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AggroTrackerTest {

    @Test
    public void testAddAndCheckAggro() {
        AggroTracker tracker = new AggroTracker("TestPlayer");
        assertFalse(tracker.isAggroedBy(12345));
        tracker.addAggro(12345, 40);
        assertTrue(tracker.isAggroedBy(12345));
        assertEquals(1, tracker.getAggroCount());
    }

    @Test
    public void testRemoveAggro() {
        AggroTracker tracker = new AggroTracker("TestPlayer");
        tracker.addAggro(12345, 40);
        tracker.removeAggro(12345);
        assertFalse(tracker.isAggroedBy(12345));
    }

    @Test
    public void testClearRemovesEverything() {
        AggroTracker tracker = new AggroTracker("TestPlayer");
        tracker.addAggro(1, 10);
        tracker.addAggro(2, 20);
        tracker.clear();
        assertEquals(0, tracker.getAggroCount());
    }
}
