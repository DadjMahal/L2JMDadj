package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stream C7/C8 regression tests for the GENUINE NPC-talk quest driver support:
 *  - PacketLogger.parseNpcHtml (opcode 0x0F) — the server only accepts a RequestBypassToServer
 *    whose command was previously SHOWN to the player in an NpcHtmlMessage (validateHtmlAction).
 *    Wire layout: [npcObjId:int][html UTF-16LE null-terminated][itemId:int].
 *  - PacketLogger.extractBypassLinks — pulls the exact "bypass ..." command strings from a dialog.
 *  - PacketLogger.findEntityByNpcId — locates the quest-giver NPC from live NPC_INFO tracking.
 */
public class PacketLoggerNpcHtmlTest
{
   private static final int OP_NPC_HTML = 0x0F;

   /** Build a real NpcHtmlMessage frame: [2-byte size][0x0F][npcObjId:int][html UTF-16LE NUL][itemId:int]. */
   private static byte[] buildNpcHtmlFrame(int npcObjId, String html, int itemId)
   {
      byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_16LE);
      int payloadLen = 1 + 4 + htmlBytes.length + 2 + 4; // opcode + objId + string + NUL + itemId
      ByteBuffer payload = ByteBuffer.allocate(payloadLen).order(ByteOrder.LITTLE_ENDIAN);
      payload.put((byte) OP_NPC_HTML);
      payload.putInt(npcObjId);
      payload.put(htmlBytes);
      payload.putChar('\0');
      payload.putInt(itemId);

      int frameLen = payloadLen + 2;
      ByteBuffer frame = ByteBuffer.allocate(frameLen).order(ByteOrder.LITTLE_ENDIAN);
      frame.putShort((short) frameLen);
      frame.put(payload.array());
      return frame.array();
   }

   @Test
   public void testParsesDefaultRoienDialog()
   {
      // The exact default html the server shows when you Action(0x04) Roien (30008).
      String html = "<html><body>Grand Master Roien:<br>Welcome...<br>"
         + "<a action=\"bypass Script\">Quest</a></body></html>";
      PacketLogger logger = new PacketLogger("ai_combat_01");
      logger.logPacket(buildNpcHtmlFrame(12345, html, 0));

      assertEquals(1, logger.getNpcHtmlCount());
      assertNotNull(logger.getLastNpcHtml());
      assertEquals(12345, logger.getLastNpcHtmlOriginObjId());
      assertTrue(logger.getLastNpcHtml().contains("Grand Master Roien"));
      String[] links = PacketLogger.extractBypassLinks(logger.getLastNpcHtml());
      assertEquals(1, links.length);
      assertEquals("Script", links[0]);
   }

   @Test
   public void testExtractsQuestBypassChainFromRealHtml()
   {
      // The real Q00101 dialog chain: 30008-02.htm -> 02a -> 02b -> 03 (startQuest).
      String htm = "<html><body><a action=\"bypass Script Q00101_SwordOfSolidarity 30008-02a.htm\">Ask</a>"
         + "<a action=\"bypass -h Script Q00101_SwordOfSolidarity 30008-02b.htm\">More</a></body></html>";
      String[] links = PacketLogger.extractBypassLinks(htm);
      assertEquals(2, links.length);
      assertEquals("Script Q00101_SwordOfSolidarity 30008-02a.htm", links[0]);
      // The "-h" (hidden) flag must be stripped so the sendable command matches the server's cache.
      assertEquals("Script Q00101_SwordOfSolidarity 30008-02b.htm", links[1]);
   }

   @Test
   public void testExtractBypassLinksIgnoresNonBypassText()
   {
      String html = "<html><body>No links here, just the word bypassed in prose.</body></html>";
      String[] links = PacketLogger.extractBypassLinks(html);
      assertArrayEquals(new String[0], links);
   }

   @Test
   public void testFindEntityByNpcIdLocatesQuestGiver()
   {
      PacketLogger logger = new PacketLogger("ai_combat_01");
      // Seed a tracked entity directly (as live NPC_INFO parsing would): Roien = npcId 30008.
      logger.getEntity(0); // no-op, ensures map exists
      PacketLogger.EntityInfo roien = new PacketLogger.EntityInfo(12345, 30008, -71384, 258304, -3104, 42000, false);
      logger.addEntityForTest(roien);

      PacketLogger.EntityInfo found = logger.findEntityByNpcId(30008);
      assertNotNull(found);
      assertEquals(12345, found.objectId);
      assertEquals(30008, found.npcId);
      assertEquals(-71384, found.x);
      assertNotNull(logger.findEntityByNpcId(30008));
      assertEquals(null, logger.findEntityByNpcId(99999));
   }
}
