package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.aiplayer.protocol.PacketLogger.SystemMessageEvent;

/**
 * S2-T06 — quest journal + system-message telemetry combined: a QUEST_LIST (0x80) frame
 * parsed with the verified WPT-27 layout [count:2][per quest: questId:4][state:4], and a
 * SystemMessage (0x64) frame parsed with the verified WPT-22 layout
 * [msgId:int][paramCount:int][(type:int, value)*] so the sysmsg id is captured.
 */
public class PacketLoggerQuestSysmsgTest
{
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
   public void questListParsesActiveQuestCountAndIds()
   {
      PacketLogger logger = new PacketLogger("Bot");

      // WPT-27 layout: Q00255 tutorial (cond step 3 -> active), Q00001 (state 0 -> not active),
      // Q00037 (completedStepFlags, bit 31 set -> active).
      ByteBuffer b = payload(0x80);
      b.putShort((short) 3);          // quest count
      b.putInt(255); b.putInt(3);
      b.putInt(1);   b.putInt(0);
      b.putInt(37);  b.putInt(0x80000000 | 0x3FF);
      send(b, logger);

      assertEquals(1, logger.getQuestListCount());
      assertEquals(3, logger.getTotalQuestCount());
      assertEquals(2, logger.getActiveQuestCount(), "255 and 37 active, 1 not");

      List<int[]> journal = logger.getActiveQuestList();
      Map<Integer, Integer> byId = new HashMap<>();
      for (int[] e : journal) byId.put(e[0], e[1]);
      assertEquals(3, byId.size());
      assertEquals(3, byId.get(255));
      assertEquals(0, byId.get(1));
      assertTrue((byId.get(37) & 0x80000000) != 0, "signed completedStepFlags must survive the parse");
   }

   @Test
   public void systemMessageCapturesSysmsgId()
   {
      PacketLogger logger = new PacketLogger("Bot");

      // WPT-22 layout, no params: the id alone must be captured.
      ByteBuffer b = payload(0x64);
      b.putInt(43);       // msgId
      b.putInt(0);        // paramCount
      send(b, logger);

      PacketLogger.SystemMessageEvent e = logger.getLastSystemMessage();
      assertNotNull(e, "system message event must be recorded");
      assertEquals(43, e.msgId, "sysmsg id must be captured");
      assertTrue(e.text.startsWith("sysmsg#43"));
      assertEquals(1, logger.getSystemMessageCount());
   }
}
