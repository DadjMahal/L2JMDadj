package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Packet Logger for Key Server Packets (Task 19)
 *
 * Logs server-to-client packets so AI can parse real game state.
 * Based on Documentation/Audit/04-gameserver-network.md and ServerPackets.java.
 *
 * Key packets: CHAR_INFO (0x03), STATUS_UPDATE (0x0E), NPC_INFO (0x16),
 * ITEM_LIST (0x1B), EX_QUEST_INFO (0xFE 0x19)
 *
 * Packet format: [2-byte size header][1-byte opcode][data]
 */
public class PacketLogger
{
   private static final Logger LOGGER = Logger.getLogger(PacketLogger.class.getName());

   // Server packet opcodes (from SourceCode ServerPackets.java)
   public static final int OP_CHAR_INFO = 0x03;
   public static final int OP_STATUS_UPDATE = 0x0E;
   public static final int OP_DELETE_OBJECT = 0x12;
   public static final int OP_NPC_INFO = 0x16;
   public static final int OP_ITEM_LIST = 0x1B;
   public static final int OP_SYSTEM_MESSAGE = 0x64;
   public static final int OP_EX_PACKET = 0xFE;
   public static final int OP_EX_QUEST_INFO = 0x19;
   // Stream C7/C8: NpcHtmlMessage (0x0F) — the dialog the server SHOWS the player.
   // Every valid RequestBypassToServer must reference a bypass link that was present in a
   // previously-sent NpcHtmlMessage (server validates via validateHtmlAction), so the engine
   // MUST parse these to know what bypass it is allowed to send next.
   public static final int OP_NPC_HTML = 0x0F;

   // StatusUpdate attribute IDs (from StatusUpdate.java)
   public static final int STAT_LEVEL = 0x01;
   public static final int STAT_CUR_HP = 0x09;
   public static final int STAT_MAX_HP = 0x0A;
   public static final int STAT_CUR_MP = 0x0B;
   public static final int STAT_MAX_MP = 0x0C;
   public static final int STAT_CUR_CP = 0x21;
   public static final int STAT_MAX_CP = 0x22;

   private final String playerName;
   private int packetsLogged = 0;
   private int charInfoCount = 0;
   private int statusUpdateCount = 0;
   private int npcInfoCount = 0;
   private int itemListCount = 0;
   private int questInfoCount = 0;

   // HP/MP/CP tracking (Task 48, 49)
   // Slice 6: these are SELF values. The server sends StatusUpdate for MANY entities (the player
   // AND every monster around), so we only update them when the packet's objId is the self object id
   // (set via setSelfObjectId, e.g. the character's objId from CharSelected). Without this filter a
   // target wolf's StatusUpdate would clobber the bot's HP.
   private int selfObjectId = 0;   // 0 = not set: keep legacy behavior (track first/any StatusUpdate)
   private int curHp = 100;
   private int maxHp = 100;
   private int curMp = 30;
   private int maxMp = 30;

   
   // Position tracking (Task 33, 34)
   private int playerX = 0;
   private int playerY = 0;
   private int playerZ = 0;
   private int playerHeading = 0;
   
   // Inventory tracking (Task 33, 35)
   private int adena = 0;
   private int inventoryUsagePercent = 0;
   
   // Quest tracking (Task 36)
   private int activeQuestCount = 0;

   // Stream C7/C8: NPC dialog tracking. The last NpcHtmlMessage the server showed us.
   private String lastNpcHtml = null;
   private int lastNpcHtmlOriginObjId = 0;
   private int npcHtmlCount = 0;
   
   // Entity tracking (Task 47)
   private final ConcurrentHashMap<Integer, EntityInfo> entitiesById = new ConcurrentHashMap<>();

   /**
    * Create a packet logger for a specific AI player.
    * @param playerName the name of the AI player this logger tracks
    */
   public PacketLogger(String playerName)
   {
      this.playerName = playerName;
   }

