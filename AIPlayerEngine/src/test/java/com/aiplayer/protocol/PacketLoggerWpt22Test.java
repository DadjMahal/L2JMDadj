package com.aiplayer.protocol;

import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.aiplayer.protocol.PacketLogger.ChatEvent;
import com.aiplayer.protocol.PacketLogger.SystemMessageEvent;

/**
 * WPT-22 — SystemMessage (0x64) and chat (NpcSay 0x02 / CreatureSay 0x4A) decoding into typed
 * telemetry. Frames are built exactly per the Interlude writers:
 *   SystemMessage: [msgId:int][paramCount:int][(type:int, value)*]
 *   NpcSay:        [objectId:int][textType:int][npcId+1000000:int][text UTF-16LE NUL]
 *   CreatureSay:   [objectId:int][chatType:int][senderName UTF-16LE NUL][text UTF-16LE NUL]
 */
public class PacketLoggerWpt22Test
{
   private static DatapackNames fixtureNames()
   {
      URL url = DatapackNamesTest.class.getClassLoader()
         .getResource("com/aiplayer/protocol/datapack/stats");
      assertNotNull(url);
      return new DatapackNames(Paths.get(url.getPath()));
   }

   private static void putString(ByteBuffer buf, String s)
   {
      for (int i = 0; i < s.length(); i++)
      {
         buf.putShort((short) s.charAt(i));
      }
      buf.putShort((short) 0);
   }

   private static ByteBuffer payload(int opcode)
   {
      ByteBuffer b = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN);
      b.put((byte) opcode);
      return b;
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

   @Test
   public void systemMessageWithNoParamsDecodesToSysmsgTag()
   {
      PacketLogger logger = new PacketLogger("Bot");
      logger.setDatapackNames(fixtureNames());
      ByteBuffer b = payload(0x64);
      b.putInt(123);      // msgId
      b.putInt(0);        // paramCount
      send(b, logger);

      PacketLogger.SystemMessageEvent e = logger.getLastSystemMessage();
      assertNotNull(e);
      assertEquals(123, e.msgId);
      assertTrue(e.text.startsWith("sysmsg#123"));
      assertEquals(1, logger.getSystemMessageCount());
   }

   @Test
   public void systemMessageResolvesItemAndNpcParamNamesIntoHumanString()
   {
      PacketLogger logger = new PacketLogger("Bot");
      logger.setDatapackNames(fixtureNames());
      ByteBuffer b = payload(0x64);
      b.putInt(999);      // msgId
      b.putInt(3);        // paramCount
      b.putInt(0); putString(b, "You earned");          // TYPE_TEXT
      b.putInt(3); b.putInt(57);                        // TYPE_ITEM_NAME -> Adena
      b.putInt(2); b.putInt(20562);                     // TYPE_NPC_NAME -> Orc Fighter
      send(b, logger);

      PacketLogger.SystemMessageEvent e = logger.getLastSystemMessage();
      assertNotNull(e);
      assertEquals(999, e.msgId);
      assertEquals("sysmsg#999 [You earned, Adena, Orc Fighter]", e.text);
      assertEquals(3, e.params.size());
      assertEquals(PacketLogger.SM_TYPE_ITEM_NAME, e.params.get(1).type);
      assertEquals("Adena", e.params.get(1).rendered);
      assertEquals("Orc Fighter", e.params.get(2).rendered);
   }

   @Test
   public void systemMessageLongAndIntAndSkillParamsDecode()
   {
      PacketLogger logger = new PacketLogger("Bot");
      logger.setDatapackNames(fixtureNames());
      ByteBuffer b = payload(0x64);
      b.putInt(500);      // msgId
      b.putInt(3);        // paramCount
      b.putInt(6); b.putLong(2_100_000L);   // TYPE_LONG_NUMBER
      b.putInt(1); b.putInt(42);            // TYPE_INT_NUMBER
      b.putInt(4); b.putInt(7); b.putInt(3); // TYPE_SKILL_NAME skillId=7 lvl=3
      send(b, logger);

      PacketLogger.SystemMessageEvent e = logger.getLastSystemMessage();
      assertNotNull(e);
      assertEquals("sysmsg#500 [2100000, 42, skill:7 (lv 3)]", e.text);
      assertEquals(3, e.params.size());
   }

   @Test
   public void npcSayChatResolvesSpeakerNameFromDatapack()
   {
      PacketLogger logger = new PacketLogger("Bot");
      logger.setDatapackNames(fixtureNames());
      ByteBuffer b = payload(0x02);
      b.putInt(268439316);      // objectId
      b.putInt(0);              // textType (general)
      b.putInt(1_000_000 + 20562); // npcId + 1000000
      putString(b, "Kill the intruders!");
      send(b, logger);

      PacketLogger.ChatEvent e = logger.getLastChatEvent();
      assertNotNull(e);
      assertEquals("npc", e.kind);
      assertEquals("Orc Fighter", e.speaker);
      assertEquals("Kill the intruders!", e.text);
      assertEquals(268439316, e.objectId);
      assertEquals(1, logger.getChatCount());
   }

   @Test
   public void creatureSayPlayerChatKeepsSpeakerAndText()
   {
      PacketLogger logger = new PacketLogger("Bot");
      logger.setDatapackNames(fixtureNames());
      ByteBuffer b = payload(0x4A);
      b.putInt(123);         // objectId
      b.putInt(2);           // chatType (trade-like)
      putString(b, "Bander");
      putString(b, "hello everyone");
      send(b, logger);

      PacketLogger.ChatEvent e = logger.getLastChatEvent();
      assertNotNull(e);
      assertEquals("player", e.kind);
      assertEquals("Bander", e.speaker);
      assertEquals("hello everyone", e.text);
   }

   @Test
   public void chatEventsAreBoundedAndRetained()
   {
      PacketLogger logger = new PacketLogger("Bot");
      logger.setDatapackNames(fixtureNames());
      for (int i = 0; i < 10; i++)
      {
         ByteBuffer b = payload(0x4A);
         b.putInt(i);
         b.putInt(0);
         putString(b, "P" + i);
         putString(b, "msg" + i);
         send(b, logger);
      }
      List<PacketLogger.ChatEvent> events = logger.getChatEvents();
      assertEquals(10, events.size());
      assertEquals("P0", events.get(0).speaker);
      assertEquals("msg9", events.get(9).text);
   }
}
