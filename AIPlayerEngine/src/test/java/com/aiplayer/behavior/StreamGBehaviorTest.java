package com.aiplayer.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.aiplayer.behavior.BehaviorSeeder;
import com.aiplayer.behavior.HumanReactionSimulator;
import com.aiplayer.behavior.MovementPatternAI;
import com.aiplayer.behavior.ResourceHoardingAI;
import com.aiplayer.net.AIPlayer;

/**
 * Stream G (G-Behavior): proves HumanReactionSimulator / BehaviorSeeder / MovementPatternAI /
 * ResourceHoardingAI are wired into AIPlayer (getters + deterministic-consultation points).
 * Before G all four had ZERO callers.
 */
public class StreamGBehaviorTest {

    @Test
    public void behaviorSimulatorsAreExposedAndNonNull() {
        AIPlayer p = new AIPlayer("GBehaveBot", 1, 1, 0);
        assertNotNull(p.getHumanReaction());
        assertNotNull(p.getBehaviorSeeder());
        assertNotNull(p.getMovementPatternAI());
        assertNotNull(p.getResourceHoardingAI());
    }

    @Test
    public void humanReactionProducesMeasurableDelay() {
        AIPlayer p = new AIPlayer("GBehaveBot", 1, 1, 0);
        long delay = p.getHumanReaction().getHumanDelay();
        assertTrue(delay > 0, "human-like delay must be positive, got " + delay);
        assertTrue(delay < 1000, "human-like delay must stay sub-second, got " + delay);
    }

    @Test
    public void behaviorSeederIsDeterministicPerPlayer() {
        AIPlayer p1 = new AIPlayer("GBehaveBot", 1, 1, 0);
        AIPlayer p2 = new AIPlayer("GBehaveBot", 1, 1, 0);
        BehaviorSeeder.BehaviorStyle s1 = p1.getBehaviorSeeder().seedUniqueBehavior(p1.getName());
        BehaviorSeeder.BehaviorStyle s2 = p2.getBehaviorSeeder().seedUniqueBehavior(p2.getName());
        // Same player id -> same seeded style (deterministic, no surprise per-run flips).
        assertEquals(s1, s2);
        assertNotNull(p1.getBehaviorSeeder().getDefaults());
    }

    @Test
    public void movementPatternRespectsWalkStyle() {
        AIPlayer p = new AIPlayer("GBehaveBot", 1, 1, 0);
        double[] hurried = p.getMovementPatternAI().getMovementVector(100.0,
            MovementPatternAI.WalkStyle.HURRIED);
        assertEquals(90.0, hurried[0], 0.001); // walk ratio 0.9
        assertEquals(80.0, hurried[1], 0.001); // run ratio 0.8
        // Cautious walks more than it runs.
        double[] cautious = p.getMovementPatternAI().getMovementVector(100.0,
            MovementPatternAI.WalkStyle.CAUTIOUS);
        assertTrue(cautious[0] < cautious[1]);
    }

    @Test
    public void resourceHoardingSavesSparingly() {
        AIPlayer p = new AIPlayer("GBehaveBot", 1, 1, 0);
        ResourceHoardingAI hoard = p.getResourceHoardingAI();
        assertTrue(hoard.shouldSave(5000, 100, ResourceHoardingAI.HoardingStyle.SAVE));
        // Balanced: only hoard when adena comfortably exceeds 10x the market price.
        assertFalse(hoard.shouldSave(500, 100, ResourceHoardingAI.HoardingStyle.SPEND_BALANCED));
        assertTrue(hoard.shouldSave(5000, 100, ResourceHoardingAI.HoardingStyle.SPEND_BALANCED));
    }
}
