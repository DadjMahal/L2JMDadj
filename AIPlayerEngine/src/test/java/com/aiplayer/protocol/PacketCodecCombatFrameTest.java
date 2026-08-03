package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stream C: byte-layout tests for the real client combat frames (Action 0x04 / AttackRequest 0x0A)
 * added to {@link PacketCodec}. These must match the B4-proven CombatProbe wire format:
 * frame = [2-byte self-inclusive size][opcode][fields]; payload for both = [opcode][objId][x][y][z][id]
 * => frame length 20.
 */
public class PacketCodecCombatFrameTest
{
   @Test
   public void testEncodeActionLayout()
   {
      byte[] frame = PacketCodec.encodeAction(0x11223344, 1, 2, 3);

      assertEquals(20, frame.length, "Action frame should be 20 bytes");
      // self-inclusive size header = 20 (LE)
      assertEquals(20, (frame[0] & 0xff) | ((frame[1] & 0xff) << 8), "self-inclusive size");
      assertEquals(0x04, frame[2] & 0xff, "opcode should be ACTION 0x04");
      assertEquals(0x11223344, leInt(frame, 3), "targetObjId");
      assertEquals(1, leInt(frame, 7), "originX");
      assertEquals(2, leInt(frame, 11), "originY");
      assertEquals(3, leInt(frame, 15), "originZ");
      assertEquals(0, frame[19] & 0xff, "actionId should be 0 (simple click)");
   }

   @Test
   public void testEncodeAttackRequestLayout()
   {
      byte[] frame = PacketCodec.encodeAttackRequest(0x55667788);

      assertEquals(20, frame.length, "AttackRequest frame should be 20 bytes");
      assertEquals(20, (frame[0] & 0xff) | ((frame[1] & 0xff) << 8), "self-inclusive size");
      assertEquals(0x0A, frame[2] & 0xff, "opcode should be ATTACK_REQUEST 0x0A");
      assertEquals(0x55667788, leInt(frame, 3), "targetObjId");
      assertEquals(0, leInt(frame, 7), "originX should be 0");
      assertEquals(0, leInt(frame, 11), "originY should be 0");
      assertEquals(0, leInt(frame, 15), "originZ should be 0");
      assertEquals(0, frame[19] & 0xff, "attackId should be 0");
   }

   private static int leInt(byte[] d, int i)
   {
      return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16) | ((d[i + 3] & 0xff) << 24);
   }
}
