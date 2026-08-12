package com.aiplayer.protocol;

import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WPT-24 — Inventory v2: ItemList (0x1B) parsing distinguishes equipped vs loose items using the
 * real 36-byte per-item writer layout (ItemList.java writeImpl -> AbstractItemPacket.writeItem):
 *   [type1:s][objectId:i][itemId:i][count:i][type2:s][customType1:s][equipped:s][slot:i]
 *   [enchant:s][customType2:s][augmentation:i][mana:i]
 * and resolves item display names via the WPT-29 datapack resolver.
 */
public class PacketLoggerWpt24Test
{
   private static DatapackNames fixtureNames()
   {
      URL url = DatapackNamesTest.class.getClassLoader()
         .getResource("com/aiplayer/protocol/datapack/stats");
      assertNotNull(url);
      return new DatapackNames(Paths.get(url.getPath()));
   }

   private static void writeItem(ByteBuffer b, int type1, int objectId, int itemId, long count,
                                 int type2, int equipped, int slot, int enchant)
   {
      b.putShort((short) type1);
      b.putInt(objectId);
      b.putInt(itemId);
      b.putInt((int) count);
      b.putShort((short) type2);
      b.putShort((short) 0);          // customType1 (filler)
      b.putShort((short) equipped);   // 0 = loose, 1 = equipped
      b.putInt(slot);                 // body-part mask
      b.putShort((short) enchant);
      b.putShort((short) 0);          // customType2
      b.putInt(0);                    // augmentation
      b.putInt(0);                    // mana
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
   public void itemListDistinguishesEquippedVsLooseAndResolvesNames()
   {
      PacketLogger logger = new PacketLogger("Bot");
      logger.setDatapackNames(fixtureNames());

      ByteBuffer b = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
      b.put((byte) 0x1B);
      b.putShort((short) 0);            // showWindow
      b.putShort((short) 2);            // item count
      writeItem(b, 1, 1001, 22, 1, 0, 1, 0x00000400, 0);    // equipped weapon (Sword of Reflection)
      writeItem(b, 4, 1002, 57, 1000, 0, 0, 0, 0);          // loose adena
      send(b, logger);

      assertEquals(1000, logger.getAdena());

      // Equipped vs loose classification.
      Set<Integer> equipped = logger.getEquippedItemIds();
      assertTrue(equipped.contains(22));
      assertFalse(equipped.contains(57));
      assertTrue(logger.isItemEquipped(22));
      assertFalse(logger.isItemEquipped(57));
      assertEquals(1, logger.getEquippedItemCount());

      // Per-item records carry identity, flag, slot and resolved names.
      List<PacketLogger.InventoryItem> records = logger.getInventoryRecords();
      assertEquals(2, records.size());
      PacketLogger.InventoryItem sword = records.get(0);
      assertEquals(22, sword.itemId);
      assertEquals(1L, sword.count);
      assertTrue(sword.equipped);
      assertEquals("Sword of Reflection", sword.name);
      assertEquals(0x00000400, sword.slot);

      PacketLogger.InventoryItem adena = records.get(1);
      assertEquals(57, adena.itemId);
      assertEquals(1000L, adena.count);
      assertFalse(adena.equipped);
      assertEquals("Adena", adena.name);

      // Legacy frozen contract map is still maintained.
      assertEquals(Long.valueOf(1000L), logger.getInventoryItems().get(57));
   }

   @Test
   public void itemListWithoutEquipmentYieldsNoEquippedItems()
   {
      PacketLogger logger = new PacketLogger("Bot");
      logger.setDatapackNames(fixtureNames());

      ByteBuffer b = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
      b.put((byte) 0x1B);
      b.putShort((short) 0);
      b.putShort((short) 1);
      writeItem(b, 4, 2001, 57, 5, 0, 0, 0, 0);
      send(b, logger);

      assertTrue(logger.getEquippedItemIds().isEmpty());
      assertEquals(0, logger.getEquippedItemCount());
      assertEquals(1, logger.getInventoryRecordCount());
   }
}
