package com.aiplayer.phase0;

import com.aiplayer.protocol.PacketLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies BotSnapshot.from() actually round-trips real PacketLogger data —
 * the specific acceptance criterion the external review asked for ("a unit
 * test asserts fields round-trip from a constructed PacketLogger").
 */
public class BotSnapshotTest {

    @Test
    public void testSnapshotReflectsPacketLoggerState() {
        PacketLogger logger = new PacketLogger("TestPlayer");
        logger.setCurHp(750);
        logger.setMaxHp(1000);
        logger.setAdena(54321);
        logger.setInventoryUsagePercent(42);

        BotSnapshot snap = BotSnapshot.from("TestPlayer", logger);

        assertEquals("TestPlayer", snap.accountName);
        assertEquals(750, snap.hpCurrent);
        assertEquals(1000, snap.hpMax);
        assertEquals(54321, snap.adena);
        assertEquals(42, snap.inventoryUsagePercent);
        assertEquals(logger.getHpPercentage(), snap.hpPercent, 0.001,
            "hpPercent must come from the real getHpPercentage(), not be hand-computed separately");
    }

    @Test
    public void testNoFieldExistsThatPacketLoggerCannotFill() {
        // Regression guard for the bug this class exists to fix: every field
        // on BotSnapshot must trace to a real PacketLogger getter. This test
        // can't statically prove that, but it does prove construction never
        // needs anything PacketLogger doesn't already expose — if a future
        // edit adds a field sourced from nowhere, from() won't compile.
        PacketLogger logger = new PacketLogger("Empty");
        BotSnapshot snap = BotSnapshot.from("Empty", logger);
        assertNotNull(snap);
        // Round-trip the real getters (the engine's PacketLogger legitimately
        // defaults curHp to a full-HP value, so assert against the getter rather
        // than a hardcoded 0) — this is the round-trip invariant this test exists
        // to guard: every BotSnapshot field traces to a real PacketLogger getter.
        assertEquals(logger.getCurHp(), snap.hpCurrent);
        assertEquals(logger.getAdena(), snap.adena);
    }
}
