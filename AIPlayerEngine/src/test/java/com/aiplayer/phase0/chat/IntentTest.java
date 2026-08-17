package com.aiplayer.phase0.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** S10-T08: locks Intent (flipped MODE:PARTIAL -> COMPLETE). */
class IntentTest
{
    @Test
    void enumIsStable()
    {
        assertEquals(11, Intent.values().length, "intent set must stay explicit");
        assertEquals(Intent.UNKNOWN, Intent.valueOf("UNKNOWN"));
        assertTrue(Intent.COMMAND_FOLLOW.name().startsWith("COMMAND_"));
        assertEquals("SOCIAL_GREET", Intent.SOCIAL_GREET.name());
    }
}