package com.aiplayer.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.aiplayer.cli.AIPlayerEngine;

/**
 * WPT-29 — datapack-backed entity / item name resolver.
 *
 * <p>Loads the L2JMobius Interlude datapack {@code stats/npcs/*.xml} and {@code stats/items/*.xml}
 * files into {@code id -> name} maps so telemetry can render {@code resolveNpcName(20562) ==
 * "Orc Fighter"} instead of {@code "mob#20562"} and {@code resolveItemName(57) == "Adena"} instead
 * of {@code "item#57"}.
 *
 * <p>The datapack XML shape (verified against SourceCode/dist/game/data/stats):
 * <pre>
 *   &lt;list&gt;
 *     &lt;npc id="12077" ... name="Wolf"&gt; ... &lt;/npc&gt;
 *   &lt;/list&gt;
 *   &lt;list&gt;
 *     &lt;item id="57" type="EtcItem" name="Adena"&gt; ... &lt;/item&gt;
 *   &lt;/list&gt;
 * </pre>
 *
 * <p>Construction is lazy: the default constructor resolves the datapack {@code stats} directory on
 * first lookup from a fixed list of candidate paths (repo root, CWD, and one level up, e.g. when
 * the engine runs from {@code AIPlayerEngine/}). It is a separate small class so it can be unit
 * tested against fixture XML ({@code DatapackNames(Path)} + {@code registerNpcName}). Loads are
 * idempotent and thread-safe.
 */
public class DatapackNames
{
   private static final Logger LOGGER = Logger.getLogger(DatapackNames.class.getName());

   private final Map<Integer, String> npcNames = new ConcurrentHashMap<>();
   private final Map<Integer, String> itemNames = new ConcurrentHashMap<>();
   private final Object loadLock = new Object();
   private volatile boolean loaded = false;

   /** Default resolver: lazily discovers the datapack stats dir and loads it on first lookup. */
   public DatapackNames()
   {
   }

   /**
    * Load names from a datapack {@code stats} directory that directly contains {@code npcs/} and
    * {@code items/} subfolders. Used for test fixtures and explicit wiring.
    */
   public DatapackNames(Path statsDir)
   {
      loadFrom(statsDir);
   }

   /** Load (or merge) npc + item names from a datapack stats dir. Idempotent. */
   public void loadFrom(Path statsDir)
   {
      if (statsDir == null || !Files.isDirectory(statsDir))
      {
         LOGGER.fine("[DATAPACK] stats dir not found: " + statsDir);
         return;
      }
      loadNpcDir(statsDir.resolve("npcs"));
      loadItemDir(statsDir.resolve("items"));
      loaded = true;
      LOGGER.info("[DATAPACK] loaded npcNames=" + npcNames.size() + " itemNames=" + itemNames.size());
   }

   /** Ensure the default resolver has attempted discovery at least once. */
   public void ensureLoaded()
   {
      if (loaded)
      {
         return;
      }
      synchronized (loadLock)
      {
         if (loaded)
         {
            return;
         }
         for (Path candidate : candidateStatsDirs())
         {
            if (Files.isDirectory(candidate.resolve("npcs")) || Files.isDirectory(candidate.resolve("items")))
            {
               loadFrom(candidate);
               return;
            }
         }
         // Nothing found: mark loaded with empty maps so we do not re-scan on every lookup.
         loaded = true;
         LOGGER.fine("[DATAPACK] no stats dir discovered; name resolution unavailable");
      }
   }

   private static List<Path> candidateStatsDirs()
   {
      List<Path> out = new ArrayList<>();
      String cwd = System.getProperty("user.dir", ".");
      addCandidate(out, cwd, "SourceCode", "dist", "game", "data", "stats");
      addCandidate(out, cwd, "ServerBuild", "game", "data", "stats");
      addCandidate(out, cwd, "..", "SourceCode", "dist", "game", "data", "stats");
      addCandidate(out, cwd, "..", "ServerBuild", "game", "data", "stats");
      // Repo-root absolute fallbacks (workspace layout: <root>/SourceCode/... ).
      out.add(Paths.get("/home/dadj/Projects/l24lude/SourceCode/dist/game/data/stats"));
      return out;
   }

