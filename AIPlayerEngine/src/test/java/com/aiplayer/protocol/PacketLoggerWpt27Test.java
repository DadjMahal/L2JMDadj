package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WPT-27 — quest telemetry protocol: parse the top-level QUEST_LIST (0x80) "quest journal"
 * packet the server sends on enter-world. Verified Interlude layout (QuestList.java writeImpl):
 *   [count:2][per quest: questId:4][state:4]
 * The 4-byte state is a positive cond step (in-progress) or the completedStepFlags bitmask
 * (bit 31 set = some steps skipped). Exposed via getActiveQuestList()/getTotalQuestCount()/
 * getActiveQuestCount() so the fleet can surface the live journal in the v1 dashboard contract.
 */
public class PacketLoggerWpt27Test
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

   @Test
   public void questListParsesJournalEntriesAndCountsActiveQuests()
   {
      PacketLogger logger = new PacketLogger("Bot");

      // Q00255 tutorial (cond step 3 -> active), Q00001 (state 0 -> not in-progress),
      // Q00037 (completedStepFlags, bit 31 set -> counted active).
      ByteBuffer b = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
      b.put((byte) 0x80);             // QUEST_LIST opcode
      b.putShort((short) 3);          // quest count
      b.putInt(255); b.putInt(3);                       // active quest at step 3
      b.putInt(1);   b.putInt(0);                       // journal entry, but no progress
      b.putInt(37);  b.putInt(0x80000000 | 0x3FF);      // completedStepFlags with bit 31 set
      send(b, logger);

      assertEquals(1, logger.getQuestListCount());
      assertEquals(3, logger.getTotalQuestCount());
      // 255 (state 3) and 37 (flags non-zero) are active; 1 (state 0) is not.
      assertEquals(2, logger.getActiveQuestCount());

      List<int[]> journal = logger.getActiveQuestList();
      Map<Integer, Integer> byId = new HashMap<>();
      for (int[] e : journal) byId.put(e[0], e[1]);
      assertEquals(3, byId.size());
      assertEquals(3, byId.get(255));
      assertEquals(0, byId.get(1));
      assertTrue((byId.get(37) & 0x80000000) != 0);   // signed flag survives the parse
   }

   @Test
   public void questListReplacesJournalOnReParse()
   {
      PacketLogger logger = new PacketLogger("Bot");

      ByteBuffer first = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
      first.put((byte) 0x80);
      first.putShort((short) 1);
      first.putInt(255); first.putInt(2);
      send(first, logger);
      assertEquals(1, logger.getTotalQuestCount());

      // A fresh 0x80 with a different journal must REPLACE, not merge.
      ByteBuffer second = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
      second.put((byte) 0x80);
      second.putShort((short) 2);
      second.putInt(255); second.putInt(4);
      second.putInt(7);   second.putInt(6);
      send(second, logger);

      assertEquals(2, logger.getTotalQuestCount());
      assertEquals(2, logger.getActiveQuestCount());
      for (int[] e : logger.getActiveQuestList())
      {
         assertTrue(e[0] == 255 || e[0] == 7);
      }
   }
}
