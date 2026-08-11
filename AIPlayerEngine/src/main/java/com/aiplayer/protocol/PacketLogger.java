package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
   public static final int OP_USER_INFO = 0x04;
   public static final int OP_STATUS_UPDATE = 0x0E;
   public static final int OP_DELETE_OBJECT = 0x12;
   public static final int OP_NPC_INFO = 0x16;
   public static final int OP_ITEM_LIST = 0x1B;
   public static final int OP_VALIDATE_LOCATION = 0x61;
   public static final int OP_SYSTEM_MESSAGE = 0x64;
   public static final int OP_EX_PACKET = 0xFE;
   public static final int OP_EX_QUEST_INFO = 0x19;
   // Stream C7/C8: NpcHtmlMessage (0x0F) — the dialog the server SHOWS the player.
   // Every valid RequestBypassToServer must reference a bypass link that was present in a
   // previously-sent NpcHtmlMessage (server validates via validateHtmlAction), so the engine
   // MUST parse these to know what bypass it is allowed to send next.
   public static final int OP_NPC_HTML = 0x0F;
   // WPT-25/26: server combat + skill-cast packets (opcodes verified against
   // SourceCode/java/org/l2jmobius/gameserver/network/ServerPackets.java).
   public static final int OP_ATTACK = 0x05;               // melee hit feed (Attack.java)
   public static final int OP_DIE = 0x06;                  // entity died (Die.java)
   public static final int OP_REVIVE = 0x07;               // entity revived (Revive.java)
   public static final int OP_MAGIC_SKILL_USE = 0x48;      // confirmed skill cast (MagicSkillUse.java)
   public static final int OP_MAGIC_SKILL_LAUNCHED = 0x76; // cast projectile/hit (MagicSkillLaunched.java)
   // WPT-26: client->server skill-cast REQUEST gate (Audit/42: REQUEST_MAGIC_SKILL_USE 0x2F,
   // raw frame [skillId:int][ctrl:int][shift:byte]). The bot SENDS this; the server's
   // MagicSkillUse(0x48) is the confirmed echo - see recordSkillRequest().
   public static final int CLIENT_OP_REQUEST_MAGIC_SKILL_USE = 0x2F;

   // StatusUpdate attribute IDs (from StatusUpdate.java)
   public static final int STAT_LEVEL = 0x01;
   public static final int STAT_EXP = 0x02;
   public static final int STAT_CUR_HP = 0x09;
   public static final int STAT_MAX_HP = 0x0A;
   public static final int STAT_CUR_MP = 0x0B;
   public static final int STAT_MAX_MP = 0x0C;
   public static final int STAT_SP = 0x0D;
   public static final int STAT_CUR_LOAD = 0x0E;
   public static final int STAT_MAX_LOAD = 0x0F;
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
   // Stream G (G-Live): level parsed from StatusUpdate (STAT_LEVEL 0x01), so the live loop can
   // detect level-ups and fire CombatAI.onLevelUp / long-term-goal progress (was never parsed).
   private int level = 0;
   // Real self-state parsed from UserInfo (0x04) / StatusUpdate attrs / ValidateLocation (0x61).
   private long exp = 0;
   private int sp = 0;
   private int curLoad = 0;
   private int maxLoad = 0;
   private boolean weaponEquipped = false;
   private int baseClass = 0;
   private String charName = null;
   private int curCp = 100;
   private int maxCp = 100;


   // Position tracking (Task 33, 34)
   private int playerX = 0;
   private int playerY = 0;
   private int playerZ = 0;
   private int playerHeading = 0;

   // Inventory tracking (Task 33, 35)
   private int adena = 0;
   private int inventoryUsagePercent = 0;
   // Stream E (task 78): real inventory contents parsed from ItemList(0x1B). itemId -> count.
   private final java.util.Map<Integer, Long> inventoryItems = new java.util.concurrent.ConcurrentHashMap<>();

   // Quest tracking (Task 36)
   private int activeQuestCount = 0;

   // Stream C7/C8: NPC dialog tracking. The last NpcHtmlMessage the server showed us.
   private String lastNpcHtml = null;
   private int lastNpcHtmlOriginObjId = 0;
   private int npcHtmlCount = 0;

   // Entity tracking (Task 47)
   private final ConcurrentHashMap<Integer, EntityInfo> entitiesById = new ConcurrentHashMap<>();

   // ============ WPT-23: per-objectId StatusUpdate attribute snapshots ============
   // StatusUpdate packets are partial [objId][attrCount][(id,val)...], so every packet MERGES
   // into the snapshot of that objectId (the player AND nearby NPCs) - see StatusSnapshot.apply().
   private final ConcurrentHashMap<Integer, StatusSnapshot> statusSnapshots = new ConcurrentHashMap<>();

   // ============ WPT-25: combat KPI collector (hits / damageIn-out / rolling DPS / kills) ============
   private static final long ROLLING_KPI_WINDOW_MS = 60_000L;    // DPS "rolling minute"
   private static final long KILL_ATTRIBUTION_WINDOW_MS = 60_000L;
   private final Object combatLock = new Object();
   private int hitsOut = 0;
   private int missesOut = 0;
   private int critsOut = 0;
   private long damageOut = 0;
   private int hitsIn = 0;
   private long damageIn = 0;
   private int kills = 0;
   private int deaths = 0;
   private final java.util.ArrayDeque<DamageEvent> damageWindow = new java.util.ArrayDeque<>();
   // Entities this bot recently damaged (objId -> last damage millis) for kill attribution.
   private final ConcurrentHashMap<Integer, Long> damagedTargets = new ConcurrentHashMap<>();

   // ============ WPT-26: MagicSkillUse / skill-cast metering ============
   private int magicSkillUseCount = 0;
   private int magicSkillLaunchedCount = 0;
   private int skillRequestCount = 0;
   private int attackPacketCount = 0;
   private int diePacketCount = 0;
   private int reviveCount = 0;
   private volatile int selfSkillCastTotal = 0;
   private volatile long lastSkillLaunchedMillis = 0;
   // Per-bot cast counters: skillId -> casts (confirmed), targetId -> casts (confirmed).
   private final ConcurrentHashMap<Integer, AtomicInteger> skillCastCounts = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<Integer, AtomicInteger> skillCastCountsByTarget = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<Integer, Long> lastSkillCastMillisBySkill = new ConcurrentHashMap<>();
   // Cooldown metering: skillId -> server-reported reuse delay + last confirmed cast time.
   private final ConcurrentHashMap<Integer, SkillCooldown> skillCooldowns = new ConcurrentHashMap<>();
   private final java.util.List<SkillCastEvent> skillCastEvents = new java.util.ArrayList<>(); // guarded by combatLock
   private static final int MAX_CAST_EVENTS = 512;
   private volatile SkillCastEvent lastSkillCast = null;
   private volatile long lastSkillCastMillis = 0;

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
            case OP_USER_INFO:
               parseUserInfo(buf);
               break;
            case OP_VALIDATE_LOCATION:
               parseValidateLocation(buf);
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
            // WPT-25/26: combat + skill-cast feed (ServerPackets.java opcodes).
            case OP_ATTACK:
               attackPacketCount++;
               parseAttack(buf);
               break;
            case OP_DIE:
               diePacketCount++;
               parseDie(buf);
               break;
            case OP_REVIVE:
               reviveCount++;
               parseRevive(buf);
               break;
            case OP_MAGIC_SKILL_USE:
               magicSkillUseCount++;
               parseMagicSkillUse(buf);
               break;
            case OP_MAGIC_SKILL_LAUNCHED:
               magicSkillLaunchedCount++;
               parseMagicSkillLaunched(buf);
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
         // Real Interlude CharInfo (0x03) write order (CharInfo.java writeImpl):
         //   [x][y][z][vehicleObjId][objId][name UTF-16LE+null][race][female][baseClass][paperdolls]...
         int x = buf.getInt();
         int y = buf.getInt();
         int z = buf.getInt();
         buf.getInt();               // vehicle object id
         int objId = buf.getInt();
         String name = readUtf16Le(buf);
         buf.getInt();               // race
         buf.getInt();               // female
         int baseClass = buf.getInt();
         boolean isSelf = selfObjectId == 0 || objId == selfObjectId;
         if (isSelf)
         {
            playerX = x;
            playerY = y;
            playerZ = z;
            if (baseClass > 0)
            {
               this.baseClass = baseClass;
            }
         }
         EntityInfo entity = new EntityInfo(objId, -1, x, y, z, 0, false);
         entity.name = name;
         entitiesById.put(objId, entity);
         LOGGER.info("[PACKET-LOG] [" + playerName + "] CHAR_INFO: objId=" + objId + " name=" + name
            + " self=" + isSelf + " pos=(" + x + "," + y + "," + z + ")");
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] CharInfo parse incomplete");
      }
   }

   /**
    * Parse UserInfo packet (opcode 0x04) - the authoritative SELF state: position, objId,
    * name, race/gender/baseClass, LEVEL, EXP, stats, HP/MP, SP, load, weapon flag.
    * Layout mirrored from UserInfo.java writeImpl (Interlude).
    */
   private void parseUserInfo(ByteBuffer buf)
   {
      try
      {
         int x = buf.getInt();
         int y = buf.getInt();
         int z = buf.getInt();
         buf.getInt();               // vehicle object id
         int objId = buf.getInt();
         String name = readUtf16Le(buf);
         buf.getInt();               // race
         buf.getInt();               // female
         int baseClass = buf.getInt();
         int lvl = buf.getInt();
         long expValue = buf.getLong();
         skip(buf, 6 * 4);           // str / dex / con / int / wit / men
         int mxHp = buf.getInt();
         int cHp = buf.getInt();
         int mxMp = buf.getInt();
         int cMp = buf.getInt();
         int spValue = buf.getInt();
         int cLoad = buf.getInt();
         int mxLoad = buf.getInt();
         int weaponFlag = buf.getInt(); // 20 = none, 40 = weapon equipped

         boolean isSelf = selfObjectId == 0 || objId == selfObjectId;
         if (isSelf)
         {
            playerX = x;
            playerY = y;
            playerZ = z;
            if (selfObjectId == 0)
            {
               selfObjectId = objId;
            }
            if (lvl > 0)
            {
               level = lvl;
            }
            exp = expValue;
            sp = spValue;
            curLoad = cLoad;
            maxLoad = mxLoad;
            weaponEquipped = weaponFlag >= 40;
            maxHp = mxHp;
            curHp = cHp;
            maxMp = mxMp;
            curMp = cMp;
            if (baseClass > 0)
            {
               this.baseClass = baseClass;
            }
            if (name != null && !name.isEmpty())
            {
               charName = name;
            }
         }
         LOGGER.info("[PACKET-LOG] [" + playerName + "] USER_INFO: objId=" + objId + " self=" + isSelf
            + " lvl=" + lvl + " exp=" + expValue + " pos=(" + x + "," + y + "," + z
            + ") load=" + cLoad + "/" + mxLoad + " weapon=" + weaponFlag);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] UserInfo parse incomplete");
      }
   }

   /**
    * Parse ValidateLocation packet (opcode 0x61) - high-frequency SELF position updates while
    * moving. Layout from ValidateLocation.java writeImpl: [objId][x][y][z][heading].
    */
   private void parseValidateLocation(ByteBuffer buf)
   {
      try
      {
         int objId = buf.getInt();
         int x = buf.getInt();
         int y = buf.getInt();
         int z = buf.getInt();
         int heading = buf.remaining() >= 4 ? buf.getInt() : 0;
         boolean isSelf = selfObjectId == 0 || objId == selfObjectId;
         EntityInfo entity = entitiesById.get(objId);
         if (entity != null)
         {
            entity.x = x;
            entity.y = y;
            entity.z = z;
            entity.heading = heading;
         }
         if (isSelf)
         {
            playerX = x;
            playerY = y;
            playerZ = z;
            playerHeading = heading;
         }
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] ValidateLocation parse incomplete");
      }
   }

   /** Read a server string: UTF-16LE chars until a 2-byte 0 terminator (WritableBuffer.writeString). */
   private static String readUtf16Le(ByteBuffer buf)
   {
      StringBuilder sb = new StringBuilder();
      for (int guard = 0; guard < 40; guard++)
      {
         if (buf.remaining() < 2)
         {
            break;
         }
         char c = (char) buf.getShort();
         if (c == 0)
         {
            break;
         }
         sb.append(c);
      }
      return sb.toString();
   }

   private static void skip(ByteBuffer buf, int bytes)
   {
      buf.position(Math.min(buf.position() + bytes, buf.limit()));
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
            // WPT-23: merge into the per-objectId full attribute snapshot (ANY entity, not only self).
            statusSnapshots.computeIfAbsent(objectId, StatusSnapshot::new).apply(attrId, value);

            // Track HP/MP values (self only; a target's StatusUpdate must not overwrite the bot's HP)
            if (isSelf && attrId == STAT_CUR_HP) curHp = value;
            if (isSelf && attrId == STAT_MAX_HP) maxHp = value;
            if (isSelf && attrId == STAT_CUR_MP) curMp = value;
            if (isSelf && attrId == STAT_MAX_MP) maxMp = value;
            // Stream G (G-Live): track level so the live loop can detect level-ups.
            if (isSelf && attrId == STAT_LEVEL) level = value;
            // Dashboard data layer: also capture EXP / SP / loads / CP from self StatusUpdate attrs.
            if (isSelf && attrId == STAT_EXP) exp = value & 0xFFFFFFFFL;
            if (isSelf && attrId == STAT_SP) sp = value;
            if (isSelf && attrId == STAT_CUR_LOAD) curLoad = value;
            if (isSelf && attrId == STAT_MAX_LOAD) maxLoad = value;
            if (isSelf && attrId == STAT_CUR_CP) curCp = value;
            if (isSelf && attrId == STAT_MAX_CP) maxCp = value;
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
         // Stream E (task 78): parse real item list so MerchantAI acts on genuine inventory.
         // Per-item layout (L2J ItemList 0x1B): [type1:short][objId:int][itemId:int][count:int]
         // [type2:short][coobj:short][bodypart:int][enchant:short][0:short][0:short] = 32 bytes.
         inventoryItems.clear();
         for (int i = 0; i < itemCount; i++)
         {
            if (buf.remaining() < 32) break;
            buf.getShort();                       // type1
            buf.getInt();                          // objectId
            int itemId = buf.getInt();
            long count = buf.getInt() & 0xFFFFFFFFL;
            buf.getShort();                        // type2
            buf.getShort();                        // coobjectId
            buf.getInt();                          // bodypart
            buf.getShort();                        // enchant
            buf.getShort();                        // customType1
            buf.getShort();                        // customType2
            if (itemId == 57) this.adena = (int) Math.min(count, Integer.MAX_VALUE);
            inventoryItems.put(itemId, count);
         }
         int totalSlots = 120;
         this.inventoryUsagePercent = (int)((itemCount * 100.0) / totalSlots);
         if (inventoryUsagePercent > 100) inventoryUsagePercent = 100;
         LOGGER.info("[PACKET-LOG] [" + playerName + "] ITEM_LIST: showWindow=" + showWindow
                 + " itemCount=" + itemCount + " usage=" + inventoryUsagePercent
                 + "% adena=" + adena + " distinctItems=" + inventoryItems.size());
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

   // ================= WPT-25 / WPT-26 combat + skill-cast parsers =================
   /**
    * Parse Attack packet (opcode 0x05) - melee hit feed broadcast by the server.
    * Interlude layout (SourceCode .../serverpackets/Attack.java writeImpl):
    *    [attackerId:int][targetId:int][damage:int][flags:byte][ax:int][ay:int][az:int]
    *    [hits-1:short][extra hits: (targetId,damage,flags)...][tx:int][ty:int][tz:int]
    * Hit flags (SourceCode Hit.java): 0x80=miss, 0x20=crit, 0x40=shield.
    * Feeds the WPT-25 combat KPIs: hits/damageOut when the bot attacks, hitsIn/damageIn when
    * the bot is hit (the server broadcasts every visible melee combat to this client).
    */
   private void parseAttack(ByteBuffer buf)
   {
      try
      {
         if (buf.remaining() < 13) return;                  // attacker(4) + first hit(9)
         int attackerId = buf.getInt();
         int targetId = buf.getInt();
         int damage = buf.getInt();
         int flags = buf.get() & 0xFF;
         recordHit(attackerId, targetId, damage, flags);
         skip(buf, 12);                                     // attacker position
         if (buf.remaining() < 2) return;
         int extraHits = buf.getShort() & 0xFFFF;           // hits.size() - 1
         for (int i = 0; i < extraHits && buf.remaining() >= 9; i++)
         {
            int tId = buf.getInt();
            int dmg = buf.getInt();
            int fl = buf.get() & 0xFF;
            recordHit(attackerId, tId, dmg, fl);
         }
         skip(buf, 12);                                     // target position tail (optional)
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] Attack parse incomplete");
      }
   }

   /** WPT-25: fold one hitting/receiving melee hit into the combat KPIs. */
   private void recordHit(int attackerId, int targetId, int damage, int flags)
   {
      boolean miss = (flags & 0x80) != 0;
      boolean crit = (flags & 0x20) != 0;
      long now = System.currentTimeMillis();
      if (selfObjectId != 0 && attackerId == selfObjectId)
      {
         synchronized (combatLock)
         {
            hitsOut++;
            if (miss) missesOut++;
            if (crit) critsOut++;
            if (damage > 0)
            {
               damageOut += damage;
               addDamageEventLocked(now, damage);
            }
         }
         if (damage > 0) damagedTargets.put(targetId, now);
      }
      if (selfObjectId != 0 && targetId == selfObjectId)
      {
         synchronized (combatLock)
         {
            hitsIn++;
            if (damage > 0) damageIn += damage;
         }
      }
   }

   /**
    * Parse Die packet (opcode 0x06) - an entity died.
    * Interlude layout (SourceCode .../serverpackets/Die.java writeImpl):
    *    [objectId:int][canTeleport:int][hideAway/castle/hq ints][sweepable:int][fixedRes:int]
    * WPT-25: a non-self entity this bot damaged in the last minute counts as a kill;
    * the bot itself dying counts as a death.
    */
   private void parseDie(ByteBuffer buf)
   {
      try
      {
         if (buf.remaining() < 4) return;
         int objectId = buf.getInt();
         boolean selfDeath = selfObjectId != 0 && objectId == selfObjectId;
         if (selfDeath)
         {
            synchronized (combatLock)
            {
               deaths++;
            }
         }
         else
         {
            Long lastDamaged = damagedTargets.get(objectId);
            if (lastDamaged != null && System.currentTimeMillis() - lastDamaged <= KILL_ATTRIBUTION_WINDOW_MS)
            {
               synchronized (combatLock)
               {
                  kills++;
               }
               damagedTargets.remove(objectId);
            }
         }
         LOGGER.info("[PACKET-LOG] [" + playerName + "] DIE: objId=" + objectId + " self=" + selfDeath
            + " kills=" + kills + " deaths=" + deaths);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] Die parse incomplete");
      }
   }

   /** Parse Revive packet (opcode 0x07) - [objectId:int] (Revive.java writeImpl). */
   private void parseRevive(ByteBuffer buf)
   {
      try
      {
         if (buf.remaining() >= 4)
         {
            LOGGER.fine("[PACKET-LOG] [" + playerName + "] REVIVE: objId=" + buf.getInt());
         }
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] Revive parse incomplete");
      }
   }

   // --- WPT-26 skill parsers inserted below ---
   /**
    * Parse MagicSkillUse (opcode 0x48) - the server broadcast confirming a skill cast.
    * Interlude layout (SourceCode .../serverpackets/MagicSkillUse.java writeImpl):
    *    [casterId:int][targetId:int][skillId:int][skillLevel:int][hitTime:int][reuseDelay:int]
    *    [px:int][py:int][pz:int][critical:int (+short 0 when critical)][tx:int][ty:int][tz:int]
    * WPT-26: this is the DEFINITIVE "cast fired" confirmation (Audit/42 open item 6 targets
    * exactly this opcode in the live histogram).
    */
   private void parseMagicSkillUse(ByteBuffer buf)
   {
      try
      {
         if (buf.remaining() < 24) return;                  // 6 leading ints
         int casterId = buf.getInt();
         int targetId = buf.getInt();
         int skillId = buf.getInt();
         int skillLevel = buf.getInt();
         int hitTime = buf.getInt();
         int reuseDelay = buf.getInt();
         skip(buf, 12);                                     // caster position
         if (buf.remaining() >= 4)
         {
            int critical = buf.getInt();
            if (critical != 0 && buf.remaining() >= 2) skip(buf, 2); // short 0 after critical flag
         }
         skip(buf, 12);                                     // target position tail (optional)
         recordSkillCast(casterId, targetId, skillId, skillLevel, hitTime, reuseDelay);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] MagicSkillUse parse incomplete");
      }
   }

   /**
    * Parse MagicSkillLaunched (opcode 0x76) - projectile/hit delivery of a cast skill.
    * Interlude layout (SourceCode .../serverpackets/MagicSkillLaunched.java writeImpl):
    *    [casterId:int][skillId:int][skillLevel:int][targetCount:int][(targetId:int)*count]
    */
   private void parseMagicSkillLaunched(ByteBuffer buf)
   {
      try
      {
         if (buf.remaining() < 16) return;
         int casterId = buf.getInt();
         int skillId = buf.getInt();
         buf.getInt();                                      // skillLevel
         int targetCount = buf.getInt();
         for (int i = 0; i < targetCount && buf.remaining() >= 4; i++)
         {
            buf.getInt();                                   // targetId
         }
         lastSkillLaunchedMillis = System.currentTimeMillis();
         LOGGER.fine("[PACKET-LOG] [" + playerName + "] SKILL_LAUNCHED: caster=" + casterId
            + " skill=" + skillId + " targets=" + targetCount);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] MagicSkillLaunched parse incomplete");
      }
   }

   // --- WPT-26 record/handler helpers ------------
   /**
    * WPT-26: fold one server-confirmed skill cast into the per-bot metering state
    * (skillId/targetId counts, cast timeline, reuse-delay cooldown, kill attribution).
    */
   private void recordSkillCast(int casterId, int targetId, int skillId, int skillLevel,
                                int hitTime, int reuseDelay)
   {
      long now = System.currentTimeMillis();
      boolean selfCast = selfObjectId != 0 && casterId == selfObjectId;
      if (selfCast && targetId != 0 && targetId != selfObjectId)
      {
         damagedTargets.put(targetId, now);                 // magic kills share melee attribution
      }
      skillCastCounts.computeIfAbsent(skillId, k -> new AtomicInteger()).incrementAndGet();
      if (targetId != 0)
      {
         skillCastCountsByTarget.computeIfAbsent(targetId, k -> new AtomicInteger()).incrementAndGet();
      }
      lastSkillCastMillisBySkill.put(skillId, now);
      SkillCastEvent event = new SkillCastEvent(skillId, targetId, casterId, skillLevel,
         hitTime, reuseDelay, now, true, selfCast);
      synchronized (combatLock)
      {
         if (selfCast) selfSkillCastTotal++;
         skillCastEvents.add(event);
         while (skillCastEvents.size() > MAX_CAST_EVENTS) skillCastEvents.remove(0);
         lastSkillCast = event;
         lastSkillCastMillis = now;
         if (reuseDelay > 0)
         {
            SkillCooldown cd = skillCooldowns.get(skillId);
            if (cd == null)
            {
               cd = new SkillCooldown(skillId, reuseDelay);
               skillCooldowns.put(skillId, cd);
            }
            cd.lastCastMillis = now;
         }
      }
      LOGGER.info("[PACKET-LOG] [" + playerName + "] SKILL_CAST: caster=" + casterId
         + (selfCast ? "(SELF)" : "") + " skill=" + skillId + " lvl=" + skillLevel
         + " target=" + targetId + " hitTime=" + hitTime + " reuse=" + reuseDelay + " castMs=" + now);
   }

   /**
    * WPT-26: record the client-&gt;server skill-cast REQUEST gate (0x2F REQUEST_MAGIC_SKILL_USE,
    * 12-byte frame [skillId:int][ctrl:int][shift:byte], live-proven in Audit/42). The send side
    * (PacketCodec.encodeUseSkill / L2JProtocol.sendUseSkill) should call this right before writing
    * the frame so the engine can meter requested casts against server confirmations.
    */
   public void recordSkillRequest(int skillId, int ctrlPressed, int shiftPressed)
   {
      long now = System.currentTimeMillis();
      synchronized (combatLock)
      {
         skillRequestCount++;
         SkillCastEvent event = new SkillCastEvent(skillId, 0, selfObjectId, 0, 0, 0, now, false, true);
         skillCastEvents.add(event);
         while (skillCastEvents.size() > MAX_CAST_EVENTS) skillCastEvents.remove(0);
      }
      LOGGER.info("[PACKET-LOG] [" + playerName + "] SKILL_REQUEST(0x2F): skill=" + skillId
         + " ctrl=" + ctrlPressed + " shift=" + shiftPressed);
   }

   /** WPT-25: append one out-going damage point to the rolling-minute DPS window (lock held). */
   private void addDamageEventLocked(long now, int amount)
   {
      purgeDamageWindowLocked(now);
      damageWindow.addLast(new DamageEvent(now, amount));
   }

   /** WPT-25: drop damage events older than the rolling minute (lock held). */
   private long purgeDamageWindowLocked(long now)
   {
      while (!damageWindow.isEmpty() && now - damageWindow.peekFirst().millis > ROLLING_KPI_WINDOW_MS)
      {
         damageWindow.removeFirst();
      }
      return damageWindow.isEmpty() ? 0 : damageWindow.peekFirst().millis;
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
         case STAT_EXP: return "EXP";
         case STAT_CUR_HP: return "CUR_HP";
         case STAT_MAX_HP: return "MAX_HP";
         case STAT_CUR_MP: return "CUR_MP";
         case STAT_MAX_MP: return "MAX_MP";
         case STAT_SP: return "SP";
         case STAT_CUR_LOAD: return "CUR_LOAD";
         case STAT_MAX_LOAD: return "MAX_LOAD";
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
   /** Stream E: test/telemetry hook to inject adena (normally parsed from ItemList item 57). */
   public void setAdena(int a) { this.adena = a; }
   /** Stream E: test/telemetry hook to inject inventory usage (normally parsed from ItemList). */
   public void setInventoryUsagePercent(int p) { this.inventoryUsagePercent = p; }
   public int getMaxHp() { return maxHp; }
   public int getCurMp() { return curMp; }
   public int getMaxMp() { return maxMp; }
   /** Stream G (G-Live): the bot's level parsed from StatusUpdate(0x0E) STAT_LEVEL(0x01); 0 if unseen. */
   public int getLevel() { return level; }
   // Dashboard data layer getters (UserInfo 0x04 / StatusUpdate / ValidateLocation).
   public long getExp() { return exp; }
   public int getSp() { return sp; }
   public int getCurrentLoad() { return curLoad; }
   public int getMaxLoad() { return maxLoad; }
   public boolean isWeaponEquipped() { return weaponEquipped; }
   public int getBaseClass() { return baseClass; }
   public String getCharName() { return charName; }
   public int getCurCp() { return curCp; }
   public int getMaxCp() { return maxCp; }
   public java.util.Collection<EntityInfo> getEntities() { return entitiesById.values(); }
   public int getEntityCountTotal() { return entitiesById.size(); }
   public double getHpPercentage() { return maxHp > 0 ? (double) curHp / maxHp * 100 : 0; }
   public double getMpPercentage() { return maxMp > 0 ? (double) curMp / maxMp * 100 : 0; }

   // Position tracking getters (Task 34)
   public int getPlayerX() { return playerX; }
   public int getPlayerY() { return playerY; }
   public int getPlayerZ() { return playerZ; }
   public int getPlayerHeading() { return playerHeading; }

   // Inventory tracking getters (Task 35)
   public int getAdena() { return adena; }
   /** Stream E (task 78): the real inventory map, itemId -> count, parsed from ItemList(0x1B). */
   public java.util.Map<Integer, Long> getInventoryItems() { return inventoryItems; }
   public int getInventoryUsagePercent() { return inventoryUsagePercent; }
   public boolean isInventoryFull() { return inventoryUsagePercent >= 90; }
   public boolean hasSpaceForTrade(int itemCount) { return inventoryUsagePercent + (itemCount * 100 / 120) < 90; }

   // Quest tracking getters (Task 36)
   public int getActiveQuestCount() { return activeQuestCount; }
   public int getQuestInfoCount() { return questInfoCount; }

   // ============ WPT-23: per-objectId StatusUpdate attribute snapshots ============
   /** WPT-23: full last-seen StatusUpdate attribute snapshot for an objectId (null if unseen). */
   public StatusSnapshot getStatusSnapshot(int objectId) { return statusSnapshots.get(objectId); }
   /** WPT-23: all currently-observed entity attribute snapshots. */
   public java.util.Collection<StatusSnapshot> getStatusSnapshots() { return statusSnapshots.values(); }
   /** WPT-23: snapshot for the bot itself (selfObjectId), null until a self StatusUpdate arrives. */
   public StatusSnapshot getSelfStatusSnapshot() { return selfObjectId != 0 ? statusSnapshots.get(selfObjectId) : null; }
   public int getStatusSnapshotCount() { return statusSnapshots.size(); }

   // ============ WPT-25: combat KPI read-only view ============
   public int getHitsOut() { synchronized (combatLock) { return hitsOut; } }
   public int getMissesOut() { synchronized (combatLock) { return missesOut; } }
   public int getCritsOut() { synchronized (combatLock) { return critsOut; } }
   public long getDamageOut() { synchronized (combatLock) { return damageOut; } }
   public int getHitsIn() { synchronized (combatLock) { return hitsIn; } }
   public long getDamageIn() { synchronized (combatLock) { return damageIn; } }
   public int getKills() { synchronized (combatLock) { return kills; } }
   public int getDeaths() { synchronized (combatLock) { return deaths; } }
   public int getAttackPacketCount() { return attackPacketCount; }
   public int getDiePacketCount() { return diePacketCount; }
   public int getReviveCount() { return reviveCount; }

   /** WPT-25: damage-out per second over the rolling 60 second window. */
   public double getDps()
   {
      synchronized (combatLock)
      {
         long now = System.currentTimeMillis();
         long oldest = purgeDamageWindowLocked(now);
         if (damageWindow.isEmpty()) return 0.0;
         long total = 0;
         for (DamageEvent e : damageWindow) total += e.amount;
         long span = Math.max(1L, now - oldest);
         return (double) total * 1000.0 / span;
      }
   }

   /** WPT-25: point-in-time snapshot of all combat KPIs. */
   public CombatKpis getCombatKpis()
   {
      synchronized (combatLock)
      {
         long now = System.currentTimeMillis();
         long oldest = purgeDamageWindowLocked(now);
         double dps = 0.0;
         if (!damageWindow.isEmpty())
         {
            long total = 0;
            for (DamageEvent e : damageWindow) total += e.amount;
            dps = (double) total * 1000.0 / Math.max(1L, now - oldest);
         }
         return new CombatKpis(hitsOut, missesOut, critsOut, damageOut, hitsIn, damageIn,
            kills, deaths, dps);
      }
   }

   /** Test/telemetry hook: clear all combat KPI counters and the DPS window. */
   public void resetCombatKpis()
   {
      synchronized (combatLock)
      {
         hitsOut = 0; missesOut = 0; critsOut = 0; damageOut = 0;
         hitsIn = 0; damageIn = 0; kills = 0; deaths = 0;
         damageWindow.clear();
      }
      damagedTargets.clear();
   }

   // ============ WPT-26: skill-cast metering read-only view ============
   public int getMagicSkillUseCount() { return magicSkillUseCount; }
   public int getMagicSkillLaunchedCount() { return magicSkillLaunchedCount; }
   public int getSkillRequestCount() { return skillRequestCount; }
   public int getSelfSkillCastTotal() { return selfSkillCastTotal; }
   /** WPT-26: total confirmed casts across all skills (server 0x48 confirmations). */
   public int getSkillCastTotal()
   {
      return (int) skillCastCounts.values().stream().mapToInt(AtomicInteger::get).sum();
   }
   public int getSkillCastCount(int skillId)
   {
      AtomicInteger c = skillCastCounts.get(skillId);
      return c == null ? 0 : c.get();
   }
   public int getSkillCastCountByTarget(int targetId)
   {
      AtomicInteger c = skillCastCountsByTarget.get(targetId);
      return c == null ? 0 : c.get();
   }
   /** WPT-26: last confirmed cast millis for one skill (0 if never cast). */
   public long getLastSkillCastMillis(int skillId)
   {
      Long t = lastSkillCastMillisBySkill.get(skillId);
      return t == null ? 0 : t;
   }
   public long getLastSkillCastMillis() { return lastSkillCastMillis; }
   public long getLastSkillLaunchedMillis() { return lastSkillLaunchedMillis; }
   public SkillCastEvent getLastSkillCast() { return lastSkillCast; }
   /** WPT-26: recent cast timeline (oldest-first; confirmed server 0x48 casts + 0x2F requests). */
   public java.util.List<SkillCastEvent> getSkillCastEvents()
   {
      synchronized (combatLock)
      {
         return new java.util.ArrayList<>(skillCastEvents);
      }
   }
   /** WPT-26: reuse delay (ms) the server reported for a skill (0 if never seen). */
   public int getSkillReuseDelayMs(int skillId)
   {
      SkillCooldown cd = skillCooldowns.get(skillId);
      return cd == null ? 0 : cd.reuseDelayMs;
   }
   /** WPT-26: remaining cooldown for a skill, 0 when the next cast is allowed. */
   public long getSkillReuseRemainingMs(int skillId)
   {
      SkillCooldown cd = skillCooldowns.get(skillId);
      if (cd == null || cd.reuseDelayMs <= 0) return 0;
      long elapsed = System.currentTimeMillis() - cd.lastCastMillis;
      return elapsed >= cd.reuseDelayMs ? 0 : cd.reuseDelayMs - elapsed;
   }

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
   /** Stream G (G-Live): test/telemetry hook to remove an entity (mirrors DeleteObject). */
   public void removeEntityForTest(int objectId) {
      entitiesById.remove(objectId);
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

    // ============ WPT-23: per-objectId StatusUpdate attribute snapshot ============
   /**
    * WPT-23: volatile snapshot of the full StatusUpdate attribute set for ONE objectId.
    * Fields are volatile so the live reader thread and the dashboard/decision threads
    * never see torn values. Packets are partial (only changed attrs), so apply() merges.
    */
   public static class StatusSnapshot
   {
      public static final int MASK_EXP = 1 << 0;
      public static final int MASK_SP = 1 << 1;
      public static final int MASK_LEVEL = 1 << 2;
      public static final int MASK_CUR_HP = 1 << 3;
      public static final int MASK_MAX_HP = 1 << 4;
      public static final int MASK_CUR_MP = 1 << 5;
      public static final int MASK_MAX_MP = 1 << 6;
      public static final int MASK_CUR_CP = 1 << 7;
      public static final int MASK_MAX_CP = 1 << 8;

      public final int objectId;
      private volatile long exp;
      private volatile int sp;
      private volatile int level;
      private volatile int curHp;
      private volatile int maxHp;
      private volatile int curMp;
      private volatile int maxMp;
      private volatile int curCp;
      private volatile int maxCp;
      private volatile int seenMask;
      private volatile long lastUpdateMillis;

      public StatusSnapshot(int objectId)
      {
         this.objectId = objectId;
      }

      /** Merge one StatusUpdate attribute pair into this snapshot. */
      void apply(int attrId, int value)
      {
         switch (attrId)
         {
            case STAT_EXP:      exp = value & 0xFFFFFFFFL; seenMask |= MASK_EXP; break;
            case STAT_SP:       sp = value; seenMask |= MASK_SP; break;
            case STAT_LEVEL:    level = value; seenMask |= MASK_LEVEL; break;
            case STAT_CUR_HP:   curHp = value; seenMask |= MASK_CUR_HP; break;
            case STAT_MAX_HP:   maxHp = value; seenMask |= MASK_MAX_HP; break;
            case STAT_CUR_MP:   curMp = value; seenMask |= MASK_CUR_MP; break;
            case STAT_MAX_MP:   maxMp = value; seenMask |= MASK_MAX_MP; break;
            case STAT_CUR_CP:   curCp = value; seenMask |= MASK_CUR_CP; break;
            case STAT_MAX_CP:   maxCp = value; seenMask |= MASK_MAX_CP; break;
            default: return; // attr not in the tracked set: don't bump the update stamp
         }
         lastUpdateMillis = System.currentTimeMillis();
      }

      public long getExp() { return exp; }
      public int getSp() { return sp; }
      public int getLevel() { return level; }
      public int getCurHp() { return curHp; }
      public int getMaxHp() { return maxHp; }
      public int getCurMp() { return curMp; }
      public int getMaxMp() { return maxMp; }
      public int getCurCp() { return curCp; }
      public int getMaxCp() { return maxCp; }
      /** Bitmask of which of the 9 tracked attrs have been seen so far. */
      public int getSeenMask() { return seenMask; }
      public long getLastUpdateMillis() { return lastUpdateMillis; }

      @Override
      public String toString()
      {
         return "StatusSnapshot[objId=" + objectId + " lvl=" + level + " exp=" + exp + " sp=" + sp
            + " hp=" + curHp + "/" + maxHp + " mp=" + curMp + "/" + maxMp
            + " cp=" + curCp + "/" + maxCp + "]";
      }
   }

   // ============ WPT-26: skill-cast metering helpers ============
   /** One recorded skill cast: a server confirmation (0x48) or a client request (0x2F). */
   public static class SkillCastEvent
   {
      public final int skillId;
      public final int targetId;
      public final int casterId;
      public final int skillLevel;
      public final int hitTimeMs;
      public final int reuseDelayMs;
      public final long castMillis;
      public final boolean confirmed; // true = server MagicSkillUse(0x48); false = client 0x2F request
      public final boolean selfCast;

      public SkillCastEvent(int skillId, int targetId, int casterId, int skillLevel,
                            int hitTimeMs, int reuseDelayMs, long castMillis,
                            boolean confirmed, boolean selfCast)
      {
         this.skillId = skillId;
         this.targetId = targetId;
         this.casterId = casterId;
         this.skillLevel = skillLevel;
         this.hitTimeMs = hitTimeMs;
         this.reuseDelayMs = reuseDelayMs;
         this.castMillis = castMillis;
         this.confirmed = confirmed;
         this.selfCast = selfCast;
      }

      @Override
      public String toString()
      {
         return "SkillCastEvent[skill=" + skillId + " target=" + targetId + " caster=" + casterId
            + (confirmed ? " confirmed" : " requested") + (selfCast ? " SELF" : "") + "]";
      }
   }

   // --- WPT-25/26 collector classes ---
   /** Server-reported cooldown state for one skill (learned from MagicSkillUse 0x48 reuseDelay). */
   private static final class SkillCooldown
   {
      final int skillId;
      final int reuseDelayMs;
      volatile long lastCastMillis;

      SkillCooldown(int skillId, int reuseDelayMs)
      {
         this.skillId = skillId;
         this.reuseDelayMs = reuseDelayMs;
      }
   }

   /** One out-going damage point inside the rolling-minute DPS window. */
   private static final class DamageEvent
   {
      final long millis;
      final int amount;

      DamageEvent(long millis, int amount)
      {
         this.millis = millis;
         this.amount = amount;
      }
   }

   /** WPT-25: read-only point-in-time combat KPI snapshot. */
   public static class CombatKpis
   {
      public final int hitsOut;
      public final int missesOut;
      public final int critsOut;
      public final long damageOut;
      public final int hitsIn;
      public final long damageIn;
      public final int kills;
      public final int deaths;
      public final double dps; // damage-out/sec over the rolling 60 s window

      public CombatKpis(int hitsOut, int missesOut, int critsOut, long damageOut,
                        int hitsIn, long damageIn, int kills, int deaths, double dps)
      {
         this.hitsOut = hitsOut;
         this.missesOut = missesOut;
         this.critsOut = critsOut;
         this.damageOut = damageOut;
         this.hitsIn = hitsIn;
         this.damageIn = damageIn;
         this.kills = kills;
         this.deaths = deaths;
         this.dps = dps;
      }

      @Override
      public String toString()
      {
         return "CombatKpis[hitsOut=" + hitsOut + " damageOut=" + damageOut + " dps=" + dps
            + " hitsIn=" + hitsIn + " damageIn=" + damageIn + " kills=" + kills + " deaths=" + deaths + "]";
      }
   }

   // Inner class for tracked entity info
   public static class EntityInfo {
      public final int objectId;
      public final int npcId;
      public volatile String name; // player name for CharInfo entities (null for NPCs)
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
