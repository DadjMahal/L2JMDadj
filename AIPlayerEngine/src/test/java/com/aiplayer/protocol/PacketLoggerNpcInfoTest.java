package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stream C regression test for the PacketLogger.parseNpcInfo OFF-BY-ONE fix
 * (Audit/35 discovery #3). Real Interlude AbstractNpcInfo layout:
 *   [0x16][objectId][displayId+1000000][isAttackable][x][y][z][heading]...
 * The old code read [objectId][id][x][y][z], misreading isAttackable as x.
 * Uses the actual B4 live-captured values (Wolf/Elder Keltir at Talking Island).
 */
public class PacketLoggerNpcInfoTest
{
   private static final int OP_NPC_INFO = 0x16;

   /** Build a real-layout NPC_INFO frame: [2-byte self-inclusive size][payload]. */
   private static byte[] buildNpcInfoFrame(int objectId, int displayId, int isAttackable,
                                           int x, int y, int z, int heading)
   {
      int payloadLen = 1 + 4 + 4 + 4 + 4 + 4 + 4 + 4; // opcode + 7 int fields
      ByteBuffer payload = ByteBuffer.allocate(payloadLen).order(ByteOrder.LITTLE_ENDIAN);
      payload.put((byte) OP_NPC_INFO);
      payload.putInt(objectId);
      payload.putInt(displayId);      // real template id + 1000000
      payload.putInt(isAttackable);   // 0 or 1
      payload.putInt(x);
      payload.putInt(y);
      payload.putInt(z);
      payload.putInt(heading);

      int frameLen = payloadLen + 2; // self-inclusive size header
      ByteBuffer frame = ByteBuffer.allocate(frameLen).order(ByteOrder.LITTLE_ENDIAN);
      frame.putShort((short) frameLen);
      frame.put(payload.array());
      return frame.array();
   }

   @Test
   public void testNpcInfoParsesAttackableMonsterAtRealPosition()
   {
      // Real B4 values: Elder Keltir objId, displayId=1020544, attackable=1,
      // pos=(-83479,250275,-3596).
      int objectId = 268439316;
      int displayId = 1020544;
      int x = -83479;
      int y = 250275;
      int z = -3596;
      int heading = 0;

      PacketLogger logger = new PacketLogger("CombatBot_01");
      logger.logPacket(buildNpcInfoFrame(objectId, displayId, 1, x, y, z, heading));

      PacketLogger.EntityInfo entity = logger.getEntity(objectId);
      assertNotNull(entity, "attackable NPC should be tracked");
      assertEquals(displayId - 1000000, entity.npcId, "npcId should be displayId - 1000000");
      // Off-by-one regression: position must be the REAL coords, not shifted.
      assertEquals(x, entity.x, "x must be the real coordinate (off-by-one regression)");
      assertEquals(y, entity.y, "y must be the real coordinate");
      assertEquals(z, entity.z, "z must be the real coordinate");
      assertTrue(entity.isHostile, "attackable monster should be flagged hostile");
   }

   @Test
   public void testNpcInfoParsesHeading()
   {
      int objectId = 7;
      int heading = 2345;
      PacketLogger logger = new PacketLogger("p");
      logger.logPacket(buildNpcInfoFrame(objectId, 1000000 + 20544 /*Keltir*/, 0, 5, 6, 7, heading));
      PacketLogger.EntityInfo entity = logger.getEntity(objectId);
      assertNotNull(entity);
      assertEquals(heading, entity.heading, "heading should be parsed from offset 25");
   }

   @Test
   public void testNonAttackableNeutralNpcNotHostile()
   {
      // npcId 0 (displayId 1000000) with attackable=0 -> heuristic also false -> not hostile.
      int objectId = 3;
      PacketLogger logger = new PacketLogger("p");
      logger.logPacket(buildNpcInfoFrame(objectId, 1000000, 0, 100, 200, 30, 0));
      PacketLogger.EntityInfo entity = logger.getEntity(objectId);
      assertNotNull(entity);
      assertEquals(0, entity.npcId);
      assertFalse(entity.isHostile, "neutral non-attackable NPC should not be hostile");
   }

   @Test
   public void testFindNearestHostileUsesRealPositions()
   {
      // Two NPCs at different distances from player origin (0,0,0); nearest should win
      // using the REAL coordinates, which proves the parse fix feeds target selection.
      int farNpc = 101;
      int nearNpc = 102;
      PacketLogger logger = new PacketLogger("p");
      // displayId = 1000000 + 20544 (Keltir, attackable)
      logger.logPacket(buildNpcInfoFrame(farNpc, 1000000 + 20544, 1, -1000, 0, 0, 0));
      logger.logPacket(buildNpcInfoFrame(nearNpc, 1000000 + 20544, 1, -100, 0, 0, 0));
      PacketLogger.EntityInfo nearest = logger.findNearestHostile(0, 0, 0, 100000);
      assertNotNull(nearest);
      assertEquals(nearNpc, nearest.objectId, "nearest hostile should be the closest NPC");
   }
}
