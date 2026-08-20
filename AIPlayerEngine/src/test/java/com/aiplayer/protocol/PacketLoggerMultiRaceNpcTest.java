package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.aiplayer.protocol.PacketLogger.EntityInfo;

/**
 * S2-T03 — multi-race NPC_INFO burst: three DIFFERENT mobs (Talking-Island wolf 20120,
 * Elven Red Keltir 20530, Orc mob 20531) arrive in one frame burst; each must be tracked
 * under its own objectId with the right npcId, hostile=true and its own position.
 * Frame layout mirrors PacketLoggerNpcInfoTest exactly (real Interlude AbstractNpcInfo):
 *   [size:short][0x16][objectId][displayId+1000000][isAttackable][x][y][z][heading]
 */
public class PacketLoggerMultiRaceNpcTest
{
   private static final int OP_NPC_INFO = 0x16;

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
   public void multiRaceBurstTracksEachMobWithOwnIdHostileAndPosition()
   {
      int wolfId = 20120;        // Talking-Island wolf
      int keltirId = 20530;      // Elven Red Keltir
      int orcId = 20531;         // Orc mob

      PacketLogger logger = new PacketLogger("CombatBot_01");
      // One burst: all three mobs visible at once, each at its own position.
      logger.logPacket(buildNpcInfoFrame(1001, 1000000 + wolfId, 1, 100, 200, -300, 0));
      logger.logPacket(buildNpcInfoFrame(1002, 1000000 + keltirId, 1, -500, 600, -700, 0));
      logger.logPacket(buildNpcInfoFrame(1003, 1000000 + orcId, 1, 800, -900, 1000, 0));

      assertEquals(3, logger.getEntityCount(), "all three mobs must be tracked in the burst");

      PacketLogger.EntityInfo wolf = logger.getEntity(1001);
      assertNotNull(wolf);
      assertEquals(wolfId, wolf.npcId, "wolf npcId must be 20120 (displayId - 1000000)");
      assertTrue(wolf.isHostile, "wolf must be hostile");
      assertEquals(100, wolf.x);
      assertEquals(200, wolf.y);
      assertEquals(-300, wolf.z);

      PacketLogger.EntityInfo keltir = logger.getEntity(1002);
      assertNotNull(keltir);
      assertEquals(keltirId, keltir.npcId, "keltir npcId must be 20530");
      assertTrue(keltir.isHostile, "keltir must be hostile");
      assertEquals(-500, keltir.x);
      assertEquals(600, keltir.y);
      assertEquals(-700, keltir.z);

      PacketLogger.EntityInfo orc = logger.getEntity(1003);
      assertNotNull(orc);
      assertEquals(orcId, orc.npcId, "orc npcId must be 20531");
      assertTrue(orc.isHostile, "orc must be hostile");
      assertEquals(800, orc.x);
      assertEquals(-900, orc.y);
      assertEquals(1000, orc.z);
   }
}
