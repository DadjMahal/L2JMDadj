package com.aiplayer.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

/**
 * Byte-exact unit test for the corrected REQUEST_MAGIC_SKILL_USE (0x2F) encoder.
 *
 * The field order/width is server-authoritative per
 * SourceCode/java/org/l2jmobius/gameserver/network/clientpackets/RequestMagicSkillUse.java:42-44
 * (readInt skillId -> readInt ctrl -> readByte shift) which yields a 12-byte frame
 * including the 2-byte little-endian self-inclusive size header:
 *   [0x0C 0x00] size=12 | [0x2F] opcode | [int skillId] | [int ctrl] | [byte shift]
 *
 * This is the same layout the live throwaway CombatProbe uses (sendMagicSkillUse), which
 * the running H5 GameServer accepted (ActionFailed reply, no disconnect).
 */
class PacketCodecUseSkillTest
{
    // [size=12 LE][0x2F][skillId=3 LE][ctrl=0 LE][shift=0]
    private static final byte[] CAST_NO_MODIFIERS =
    {
        0x0C, 0x00, // size = 12 (self-inclusive), little-endian
        0x2F,       // REQUEST_MAGIC_SKILL_USE opcode (H5)
        0x03, 0x00, 0x00, 0x00, // skillId = 3 (Power Strike), LE int
        0x00, 0x00, 0x00, 0x00, // ctrl  = false -> 4-byte 0 (readInt)
        0x00        // shift = false -> 1-byte  0 (readByte)
    };

    // [size=12 LE][0x2F][skillId=3 LE][ctrl=1 LE][shift=1]
    private static final byte[] CAST_BOTH_MODIFIERS =
    {
        0x0C, 0x00,
        0x2F,
        0x03, 0x00, 0x00, 0x00,
        0x01, 0x00, 0x00, 0x00, // ctrl = true -> 4-byte 1
        0x01        // shift = true -> 1-byte 1
    };

    @Test
    void frameIs12BytesWithCorrectOpcodeAndFieldWidths()
    {
        byte[] f = PacketCodec.encodeUseSkill(3, false, false);
        assertArrayEquals(CAST_NO_MODIFIERS, f,
                "0x2F frame must match server readInt/readInt/readByte layout (12 bytes)");
    }

    @Test
    void ctrlEncodesAsFourByteIntAndShiftAsOneByte()
    {
        byte[] f = PacketCodec.encodeUseSkill(3, true, true);
        assertArrayEquals(CAST_BOTH_MODIFIERS, f,
                "ctrl must be 4 bytes (readInt) and shift must be 1 byte (readByte)");
    }

    @Test
    void frameMatchesProbeThrowawayLayoutUsedInLiveGate()
    {
        // Mirrors CombatProbe.sendMagicSkillUse(skillId,false,false) — the live-proven 0x2F frame.
        byte[] f = PacketCodec.encodeUseSkill(3, false, false);
        assertEquals(12, f.length);
        assertEquals(0x2F, f[2] & 0xff); // opcode sits at offset 2 (after the 2-byte size header)
                assertEquals(3, ByteBuffer.wrap(f, 3, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()); // skillId right after opcode
    }
}