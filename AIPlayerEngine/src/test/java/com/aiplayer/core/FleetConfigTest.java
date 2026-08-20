package com.aiplayer.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aiplayer.knowledge.PlayerRace;

/**
 * EP-4: FleetConfig parses the launcher args exactly as the pre-split FleetPlay.main did. The
 * primary case is the REAL arg string scripts/fleet_launch.sh builds, so a launcher regression
 * (shifted arg, wrong default) fails here before it can strand a live fleet.
 */
class FleetConfigTest
{
    /** Verbatim from scripts/fleet_launch.sh: fleet_launch.sh 50 8210 ai_rand_ 500000 ELF,... */
    private static final String[] REAL_ARGS =
        "50 127.0.0.1 7777 2106 8210 movement ai_rand_ 500000 ELF,DARK_ELF,ORC,DWARF,HUMAN".split(" ");

    @Test
    void parsesRealFleetLaunchArgs()
    {
        FleetConfig c = FleetConfig.parse(REAL_ARGS);
        assertEquals(50, c.count);
        assertEquals("127.0.0.1", c.host);
        assertEquals(7777, c.gamePort);
        assertEquals(2106, c.loginPort);
        assertEquals(8210, c.dashPort);
        assertTrue(c.forceMovement);
        assertEquals("ai_rand_", c.accountPrefix);
        assertEquals(500000, c.charIdBase);
        assertArrayEquals(new PlayerRace[]
        {
            PlayerRace.ELF, PlayerRace.DARK_ELF, PlayerRace.ORC, PlayerRace.DWARF, PlayerRace.HUMAN
        }, c.raceRotation);
    }

    @Test
    void noArgsKeepOriginalDefaults()
    {
        FleetConfig c = FleetConfig.parse(new String[0]);
        assertEquals(5, c.count);
        assertEquals("127.0.0.1", c.host);
        assertEquals(7777, c.gamePort);
        assertEquals(2106, c.loginPort);
        assertEquals(8080, c.dashPort);
        assertFalse(c.forceMovement);
        assertEquals("ai_combat_", c.accountPrefix);
        assertEquals(100000, c.charIdBase);
        assertEquals(0, c.raceRotation.length, "no 9th arg -> empty rotation -> all-HUMAN fallback");
    }

    @Test
    void randomRaceModeCoversEveryRace()
    {
        FleetConfig c = FleetConfig.parse("3 127.0.0.1 7777 2106 8080 movement ai_x_ 1 random".split(" "));
        assertEquals(PlayerRace.values().length, c.raceRotation.length);
    }

    @Test
    void raceListIsCaseInsensitiveAndSkipsUnknownTokens()
    {
        FleetConfig c = FleetConfig.parse("3 127.0.0.1 7777 2106 8080 movement ai_x_ 1 elf,BOGUS,orc".split(" "));
        assertArrayEquals(new PlayerRace[]
        {
            PlayerRace.ELF, PlayerRace.ORC
        }, c.raceRotation);
    }

    @Test
    void nonMovementSixthArgKeepsEngineMovementDefault()
    {
        // Prefix/base are ALWAYS positional args 7/8 (pre-split semantics): a non-"movement" 6th
        // arg just leaves forceMovement OFF — engine.movement default preserved — and is ignored.
        FleetConfig c = FleetConfig.parse("2 10.0.0.5 7778 2107 9090 none ai_prod_ 200000".split(" "));
        assertFalse(c.forceMovement);
        assertEquals("ai_prod_", c.accountPrefix);
        assertEquals(200000, c.charIdBase);
    }
}
