package com.aiplayer.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Gate tests: CombatAI must be phase0-ignorant with default config (byte-for-byte
 * previous behavior) and only build the Phase0Integration seam when explicitly enabled.
 */
class CombatAIPhase0Test
{
    @BeforeEach
    void clean()
    {
        AIConfiguration.getInstance().setProperty("phase0.enabled", "false");
    }

    @AfterEach
    void restore()
    {
        AIConfiguration.getInstance().setProperty("phase0.enabled", "false");
    }

    @Test
    void combatAiHasNoPhase0SeamByDefault()
    {
        CombatAI ai = new CombatAI(new AIPlayer("gatebot", 1, 0, 0));
        assertNull(ai.getPhase0Integration(), "phase0 seam must be absent with default config");
    }

    @Test
    void combatAiBuildsPhase0SeamWhenEnabledAndStillDecides()
    {
        AIConfiguration.getInstance().setProperty("phase0.enabled", "true");
        CombatAI ai = new CombatAI(new AIPlayer("gatebot", 1, 0, 0));
        assertNotNull(ai.getPhase0Integration(), "phase0 seam must be present when enabled");
        // A fresh bot must still produce a decision without exceptions — the exact action for a
        // bot with no packets is engine-defined (IDLE when packetLogger reports it dead, or
        // AUTO_PLAY when auto_play is enabled), not phase0-defined.
        CombatDecision decision = ai.makeDecision();
        assertNotNull(decision, "makeDecision must produce a decision when phase0 enabled");
        assertTrue(decision.getAction() == CombatDecision.Action.IDLE
            || decision.getAction() == CombatDecision.Action.AUTO_PLAY);
    }
}