   /**
    * Log a received server packet.
    * @param packetData The raw packet bytes including 2-byte size header
    */
   public void logPacket(byte[] packetData)
   {
      if (packetData == null || packetData.length < 3)
      {
         return;
      }

      try
      {
         ByteBuffer buf = ByteBuffer.wrap(packetData);
         buf.order(ByteOrder.LITTLE_ENDIAN);

         int size = buf.getShort() & 0xFFFF;
         if (size < 1) return;

         int opcode = buf.get() & 0xFF;
         packetsLogged++;

         switch (opcode)
         {
            case OP_CHAR_INFO:
               charInfoCount++;
               parseCharInfo(buf);
               break;
            case OP_STATUS_UPDATE:
               statusUpdateCount++;
               parseStatusUpdate(buf);
               break;
            case OP_NPC_INFO:
               npcInfoCount++;
               parseNpcInfo(buf);
               break;
            case OP_ITEM_LIST:
               itemListCount++;
               parseItemList(buf);
               break;
            case OP_EX_PACKET:
               parseExPacket(buf);
               break;
            case OP_DELETE_OBJECT:
               parseDeleteObject(buf);
               break;
            case OP_NPC_HTML:
               npcHtmlCount++;
               parseNpcHtml(buf);
               break;
            case OP_SYSTEM_MESSAGE:
               parseSystemMessage(buf);
               break;
            default:
               LOGGER.fine("[" + playerName + "] Unknown packet: 0x" + Integer.toHexString(opcode) + " size=" + size);
         }
      }
      catch (Exception e)
      {
         LOGGER.warning("[" + playerName + "] Packet parse error: " + e.getMessage());
      }
   }

