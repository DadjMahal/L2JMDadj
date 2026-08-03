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

   @Test
   public void testEncodeMoveToLocationLayout()
   {
      byte[] frame = PacketCodec.encodeMoveToLocation(-82515, 241221, -3728, -83789, 240799, -3717, 0);

      assertEquals(31, frame.length, "MoveToLocation frame should be 31 bytes");
      assertEquals(31, (frame[0] & 0xff) | ((frame[1] & 0xff) << 8), "self-inclusive size");
      assertEquals(0x01, frame[2] & 0xff, "opcode should be MOVE_TO_LOCATION 0x01");
      assertEquals(-82515, leInt(frame, 3), "targetX");
      assertEquals(241221, leInt(frame, 7), "targetY");
      assertEquals(-3728, leInt(frame, 11), "targetZ");
      assertEquals(-83789, leInt(frame, 15), "originX");
      assertEquals(240799, leInt(frame, 19), "originY");
      assertEquals(-3717, leInt(frame, 23), "originZ");
      assertEquals(0, leInt(frame, 27), "moveType should be 0 (cursor-key walk)");
   }

   @Test
   public void testEncodeProtocolVersionLayout()
   {
      byte[] p = PacketCodec.encodeProtocolVersion(746);
      assertEquals(5, p.length, "ProtocolVersion payload is 5 bytes");
      assertEquals(0x00, p[0] & 0xff, "opcode 0x00");
      assertEquals(746, leInt(p, 1), "version 746");
   }

   @Test
   public void testEncodeAuthLoginLayout()
   {
      byte[] p = PacketCodec.encodeAuthLogin("ai_combat_01", 0x11111111, 0x22222222, 0x33333333, 0x44444444);
      assertEquals(0x08, p[0] & 0xff, "opcode 0x08");
      // account "ai_combat_01" (12 chars) UTF-16LE = 24 bytes + 1 opcode + 2 null + 16 keys
      assertEquals(1 + 24 + 2 + 16, p.length, "AuthLogin payload length");
      assertEquals(0x11111111, leInt(p, p.length - 16), "playKey2");
      assertEquals(0x22222222, leInt(p, p.length - 12), "playKey1");
      assertEquals(0x33333333, leInt(p, p.length - 8), "loginKey1");
      assertEquals(0x44444444, leInt(p, p.length - 4), "loginKey2");
   }

   @Test
   public void testEncodeCharacterSelectAndEnterWorldLayout()
   {
      byte[] cs = PacketCodec.encodeCharacterSelect(0);
      assertEquals(19, cs.length, "CharacterSelect payload is 19 bytes");
      assertEquals(0x0D, cs[0] & 0xff, "opcode 0x0D");
      assertEquals(0, leInt(cs, 1), "charSlot");

      byte[] ew = PacketCodec.encodeEnterWorld();
      assertEquals(105, ew.length, "EnterWorld payload is 105 bytes");
      assertEquals(0x03, ew[0] & 0xff, "opcode 0x03");
   }

   private static int leInt(byte[] d, int i)
   {
      return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16) | ((d[i + 3] & 0xff) << 24);
   }
}
