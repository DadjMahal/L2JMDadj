package com.aiplayer.engine;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stream C: tests the {@link CombatFramePlanner} decision → ordered wire-frame mapping.
 */
public class CombatFramePlannerTest
{
    private final CombatFramePlanner planner = new CombatFramePlanner();

    @Test
    public void testAttackDecisionProducesActionThenAttackRequest()
    {
        CombatDecision decision = CombatDecision.attackTarget("objId=500");
        List<CombatFramePlanner.FrameStep> steps =
            planner.plan(decision, 100, 200, -30, 500);

        assertEquals(2, steps.size(), "attack should produce Action + AttackRequest");
        assertEquals(0x04, steps.get(0).getOpcode(), "step 0 should be Action (0x04)");
        assertEquals(CombatFramePlanner.FLOOD_PROTECTOR_DELAY_MS, steps.get(0).delayAfterMs,
            "Action and AttackRequest must be spaced by the flood-protector gap");
        assertEquals(0x0A, steps.get(1).getOpcode(), "step 1 should be AttackRequest (0x0A)");
        assertEquals(0, steps.get(1).delayAfterMs);
    }

    @Test
    public void testAttackWithoutTargetIsEmpty()
    {
        CombatDecision decision = CombatDecision.attack();
        List<CombatFramePlanner.FrameStep> steps = planner.plan(decision, 0, 0, 0, -1);
        assertTrue(steps.isEmpty(), "no target -> no frames to send");
    }

    @Test
    public void testFleeProducesMoveToLocation()
    {
        CombatDecision decision = CombatDecision.flee();
        List<CombatFramePlanner.FrameStep> steps = planner.plan(decision, 100, 200, -30, 0);

        assertEquals(1, steps.size(), "flee should produce one move frame");
        assertEquals(0x01, steps.get(0).getOpcode(), "flee should be MoveToLocation (0x01)");
        assertEquals(31, steps.get(0).frame.length, "MoveToLocation frame is 31 bytes");
        assertEquals(0, steps.get(0).delayAfterMs);
    }

    @Test
    public void testIdleProducesNoFrames()
    {
        List<CombatFramePlanner.FrameStep> steps =
            planner.plan(CombatDecision.idle(), 0, 0, 0, 0);
        assertTrue(steps.isEmpty(), "idle -> no frames");
    }

    @Test
    public void testNullDecisionProducesNoFrames()
    {
        assertTrue(planner.plan(null, 0, 0, 0, 0).isEmpty(), "null decision -> no frames");
    }
}
