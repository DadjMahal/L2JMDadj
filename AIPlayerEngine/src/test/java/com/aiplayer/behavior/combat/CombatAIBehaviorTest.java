package com.aiplayer.behavior.combat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.aiplayer.behavior.combat.CombatAI;
import com.aiplayer.behavior.combat.CombatDecision;
import com.aiplayer.core.AIConfiguration;
import com.aiplayer.core.EngineWiring;
import com.aiplayer.net.AIPlayer;

/**
 * Gate tests: CombatAI must be behavior-ignorant with default config (byte-for-byte
 * previous behavior) and only build the EngineWiring seam when explicitly enabled.
 */
class CombatAIBehaviorTest
{
    @BeforeEach
    void clean()
    {
        AIConfiguration.getInstance().setProperty("engine.enabled", "false");
    }

    @AfterEach
    void restore()
    {
        AIConfiguration.getInstance().setProperty("engine.enabled", "false");
    }

    @Test
    void combatAiHasNoBehaviorSeamByDefault()
    {
        CombatAI ai = new CombatAI(new AIPlayer("gatebot", 1, 0, 0));
        assertNull(ai.getBehaviorIntegration(), "behavior seam must be absent with default config");
    }

    @Test
    void combatAiBuildsBehaviorSeamWhenEnabledAndStillDecides()
    {
        AIConfiguration.getInstance().setProperty("engine.enabled", "true");
        CombatAI ai = new CombatAI(new AIPlayer("gatebot", 1, 0, 0));
        assertNotNull(ai.getBehaviorIntegration(), "behavior seam must be present when enabled");
        // A fresh bot must still produce a decision without exceptions — the exact action for a
        // bot with no packets is engine-defined (IDLE when packetLogger reports it dead, or
        // AUTO_PLAY when auto_play is enabled), not behavior-defined.
        CombatDecision decision = ai.makeDecision();
        assertNotNull(decision, "makeDecision must produce a decision when behavior enabled");
        assertTrue(decision.getAction() == CombatDecision.Action.IDLE
            || decision.getAction() == CombatDecision.Action.AUTO_PLAY);
    }
}