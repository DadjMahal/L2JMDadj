package com.aiplayer.phase0.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.aiplayer.core.AIConfiguration;
import com.aiplayer.core.EngineConfig;

/**
 * TIM-001: the phase0.movement switchboard must AND with phase0.enabled (default OFF =
 * previous behavior) and expose the tunables with sane defaults.
 */
class Phase0MovementConfigTest
{
    @AfterEach
    void tearDown()
    {
        AIConfiguration cfg = AIConfiguration.getInstance();
        cfg.setProperty("phase0.enabled", "false");
        cfg.setProperty("phase0.movement", "false");
    }

    @Test
    void movementRequiresMasterSwitch()
    {
        AIConfiguration cfg = AIConfiguration.getInstance();
        cfg.setProperty("phase0.movement", "true");

        EngineConfig c = EngineConfig.getInstance();
        assertFalse(c.isMovementEnabled(), "master phase0.enabled=false must keep movement OFF");
    }

    @Test
    void movementEnabledWithBothFlags()
    {
        AIConfiguration cfg = AIConfiguration.getInstance();
        cfg.setProperty("phase0.enabled", "true");
        cfg.setProperty("phase0.movement", "true");

        EngineConfig c = EngineConfig.getInstance();
        assertTrue(c.isMovementEnabled());
        assertEquals(20_000, c.getMovementIdleRouteMs());
        assertEquals(4_000, c.getMovementMinRadius());
        assertEquals(30_000, c.getMovementMaxRadius());
    }
}