   private static void addCandidate(List<Path> out, String base, String... parts)
   {
      String[] merged = new String[parts.length + 1];
      merged[0] = base;
      System.arraycopy(parts, 0, merged, 1, parts.length);
      out.add(Paths.get("", merged));
   }

   private void loadNpcDir(Path dir)
   {
      if (!Files.isDirectory(dir))
      {
         return;
      }
      try (Stream<Path> files = Files.list(dir))
      {
         files.filter(p -> p.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".xml"))
              .forEach(this::parseNpcFile);
      }
      catch (IOException e)
      {
         LOGGER.fine("[DATAPACK] npc dir scan failed: " + e.getMessage());
      }
   }

   private void loadItemDir(Path dir)
   {
      if (!Files.isDirectory(dir))
      {
         return;
      }
      try (Stream<Path> files = Files.list(dir))
      {
         files.filter(p -> p.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".xml"))
              .forEach(this::parseItemFile);
      }
      catch (IOException e)
      {
         LOGGER.fine("[DATAPACK] item dir scan failed: " + e.getMessage());
      }
   }

   private void parseNpcFile(Path file)
   {
      parseIdNameFile(file, "npc", npcNames);
   }

   private void parseItemFile(Path file)
   {
      parseIdNameFile(file, "item", itemNames);
   }

   private void parseIdNameFile(Path file, String tag, Map<Integer, String> target)
   {
      try
      {
         DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
         Document doc = builder.parse(file.toFile());
         NodeList nodes = doc.getElementsByTagName(tag);
         for (int i = 0; i < nodes.getLength(); i++)
         {
            Element el = (Element) nodes.item(i);
            String id = el.getAttribute("id");
            String name = el.getAttribute("name");
            if (id.isEmpty() || name.isEmpty())
            {
               continue;
            }
            try
            {
               target.putIfAbsent(Integer.parseInt(id.trim()), name);
            }
            catch (NumberFormatException ignored)
            {
               // non-numeric id: skip
            }
         }
      }
      catch (Exception e)
      {
         LOGGER.fine("[DATAPACK] parse failed " + file + ": " + e.getMessage());
      }
   }

   /**
    * WPT-29: resolve an NPC template id to its display name (e.g. 20562 -&gt; "Orc Fighter").
    * Falls back to {@code "mob#<id>"} when the datapack has no entry.
    */
   public String resolveNpcName(int npcId)
   {
      ensureLoaded();
      String name = npcNames.get(npcId);
      return name != null ? name : "mob#" + npcId;
   }

   /** Resolve an NPC id to a display name, or {@code null} when absent from the datapack. */
   public String resolveNpcNameOrNull(int npcId)
   {
      ensureLoaded();
      return npcNames.get(npcId);
   }

   /**
    * WPT-29/WPT-24: resolve an item template id to its display name (e.g. 57 -&gt; "Adena").
    * Falls back to {@code "item#<id>"} when the datapack has no entry.
    */
   public String resolveItemName(int itemId)
   {
      ensureLoaded();
      String name = itemNames.get(itemId);
      return name != null ? name : "item#" + itemId;
   }

   /** Resolve an item id to a display name, or {@code null} when absent from the datapack. */
   public String resolveItemNameOrNull(int itemId)
   {
      ensureLoaded();
      return itemNames.get(itemId);
   }

   /** Test/telemetry seeding hook: register one NPC name without touching the datapack. */
   public void registerNpcName(int npcId, String name)
   {
      if (name != null)
      {
         npcNames.put(npcId, name);
      }
   }

   /** Test/telemetry seeding hook: register one item name without touching the datapack. */
   public void registerItemName(int itemId, String name)
   {
      if (name != null)
      {
         itemNames.put(itemId, name);
      }
   }

   public int npcCount() { return npcNames.size(); }
   public int itemCount() { return itemNames.size(); }
}
