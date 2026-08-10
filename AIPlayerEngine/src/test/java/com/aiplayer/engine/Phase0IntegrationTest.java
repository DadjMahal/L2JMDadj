package com.aiplayer.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Decision-level tests for the Phase-0 runtime seam ({@link Phase0Integration}).
 * All flags default OFF (config/ai-player.properties) — these tests prove the seam is a
 * NO-OP when disabled and genuinely drives the phase0 modules (Human Fighter rotation ->
 * Power Strike(3)/Mortal Blow(16)) when enabled, with cooldowns honored.
 */
class Phase0IntegrationTest
{
    private static final String[] FLAGS =
    {
        "phase0.enabled", "phase0.combat_rotation", "phase0.combat_shots", "phase0.targeting",
        "phase0.humanize", "phase0.social", "phase0.inventory",
        "phase0.death_recovery", "phase0.quest_farm"
    };

    @BeforeEach
    void setUp()
    {
        setAll(false);
    }

    @AfterEach
    void tearDown()
    {
        setAll(false);
    }

    private static void setAll(boolean value)
    {
        AIConfiguration cfg = AIConfiguration.getInstance();
        cfg.setProperty("phase0.enabled", String.valueOf(value));
        for (String flag : FLAGS)
        {
            cfg.setProperty(flag, String.valueOf(value));
        }
    }

    private static Phase0Integration integration()
    {
        return new Phase0Integration(new AIPlayer("phase0test", 1, 0, 0)); // Human Fighter
    }

    @Test
    void disabledSeamIsANoOp()
    {
        Phase0Integration p = integration();
        assertNull(p.socialStatus());
        assertNull(p.deathRecoveryStatus());
        assertNull(p.questFarmStatus());
        assertEquals(-1, p.recommendSkill(80, 80, 40.0));
        assertEquals(0, p.selectTarget());
        assertEquals(0L, p.reactionDelayMs());
        assertEquals(0, p.aggroCount());
        assertFalse(p.shouldKite());
    }

    @Test
    void enabledFighterRotationRecommendsPowerStrikeThenHonorsCooldown()
    {
        setAll(true);
        Phase0Integration p = integration();
        // FighterRotation combo: step1 -> normal attack, step2 -> Power Strike (3)
        assertEquals(-1, p.recommendSkill(80, 80, 40.0), "combo step 1 falls through to normal attack");
        assertEquals(3, p.recommendSkill(80, 80, 40.0), "combo step 2 picks Power Strike (H5 lv1, range 40)");
        p.noteSkillUsed(3);
        assertTrue(p.isOnCooldown(3), "skill 3 must go on the H5 13000ms cooldown after use");
        assertNotEquals(3, p.recommendSkill(80, 80, 40.0), "rotation must not re-pick a skill on cooldown");
    }

    @Test
    void enabledTargetingAggroAndHumanize()
    {
        setAll(true);
        Phase0Integration p = integration();
        p.noteAggro(5, 3);
        p.noteAggro(7, 3);
        assertEquals(2, p.aggroCount());
        assertTrue(p.selectTarget() >= 0, "selectTarget must not throw on an empty world");
        assertTrue(p.reactionDelayMs() >= 0, "humanized reaction delay must never be negative");
    }

    @Test
    void enabledSeamsReportExplicitSkipsNotFakeBehavior()
    {
        setAll(true);
        Phase0Integration p = integration();
        assertNotNull(p.socialStatus());
        assertNotNull(p.deathRecoveryStatus());
        assertNotNull(p.questFarmStatus());
        assertTrue(p.socialStatus().startsWith("SKIP"));
        assertTrue(p.deathRecoveryStatus().startsWith("SKIP"));
        assertTrue(p.questFarmStatus().startsWith("SKIP"));
    }
}