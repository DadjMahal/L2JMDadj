package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import com.aiplayer.net.GameServerClient;

/**
 * S2-T05: PacketLogger must decode the character's NAME / CLASS / RACE from CharSelectInfo (0x13)
 * during the handshake, so the build knows who it really is.
 *
 * <p>Frame layout mirrors SourceCode CharSelectionInfo.java writeImpl (Interlude) for the FIRST
 * character entry, exactly as parsed by {@code PacketLogger.parseCharSelectInfo}:
 * [0x13][count:int][name:UTF-16LE+null][objectId:int][account:UTF-16LE+null][sessionId:int]
 * [clanId:int][builder:int][sex:int][race:int][classId:int]
 */
public class PacketLoggerCharSelectTest
{
   private static final int OP_CHAR_SELECT_INFO = 0x13;

   /** Build a CharSelectInfo frame: [2-byte self-inclusive size][payload]. */
   private static byte[] buildCharSelectFrame(int objectId, String name, String account,
                                              int sessionId, int clanId, int sex,
                                              int raceId, int classId)
   {
      ByteBuffer payload = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
      payload.put((byte) OP_CHAR_SELECT_INFO);
      payload.putInt(1);                                  // count: one character slot
      putString(payload, name);                           // Character name (UTF-16LE + null)
      payload.putInt(objectId);                           // Character ID
      putString(payload, account);                        // Account name
      payload.putInt(sessionId);                          // Account ID
      payload.putInt(clanId);                             // Clan ID
      payload.putInt(0);                                  // Builder level
      payload.putInt(sex);                                // Sex
      payload.putInt(raceId);                             // Race
      payload.putInt(classId);                            // Base class ID

      int payloadLen = payload.position();
      int frameLen = payloadLen + 2; // self-inclusive size header
      ByteBuffer frame = ByteBuffer.allocate(frameLen).order(ByteOrder.LITTLE_ENDIAN);
      frame.putShort((short) frameLen);
      frame.put(payload.array(), 0, payloadLen);
      return frame.array();
   }

   /** writeString: UTF-16LE chars + 2-byte 0 terminator (WritableBuffer.writeString). */
   private static void putString(ByteBuffer buf, String s)
   {
      byte[] chars = s.getBytes(StandardCharsets.UTF_16LE);
      buf.put(chars);
      buf.putShort((short) 0);
   }

   @Test
   public void testCharSelectInfoCapturesNameClassRace()
   {
      int objectId = 268435456;
      String name = "CombatBot_01";
      String account = "ai_account";
      int sessionId = 12345;
      int clanId = 0;
      int sex = 1;
      int raceId = 2;    // Human
      int classId = 0x31; // e.g. Human Fighter base class

      PacketLogger logger = new PacketLogger(name);
      logger.logPacket(buildCharSelectFrame(objectId, name, account, sessionId, clanId, sex, raceId, classId));

      assertEquals(name, logger.getCharSelectName(), "CharSelectInfo must capture the character name");
      assertEquals(classId, logger.getCharSelectClassId(), "CharSelectInfo must capture the class id");
      assertEquals(raceId, logger.getCharSelectRaceId(), "CharSelectInfo must capture the race id");
      // CharSelectInfo arrives before UserInfo, so it must seed getCharName() too.
      assertEquals(name, logger.getCharName(), "CharSelectInfo should seed the general charName");
   }

   @Test
   public void testRecordCharSelectInfoFromHandshakePayload()
   {
      // GameServerClient hands the raw payload (opcode first, no size header) to recordCharSelectInfo.
      int objectId = 999;
      String name = "SoloArcher";
      int raceId = 3;    // Elf
      int classId = 0x34; // Elven Ranger base class

      ByteBuffer payload = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
      payload.put((byte) OP_CHAR_SELECT_INFO);
      payload.putInt(1);
      putString(payload, name);
      payload.putInt(objectId);
      putString(payload, "acct");
      payload.putInt(7);   // sessionId
      payload.putInt(0);   // clanId
      payload.putInt(0);   // builder
      payload.putInt(0);   // sex
      payload.putInt(raceId);
      payload.putInt(classId);
      int payloadLen = payload.position();
      byte[] body = new byte[payloadLen];
      System.arraycopy(payload.array(), 0, body, 0, payloadLen);

      PacketLogger logger = new PacketLogger("p");
      logger.recordCharSelectInfo(body);

      assertEquals(name, logger.getCharSelectName());
      assertEquals(classId, logger.getCharSelectClassId());
      assertEquals(raceId, logger.getCharSelectRaceId());
   }

   @Test
   public void testNoCharLeavesIdentityUnset()
   {
      ByteBuffer payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
      payload.put((byte) OP_CHAR_SELECT_INFO);
      payload.putInt(0); // zero characters on the account
      int payloadLen = payload.position();
      int frameLen = payloadLen + 2;
      ByteBuffer frame = ByteBuffer.allocate(frameLen).order(ByteOrder.LITTLE_ENDIAN);
      frame.putShort((short) frameLen);
      frame.put(payload.array(), 0, payloadLen);

      PacketLogger logger = new PacketLogger("p");
      logger.logPacket(frame.array());
      assertNull(logger.getCharSelectName(), "no character -> name stays null");
      assertEquals(0, logger.getCharSelectClassId());
      assertEquals(0, logger.getCharSelectRaceId());
   }
}
