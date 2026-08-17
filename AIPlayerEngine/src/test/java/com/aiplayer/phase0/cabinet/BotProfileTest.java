package com.aiplayer.phase0.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** S10-T08: locks BotProfile (flipped MODE:PARTIAL -> COMPLETE). */
class BotProfileTest
{
    private static BotProfile profile(int classId)
    {
        return new BotProfile(UUID.randomUUID(), "b", "a", 0, classId, 0, 1, 10, "p", 0, 0, 0);
    }

    @Test
    void healerBufferMageArcherClassification()
    {
        assertTrue(profile(15).isHealer());
        assertTrue(profile(16).isBuffer());
        assertTrue(profile(11).isMage());
        assertTrue(profile(18).isArcher());
        assertFalse(profile(18).isHealer());
    }

    @Test
    void identityIsPreserved()
    {
        BotProfile p = profile(0);
        assertEquals("b", p.getName());
        assertEquals("a", p.getAccountName());
        assertEquals(1, p.getLevel());
        assertTrue(p.getBotId() != null, "botId must be present");
    }
}