   /**
    * Parse CharInfo packet (opcode 0x03) - position, heading
    */
   private void parseCharInfo(ByteBuffer buf)
   {
      try
      {
         int objectId = buf.getInt();
         this.playerX = buf.getInt();
         this.playerY = buf.getInt();
         this.playerZ = buf.getInt();
         this.playerHeading = buf.getInt();
         LOGGER.info("[PACKET-LOG] [" + playerName + "] CHAR_INFO: objId=" + objectId + " pos=(" + playerX + "," + playerY + "," + playerZ + ")");
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] CharInfo parse incomplete");
      }
   }

   /**
    * Parse StatusUpdate packet (opcode 0x0E) - HP/MP/CP/Level/EXP
    */
   private void parseStatusUpdate(ByteBuffer buf)
   {
      try
      {
         int objectId = buf.getInt();
         int attributeCount = buf.getInt();
         StringBuilder attrs = new StringBuilder();
         // Slice 6: only let THIS entity's StatusUpdate drive the bot's own HP/MP when it is the self
         // object id (or when selfObjectId is unset, to preserve legacy single-threaded test behavior).
         boolean isSelf = selfObjectId == 0 || objectId == selfObjectId;
         for (int i = 0; i < attributeCount && buf.remaining() >= 8; i++)
         {
            int attrId = buf.getInt();
            int value = buf.getInt();
            attrs.append(getAttributeName(attrId)).append("=").append(value);
            if (i < attributeCount - 1) attrs.append(", ");
            
            // Track HP/MP values (self only; a target's StatusUpdate must not overwrite the bot's HP)
            if (isSelf && attrId == STAT_CUR_HP) curHp = value;
            if (isSelf && attrId == STAT_MAX_HP) maxHp = value;
            if (isSelf && attrId == STAT_CUR_MP) curMp = value;
            if (isSelf && attrId == STAT_MAX_MP) maxMp = value;
         }
         LOGGER.info("[PACKET-LOG] [" + playerName + "] STATUS_UPDATE: objId=" + objectId
            + " [self=" + isSelf + "] [" + attrs + "] hp=" + curHp + "/" + maxHp + " mp=" + curMp + "/" + maxMp);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] StatusUpdate parse incomplete");
      }
   }

   /**
    * Parse NpcInfo packet (opcode 0x16) - monster position, attackable
    */
   private void parseNpcInfo(ByteBuffer buf)
   {
      try
      {
         // Stream C: fixed the OFF-BY-ONE vs the real Interlude AbstractNpcInfo layout
         // (proven by B4 / Audit/35). Real field order after the 0x16 opcode:
         //    [objectId][displayId+1000000][isAttackable][x][y][z][heading]...
         // The old code read [objectId][id][x][y][z], so it misread the packet's
         // isAttackable as x (position was shifted 4 bytes early). CombatProbe proved
         // the working offsets: isAttackable@9, x@13, y@17, z@21.
         int objectId = buf.getInt();          // offset 1
         int displayId = buf.getInt();         // offset 5 : real template id + 1000000
         int isAttackable = buf.getInt();      // offset 9 : packet-derived flag (0 or 1)
         int x = buf.getInt();                 // offset 13
         int y = buf.getInt();                 // offset 17
         int z = buf.getInt();                 // offset 21
         int heading = buf.remaining() >= 4 ? buf.getInt() : 0; // offset 25 (optional tail)

         int npcId = displayId - 1000000;      // real NPC template id
         // The real packet gives isAttackable directly — better than the ID-range heuristic
         // (Audit/35). Keep the heuristic as a fallback for monsters not flagged in this field.
         boolean isHostile = isAttackable != 0 || isHostileNpc(npcId);

         // Track entity for enemy detection (Task 47)
         entitiesById.put(objectId, new EntityInfo(objectId, npcId, x, y, z, heading, isHostile));

         LOGGER.info("[PACKET-LOG] [" + playerName + "] NPC_INFO: objId=" + objectId + " npcId=" + npcId + " attackable=" + isAttackable + " pos=(" + x + "," + y + "," + z + ") heading=" + heading + " hostile=" + isHostile);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] NpcInfo parse incomplete");
      }
   }
   
   /**
    * Determine if NPC ID represents a hostile creature
    * Based on L2J typical NPC ID ranges
    */
   private boolean isHostileNpc(int npcId) {
      // Hostile NPCs typically have IDs in these ranges:
      // 1-199999: Standard monsters (varies by region)
      // 210000-210999: Beasts (usually hostile)
      // 800000-899999: Event monsters
      
      // For now, we'll check common hostile ranges
      // In production, this would be extended with actual L2JMobius data
      if (npcId >= 1 && npcId < 200000) return true;  // Most monsters
      if (npcId >= 210000 && npcId < 220000) return true;  // Beasts
      if (npcId >= 800000) return true;  // Event monsters
      return false;  // NPCs, guards, etc.
   }

   /**
    * Parse ItemList packet (opcode 0x1B) - inventory contents
    */
   private void parseItemList(ByteBuffer buf)
   {
      try
      {
         int showWindow = buf.getShort() & 0xFFFF;
         int itemCount = buf.getShort() & 0xFFFF;
         
         // Calculate inventory usage
         // Total slots would be from character configuration, estimate based on itemCount
         int totalSlots = 120; // Typical L2J max inventory + equipment slots
         this.inventoryUsagePercent = (int)((itemCount * 100.0) / totalSlots);
         if (inventoryUsagePercent > 100) inventoryUsagePercent = 100;
         
         LOGGER.info("[PACKET-LOG] [" + playerName + "] ITEM_LIST: showWindow=" + showWindow + " itemCount=" + itemCount + " usage=" + inventoryUsagePercent + "%");
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] ItemList parse incomplete");
      }
   }

   /**
    * Parse Ex packet (opcode 0xFE) - routes to sub-packet handler
    */
   private void parseExPacket(ByteBuffer buf)
   {
      try
      {
         int subOpcode = buf.getShort() & 0xFFFF;
         switch (subOpcode)
         {
            case OP_EX_QUEST_INFO:
               questInfoCount++;
               parseQuestInfo(buf);
               break;
            default:
               LOGGER.fine("[" + playerName + "] Unknown Ex: 0x" + Integer.toHexString(subOpcode));
         }
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] Ex packet parse incomplete");
      }
   }

   /**
    * Parse QuestInfo Ex packet (0xFE 0x19) - quest states
    */
   private void parseQuestInfo(ByteBuffer buf)
   {
      try
      {
         // QuestInfo contains list of quests with state
         // Format: [questCount: 2 bytes][for each quest: questId: 4 bytes state: 1 byte]
         int questCount = buf.getShort() & 0xFFFF;
         this.activeQuestCount = 0;
         
         for (int i = 0; i < questCount; i++) {
            int questId = buf.getInt();
            byte state = buf.get();
            
            // Count active quests (states 1-4 are active, 0 is not started, 5 is completed)
            if (state > 0 && state < 5) {
               activeQuestCount++;
            }
         }
         
         LOGGER.info("[PACKET-LOG] [" + playerName + "] QUEST_INFO: count=" + questCount + " active=" + activeQuestCount);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] QuestInfo parse incomplete");
      }
   }

   /**
    * Parse DeleteObject packet (0x12) - remove entity from tracking
    */
   private void parseDeleteObject(ByteBuffer buf)
   {
      try
      {
         int objectId = buf.getInt();
         
         // Remove entity from tracking map (Task 38). INFO level so the live proof can show the target
         // despawning (Slice 6 feedback: DeleteObject drives end-of-combat and re-targeting).
         EntityInfo removed = entitiesById.remove(objectId);
         if (removed != null) {
            LOGGER.info("[PACKET-LOG] [" + playerName + "] DELETE_OBJECT: objId=" + objectId + " (removed " + removed.npcId + ")");
         } else {
            LOGGER.info("[PACKET-LOG] [" + playerName + "] DELETE_OBJECT: objId=" + objectId + " (not tracked)");
         }
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] DeleteObject parse incomplete");
      }
   }

   /**
    * Parse NpcHtmlMessage (opcode 0x0F): the dialog the server shows the player.
    * Wire layout (L2JMobius NpcHtmlMessage.writeImpl):
    *    [npcObjId:int][html UTF-16LE null-terminated][itemId:int]
    * The html body contains the links the player may click. The server ONLY accepts a
    * RequestBypassToServer whose command string was previously shown in one of these html
    * dialogs (validateHtmlAction), so the engine stores the raw html + origin NPC objId and
    * exposes extractBypassLinks() so the quest driver can follow the real dialog chain.
    */
   private void parseNpcHtml(ByteBuffer buf)
   {
      try
      {
         if (buf.remaining() < 4) return;
         int npcObjId = buf.getInt();
         // html is UTF-16LE, null-terminated ('\0' as a 16-bit char)
         StringBuilder sb = new StringBuilder();
         while (buf.remaining() >= 2)
         {
            char c = buf.getChar();
            if (c == 0) break;
            sb.append(c);
         }
         String html = sb.toString();
         this.lastNpcHtml = html;
         this.lastNpcHtmlOriginObjId = npcObjId;
         LOGGER.info("[PACKET-LOG] [" + playerName + "] NPC_HTML: npcObjId=" + npcObjId
            + " htmlLen=" + html.length() + " bypassLinks=" + extractBypassLinks(html).length
            + " excerpt=\"" + (html.length() > 120 ? html.substring(0, 120) : html) + "\"");
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] NpcHtml parse incomplete: " + e.getMessage());
      }
   }

   /** Extract the full command strings of every "bypass ..." link inside an NPC html dialog. */
   public static String[] extractBypassLinks(String html)
   {
      if (html == null || html.isEmpty()) return new String[0];
      java.util.List<String> links = new java.util.ArrayList<>();
      String lower = html.toLowerCase(java.util.Locale.ROOT);
      int idx = 0;
      while (idx < html.length())
      {
         int pos = lower.indexOf("bypass", idx);
         if (pos < 0) break;
         // Require a word boundary: "bypass" must be followed by whitespace, a quote, or a '<'
         // so a word like "bypassed" is NOT treated as a bypass command.
         int boundary = pos + "bypass".length();
         if (boundary < html.length())
         {
            char next = html.charAt(boundary);
            boolean isWordChar = !Character.isWhitespace(next) && next != '\"' && next != '<' && next != '>';
            if (isWordChar)
            {
               idx = boundary;
               continue;
            }
         }
         // The real link is: action="bypass -h <cmd>" or action="bypass <cmd>".
         // Strip the optional "-h" (hidden) mode flag so the sendable command matches the
         // exact string the server cached in validateHtmlAction (which keeps the -h OUT).
         int after = boundary;
         while (after < html.length() && Character.isWhitespace(html.charAt(after))) after++;
         if (after < html.length() && html.charAt(after) == '-')
         {
            after++; // consume '-'
            while (after < html.length() && !Character.isWhitespace(html.charAt(after))) after++; // consume 'h'
         }
         while (after < html.length() && Character.isWhitespace(html.charAt(after))) after++;
         StringBuilder cmd = new StringBuilder();
         while (after < html.length() && html.charAt(after) != '\"' && html.charAt(after) != '<'
            && html.charAt(after) != '>' && html.charAt(after) != '\\')
         {
            cmd.append(html.charAt(after));
            after++;
         }
         String link = cmd.toString().trim();
         if (!link.isEmpty()) links.add(link);
         idx = Math.max(pos + 1, after);
      }
      return links.toArray(new String[0]);
   }

   /** The full html of the most recent NPC dialog the server showed this player. */
   public String getLastNpcHtml() { return lastNpcHtml; }

   /** Object id of the NPC that sent the most recent html dialog. */
   public int getLastNpcHtmlOriginObjId() { return lastNpcHtmlOriginObjId; }

   public int getNpcHtmlCount() { return npcHtmlCount; }

   /** Parse SystemMessage packet (0x64) */
   private void parseSystemMessage(ByteBuffer buf)
   {
      LOGGER.info("[PACKET-LOG] [" + playerName + "] SYSTEM_MESSAGE received");
   }

   /**
    * Get human-readable name for StatusUpdate attribute IDs.
    */
   private String getAttributeName(int attrId)
   {
      switch (attrId)
      {
         case STAT_LEVEL: return "LEVEL";
         case STAT_CUR_HP: return "CUR_HP";
         case STAT_MAX_HP: return "MAX_HP";
         case STAT_CUR_MP: return "CUR_MP";
         case STAT_MAX_MP: return "MAX_MP";
         case STAT_CUR_CP: return "CUR_CP";
         case STAT_MAX_CP: return "MAX_CP";
         default: return "ATTR_" + Integer.toHexString(attrId);
      }
   }

   /**
    * Get telemetry summary for this player's packet logging.
    * @return formatted telemetry summary string
    */
   public String getTelemetrySummary()
   {
      return "[PACKET-LOG-TELEMETRY] [" + playerName + "] " + "total=" + packetsLogged + " charInfo=" + charInfoCount + " statusUpdate=" + statusUpdateCount + " npcInfo=" + npcInfoCount + " itemList=" + itemListCount + " questInfo=" + questInfoCount;
   }

   /** Log telemetry summary. */
   public void logTelemetry()
   {
      LOGGER.info(getTelemetrySummary());
   }

   // Telemetry getters
   public int getTotalPacketsLogged() { return packetsLogged; }
   public int getCharInfoCount() { return charInfoCount; }
   public int getStatusUpdateCount() { return statusUpdateCount; }
   public int getNpcInfoCount() { return npcInfoCount; }
   public int getItemListCount() { return itemListCount; }
   
   // HP/MP tracking getters (Task 48, 49)
   public int getSelfObjectId() { return selfObjectId; }

   /** Slice 6: tell the logger which object id is the bot itself so StatusUpdate self-tracking is safe. */
   public void setSelfObjectId(int selfObjectId) { this.selfObjectId = selfObjectId; }

   public int getCurHp() { return curHp; }
   /** Stream D: test/telemetry hook to inject a live HP value (normally set by StatusUpdate parse). */
   public void setCurHp(int hp) { this.curHp = hp; }
   /** Stream D: test/telemetry hook to inject a live max-HP value. */
   public void setMaxHp(int hp) { this.maxHp = hp; }
   public int getMaxHp() { return maxHp; }
   public int getCurMp() { return curMp; }
   public int getMaxMp() { return maxMp; }
   public double getHpPercentage() { return maxHp > 0 ? (double) curHp / maxHp * 100 : 0; }
   public double getMpPercentage() { return maxMp > 0 ? (double) curMp / maxMp * 100 : 0; }
   
   // Position tracking getters (Task 34)
   public int getPlayerX() { return playerX; }
   public int getPlayerY() { return playerY; }
   public int getPlayerZ() { return playerZ; }
   public int getPlayerHeading() { return playerHeading; }
   
   // Inventory tracking getters (Task 35)
   public int getAdena() { return adena; }
   public int getInventoryUsagePercent() { return inventoryUsagePercent; }
   public boolean isInventoryFull() { return inventoryUsagePercent >= 90; }
   public boolean hasSpaceForTrade(int itemCount) { return inventoryUsagePercent + (itemCount * 100 / 120) < 90; }
   
   // Quest tracking getters (Task 36)
   public int getActiveQuestCount() { return activeQuestCount; }
   public int getQuestInfoCount() { return questInfoCount; }
   
   // Entity tracking getters (Task 47)
   public int getEntityCount() { return entitiesById.size(); }
   public EntityInfo getEntity(int objectId) { return entitiesById.get(objectId); }
   /** Stream C7: find a tracked entity by its NPC template id (e.g. quest-giver NPC). */
   public EntityInfo findEntityByNpcId(int npcId) {
      for (EntityInfo e : entitiesById.values()) {
         if (e.npcId == npcId) return e;
      }
      return null;
   }

   /** Test/telemetry hook: seed an entity (mirrors what live NPC_INFO parsing does). */
   public void addEntityForTest(EntityInfo entity) {
      entitiesById.put(entity.objectId, entity);
   }
   public EntityInfo[] getHostileEntities() { return entitiesById.values().stream().filter(e -> e.isHostile).toArray(EntityInfo[]::new); }
   public EntityInfo findNearestHostile(int playerX, int playerY, int playerZ, int maxDistance) {
      EntityInfo nearest = null;
      double nearestDistSq = (double) maxDistance * maxDistance;
      for (EntityInfo entity : entitiesById.values()) {
         if (!entity.isHostile) continue;
         double distSq = Math.pow(entity.x - playerX, 2) + Math.pow(entity.y - playerY, 2) + Math.pow(entity.z - playerZ, 2);
         if (distSq < nearestDistSq) {
            nearestDistSq = distSq;
            nearest = entity;
         }
      }
      return nearest;
   }
   
    
    /**
     * Get all entities within a radius of the player.
     * Used for advanced escape route calculation.
     * @param centerX center X coordinate
     * @param centerY center Y coordinate
     * @param radius search radius
     * @return array of nearby entities
     */
    public EntityInfo[] getNearbyEntities(int centerX, int centerY, int radius) {
        int radiusSq = radius * radius;
        return entitiesById.values().stream()
            .filter(e -> Math.pow(e.x - centerX, 2) + Math.pow(e.y - centerY, 2) <= radiusSq)
            .toArray(EntityInfo[]::new);
    }
    
    /**
     * Find nearest entity to player (any entity).
     * @param playerX player X coordinate
     * @param playerY player Y coordinate
     * @param playerZ player Z coordinate
     * @param maxDistance maximum search distance
     * @return nearest entity or null
     */
    public EntityInfo findNearestEntity(int playerX, int playerY, int playerZ, int maxDistance) {
        EntityInfo nearest = null;
        double nearestDistSq = (double) maxDistance * maxDistance;
        for (EntityInfo entity : entitiesById.values()) {
            double distSq = Math.pow(entity.x - playerX, 2) + Math.pow(entity.y - playerY, 2) + Math.pow(entity.z - playerZ, 2);
            if (distSq < nearestDistSq) {
               nearestDistSq = distSq;
               nearest = entity;
            }
        }
        return nearest;
    }
    
    /**
     * Check if there are any hostile entities nearby.
     * @param playerX player X coordinate
     * @param playerY player Y coordinate
     * @param playerZ player Z coordinate
     * @param maxDistance maximum distance to check
     * @return true if hostile entities nearby
     */
    public boolean hasHostileNearby(int playerX, int playerY, int playerZ, int maxDistance) {
        return findNearestHostile(playerX, playerY, playerZ, maxDistance) != null;
    }
    
    /**
     * Clear all tracked entities (useful for zone changes).
     */
    public void clearEntities() {
        int count = entitiesById.size();
        entitiesById.clear();
        LOGGER.info("[PACKET-LOG] [" + playerName + "] CLEARED_ENTITIES: " + count + " entities removed");
    }
    
    /**
     * Get count of hostile entities only.
     */
    public int getHostileEntityCount() {
        return (int) entitiesById.values().stream().filter(e -> e.isHostile).count();
    }
    
    // Inner class for tracked entity info
   public static class EntityInfo {
      public final int objectId;
      public final int npcId;
      public int x, y, z, heading;
      public boolean isHostile;
      
      public EntityInfo(int objectId, int npcId, int x, int y, int z, int heading, boolean isHostile) {
         this.objectId = objectId;
         this.npcId = npcId;
         this.x = x;
         this.y = y;
         this.z = z;
         this.heading = heading;
         this.isHostile = isHostile;
      }
      
      @Override
      public String toString() {
         return "Entity[" + (isHostile ? "HOSTILE" : "NEUTRAL") + "] objId=" + objectId + 
                " npcId=" + npcId + " pos=(" + x + "," + y + "," + z + ")";
      }
   }
}
