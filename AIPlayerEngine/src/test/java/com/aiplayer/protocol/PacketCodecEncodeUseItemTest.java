package com.aiplayer.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** S6-T04: locks the UseItem (0x14) encoder the potion-drink wiring depends on. */
class PacketCodecEncodeUseItemTest
{
    @Test
    void encodeUsesServerOpcode14WithObjectIdAndZeroCtrl()
    {
        byte[] f = PacketCodec.encodeUseItem(0x01020304);
        // [size:2=11][opcode 0x14][objectId:4][ctrlPressed:4]
        assertArrayEquals(new byte[]{
            11, 0, 0x14,
            4, 3, 2, 1,   // objectId little-endian
            0, 0, 0, 0    // ctrlPressed = false
        }, f);
    }
}