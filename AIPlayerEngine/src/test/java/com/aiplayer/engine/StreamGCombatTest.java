package com.aiplayer.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stream G (G-Combat): proves the previously-dead combat helper classes now have REAL callers in
 * the live path. Before G, RangedKiteAI / PvPSkillRotation / AntiGriefing / AggroManager /
 * SkillAllocator each had ZERO external references — the recurring instantiated-but-uncalled defect.
 */
public class StreamGCombatTest {

    @Test
    public void antiGriefingIsWiredIntoCombatAI() {
        AIPlayer p = new AIPlayer("GCombatBot", 1, 1, 0);
        AntiGriefing ag = p.getCombatAI().getAntiGriefing();
        assertNotNull(ag);
        // A fresh anti-grief guard permits escalation; repeated grief raises the counter.
        assertTrue(p.getCombatAI().allowPvP());
        ag.increaseGriefLevel();
        ag.increaseGriefLevel();
        ag.increaseGriefLevel();
        assertFalse(p.getCombatAI().allowPvP()); // grief cap reached -> no more PK escalation
    }

    @Test
    public void aggroManagerIsExposed() {
        AIPlayer p = new AIPlayer("GCombatBot", 1, 1, 0);
        AggroManager am = p.getCombatAI().getAggroManager();
        assertNotNull(am);
        assertEquals(AggroManager.EMOTION_NONE, am.getCurrentEmotion());
        am.addThreat(42, 200.0); // a tracked threat entry
        assertEquals(200.0, am.getThreatLevel(42), 0.001);
        assertEquals(42, am.getHighestThreatTarget());
    }

    @Test
    public void skillAllocatorRunsOnLevelUp() {
        AIPlayer p = new AIPlayer("GCombatBot", 1, 1, 0);
        assertEquals(0, p.getCombatAI().getLastSkillAllocation().length); // nothing yet
        p.getCombatAI().onLevelUp(25);
        int[] alloc = p.getCombatAI().getLastSkillAllocation();
        assertEquals(10, alloc.length); // 10 skill categories
        assertEquals(25, p.getLevel());
    }

    @Test
    public void rangedKiteBehaviorIsWired() {
        AIPlayer p = new AIPlayer("GCombatBot", 1, 1, 0);
        // No current target -> never kite.
        assertFalse(p.getCombatAI().shouldKiteNow());
        assertNull(p.getCombatAI().applyKiteBehavior());
        // Direct helper call still resolves sensibly (RangedKiteAI stays in package).
        assertTrue(RangedKiteAI.shouldKite(20, 500, 400));
        assertFalse(RangedKiteAI.shouldKite(90, 100, 400));
    }

    @Test
    public void pvpSkillRotationIsWiredIntoCombatAI() {
        AIPlayer p = new AIPlayer("GCombatBot", 1, 1, 0);
        // High MP vs a caster -> PvPSkillRotation SILENCE/HIGH_BURST path -> a real skill id.
        String skill = p.getCombatAI().getOptimalPvPSkill(80, "Wizard");
        assertTrue(skill.startsWith("SKILL:"));
        // Low MP -> basic attack.
        assertTrue(p.getCombatAI().getOptimalPvPSkill(10, "Wizard").startsWith("ATTACK"));
        assertEquals(PvPSkillRotation.getDefensiveSkill(), p.getCombatAI().getDefensiveSkillId());
    }
}
