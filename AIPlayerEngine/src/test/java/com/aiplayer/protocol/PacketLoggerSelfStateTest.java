package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.aiplayer.protocol.PacketLogger.EntityInfo;

/**
 * Dashboard data-layer regression tests. Frames are built EXACTLY per the Interlude server
 * writers (SourceCode/.../serverpackets/UserInfo.java, CharInfo.java, ValidateLocation.java,
 * StatusUpdate.java):
 *   UserInfo (0x04): [x][y][z][vehicle][objId][name UTF16LE+null][race][female][baseClass]
 *                    [level][exp:long][str..men][maxHp][curHp][maxMp][curMp][sp][curLoad]
 *                    [maxLoad][weaponFlag]...
 *   CharInfo (0x03): [x][y][z][vehicle][objId][name UTF16LE+null][race][female][baseClass]...
 *   ValidateLocation (0x61): [objId][x][y][z][heading]
 */
public class PacketLoggerSelfStateTest
{
   private static void putString(ByteBuffer buf, String s)
   {
      for (int i = 0; i < s.length(); i++)
      {
         buf.putShort((short) s.charAt(i));
      }
      buf.putShort((short) 0);
   }

   private static void send(ByteBuffer payload, PacketLogger logger)
   {
      byte[] body = new byte[payload.position()];
      ByteBuffer dup = payload.duplicate();
      dup.flip();
      dup.get(body);
      ByteBuffer frame = ByteBuffer.allocate(body.length + 2).order(ByteOrder.LITTLE_ENDIAN);
      frame.putShort((short) (body.length + 2));
      frame.put(body);
      logger.logPacket(frame.array());
   }

   private static ByteBuffer payload(int opcode)
   {
      ByteBuffer b = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
      b.put((byte) opcode);
      return b;
   }

   @Test
   public void charInfoParsesPositionObjectIdAndTracksEntity()
   {
      PacketLogger logger = new PacketLogger("CombatBot_02");
      logger.setSelfObjectId(100001);

      ByteBuffer b = payload(0x03);
      b.putInt(-83500);
      b.putInt(251000);
      b.putInt(-3600);
      b.putInt(0);                      // vehicle
      b.putInt(100001);                 // objId
      putString(b, "CombatBot_02");
      b.putInt(0);
      b.putInt(0);
      b.putInt(0);
      send(b, logger);

      assertEquals(-83500, logger.getPlayerX());
      assertEquals(251000, logger.getPlayerY());
      PacketLogger.EntityInfo e = logger.getEntity(100001);
      assertNotNull(e);
      assertEquals("CombatBot_02", e.name);
      assertTrue(!e.isHostile);
   }

   @Test
   public void validateLocationUpdatesSelfPositionWhileMoving()
   {
      PacketLogger logger = new PacketLogger("CombatBot_01");
      logger.setSelfObjectId(100000);

      ByteBuffer b = payload(0x61);
      b.putInt(100000);
      b.putInt(-82000);
      b.putInt(249900);
      b.putInt(-3596);
      b.putInt(300);                    // heading
      send(b, logger);

      assertEquals(-82000, logger.getPlayerX());
      assertEquals(249900, logger.getPlayerY());
      assertEquals(-3596, logger.getPlayerZ());
      assertEquals(300, logger.getPlayerHeading());
   }

   @Test
   public void charMoveToLocationUpdatesSelfPositionMidWalk()
   {
      // Server MoveToLocation.java writeImpl (CHAR_MOVE_TO_LOCATION 0x01):
      // [objId][xDest][yDest][zDest][x][y][z] — the current position is carried last.
      PacketLogger logger = new PacketLogger("CombatBot_01");
      logger.setSelfObjectId(100000);

      ByteBuffer b = payload(0x01);
      b.putInt(100000);                 // objId
      b.putInt(-80591);                 // destination x
      b.putInt(240497);                 // destination y
      b.putInt(-3639);                  // destination z
      b.putInt(-81000);                 // current x (mid-walk)
      b.putInt(240200);                 // current y
      b.putInt(-3635);                  // current z
      send(b, logger);

      assertEquals(-81000, logger.getPlayerX(), "self X must track the server broadcast");
      assertEquals(240200, logger.getPlayerY());
      assertEquals(-3635, logger.getPlayerZ());
   }

   @Test
   public void stopMoveConfirmsArrivalPosition()
   {
      // Server StopMove.java writeImpl (STOP_MOVE 0x47): [objId][x][y][z][heading].
      PacketLogger logger = new PacketLogger("CombatBot_01");
      logger.setSelfObjectId(100000);

      ByteBuffer b = payload(0x47);
      b.putInt(100000);
      b.putInt(-80591);                 // arrived x = the hunt target from the live run
      b.putInt(240497);                 // arrived y
      b.putInt(-3639);                  // arrived z
      b.putInt(4096);                   // heading
      send(b, logger);

      assertEquals(-80591, logger.getPlayerX(), "StopMove is the server-confirmed arrival");
      assertEquals(240497, logger.getPlayerY());
      assertEquals(-3639, logger.getPlayerZ());
      assertEquals(4096, logger.getPlayerHeading());
   }

   @Test
   public void charMoveToLocationForOtherEntityDoesNotMoveSelf()
   {
      PacketLogger logger = new PacketLogger("CombatBot_01");
      logger.setSelfObjectId(100000);

      // Seed self position with a CharInfo first.
      ByteBuffer seed = payload(0x03);
      seed.putInt(-79052);
      seed.putInt(239857);
      seed.putInt(-3639);
      seed.putInt(0);                   // vehicle
      seed.putInt(100000);              // objId
      putString(seed, "CombatBot_01");
      seed.putInt(0);
      seed.putInt(0);
      seed.putInt(0);
      send(seed, logger);

      ByteBuffer b = payload(0x01);
      b.putInt(268461977);              // a wolf objId
      b.putInt(-80591);
      b.putInt(240497);
      b.putInt(-3639);
      b.putInt(-80000);
      b.putInt(240000);
      b.putInt(-3630);
      send(b, logger);

      assertEquals(-79052, logger.getPlayerX(), "another entity's move must not move self");
      assertEquals(239857, logger.getPlayerY());
   }

   @Test
   public void statusUpdateLevelAndExpAttributesAreCaptured()
   {
      PacketLogger logger = new PacketLogger("CombatBot_01");
      logger.setSelfObjectId(100000);

      ByteBuffer b = payload(0x0E);
      b.putInt(100000);                 // objId
      b.putInt(3);                      // attr count
      b.putInt(0x01); b.putInt(21);     // LEVEL = 21
      b.putInt(0x02); b.putInt(2_100_000); // EXP = 2100000
      b.putInt(0x09); b.putInt(510);   // CUR_HP
      send(b, logger);

      assertEquals(21, logger.getLevel());
      assertEquals(2_100_000L, logger.getExp());
      assertEquals(510, logger.getCurHp());
   }

   @Test
   public void targetEntityResolvableForThoughts()
   {
      PacketLogger logger = new PacketLogger("CombatBot_01");
      logger.setSelfObjectId(100000);
      ByteBuffer b = payload(0x16); // NPC_INFO real layout
      b.putInt(268439316);
      b.putInt(1020544);   // wolf display
      b.putInt(1);         // attackable
      b.putInt(-83479);
      b.putInt(250275);
      b.putInt(-3596);
      b.putInt(0);
      send(b, logger);
      PacketLogger.EntityInfo wolf = logger.getEntity(268439316);
      assertNotNull(wolf);
      assertTrue(wolf.isHostile);
      assertTrue(logger.getHostileEntityCount() >= 1);
   }
}