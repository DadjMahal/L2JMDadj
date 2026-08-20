package com.aiplayer.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.aiplayer.core.AIConfiguration;
import com.aiplayer.core.EngineWiring;
import com.aiplayer.net.AIPlayer;
import com.aiplayer.behavior.combat.FighterRotation;
import com.aiplayer.behavior.combat.PKDecision.Decision;

/**
 * Decision-level tests for the Phase-0 runtime seam ({@link EngineWiring}).
 * All flags default OFF (config/ai-player.properties) — these tests prove the seam is a
 * NO-OP when disabled and genuinely drives the behavior modules (Human Fighter rotation ->
 * Power Strike(3)/Mortal Blow(16)) when enabled, with cooldowns honored.
 */
class EngineWiringTest
{
    private static final String[] FLAGS =
    {
        "engine.enabled", "engine.combat_rotation", "engine.combat_shots", "engine.targeting",
        "engine.humanize", "engine.social", "engine.inventory",
        "engine.death_recovery", "engine.quest_farm"
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
        cfg.setProperty("engine.enabled", String.valueOf(value));
        for (String flag : FLAGS)
        {
            cfg.setProperty(flag, String.valueOf(value));
        }
    }

    private static EngineWiring integration()
    {
        return new EngineWiring(new AIPlayer("behaviortest", 1, 0, 0)); // Human Fighter
    }

    @Test
    void disabledSeamIsANoOp()
    {
        EngineWiring p = integration();
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
        EngineWiring p = integration();
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
        EngineWiring p = integration();
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
        EngineWiring p = integration();
        assertNotNull(p.socialStatus());
        assertNotNull(p.deathRecoveryStatus());
        assertNotNull(p.questFarmStatus());
        assertTrue(p.socialStatus().startsWith("SKIP"));
        assertTrue(p.deathRecoveryStatus().startsWith("SKIP"));
        assertTrue(p.questFarmStatus().startsWith("SKIP"));
    }
}