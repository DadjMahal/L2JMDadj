package com.aiplayer.protocol;

import java.net.URL;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WPT-29 — datapack-backed NPC + item name resolution.
 *
 * <p>Loads the fixture datapack under src/test/resources/com/aiplayer/protocol/datapack/stats
 * (npcs/fixture.xml + items/fixture.xml) and asserts names resolve instead of the raw ids.
 */
public class DatapackNamesTest
{
   private static DatapackNames fixture()
   {
      URL url = DatapackNamesTest.class.getClassLoader()
         .getResource("com/aiplayer/protocol/datapack/stats");
      assertNotNull(url, "fixture stats dir must be present");
      return new DatapackNames(Paths.get(url.getPath()));
   }

   @Test
   public void resolvesKnownNpcDisplayNames()
   {
      DatapackNames names = fixture();
      assertEquals("Orc Fighter", names.resolveNpcName(20562));
      assertEquals("Wolf", names.resolveNpcName(12077));
      assertEquals("Warehouse Keeper Valkon", names.resolveNpcName(30330));
      assertTrue(names.npcCount() >= 3);
   }

   @Test
   public void fallsBackToMobTagForUnknownNpc()
   {
      DatapackNames names = fixture();
      assertEquals("mob#99999", names.resolveNpcName(99999));
      assertNull(names.resolveNpcNameOrNull(99999));
   }

   @Test
   public void resolvesKnownItemDisplayNames()
   {
      DatapackNames names = fixture();
      assertEquals("Adena", names.resolveItemName(57));
      assertEquals("Sword of Reflection", names.resolveItemName(22));
      assertTrue(names.itemCount() >= 2);
   }

   @Test
   public void fallsBackToItemTagForUnknownItem()
   {
      DatapackNames names = fixture();
      assertEquals("item#404", names.resolveItemName(404));
      assertNull(names.resolveItemNameOrNull(404));
   }

   @Test
   public void seededRegistrationsAreVisibleToResolvers()
   {
      DatapackNames names = new DatapackNames();
      names.registerNpcName(20562, "Orc Fighter");
      names.registerItemName(57, "Adena");
      assertEquals("Orc Fighter", names.resolveNpcName(20562));
      assertEquals("Adena", names.resolveItemName(57));
   }
}
