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
   public static final int OP_CHAR_SELECT_INFO = 0x13;
   public static final int OP_USER_INFO = 0x04;
   public static final int OP_STATUS_UPDATE = 0x0E;
   public static final int OP_DELETE_OBJECT = 0x12;
   public static final int OP_NPC_INFO = 0x16;
   public static final int OP_ITEM_LIST = 0x1B;
   // TIM-001 movement-sync: the server broadcasts CHAR_MOVE_TO_LOCATION (0x01) at MOVE_START and
   // STOP_MOVE (0x47) when the char reaches its destination / stops. Interlude clients lerp the walk
   // client-side, so the server does NOT stream ValidateLocation(0x61) per-tick during a walk — a bot
   // that only parses 0x61 saw itself as a frozen dot and every hop route was "abandoned as
   // unreachable" 45s after the server had actually already walked it to the target (live-verified:
   // gameserver.characters x/y/z == the hunt target coords while the bot's local position never moved).
   public static final int OP_CHAR_MOVE_TO_LOCATION = 0x01; // [objId][xDest][yDest][zDest][x][y][z]
   public static final int OP_STOP_MOVE = 0x47;             // [objId][x][y][z][heading]
   public static final int OP_VALIDATE_LOCATION = 0x61;
   // WPT-22: SystemMessage server opcode. NOTE: the ticket mentions 0x5A but the verified
   // SourceCode ServerPackets.java enum maps SYSTEM_MESSAGE to 0x64 (0x5A is VEHICLE_DEPARTURE),
   // so we keep the existing 0x64 routing below.
   public static final int OP_SYSTEM_MESSAGE = 0x64;
   public static final int OP_EX_PACKET = 0xFE;
   public static final int OP_EX_QUEST_INFO = 0x19;
   // WPT-27: top-level QUEST_LIST "quest journal" packet (ServerPackets.QUEST_LIST enum -> 0x80),
   // the one the server sends on enter-world with the character's full active-quest log. Unlike
   // the count-only ExQuestInfo (0xFE 0x19), this carries each quest's 4-byte journal state.
   public static final int OP_QUEST_LIST = 0x80;
   // WPT-22: server->client SAY/chat broadcasts (ServerPackets.java enum + writeImpl verified).
   public static final int OP_NPC_SAY = 0x02;        // NpcSay: [objId][textType][npcId+1000000][text]
   public static final int OP_CREATURE_SAY = 0x4A;   // CreatureSay: [objId][chatType][name][text]

   // SystemMessage param types (SystemMessage.java constants). Each param is prefixed by its
   // 4-byte type; the payload width depends on the type (string vs int vs long vs int[]).
   public static final int SM_TYPE_TEXT = 0;
   public static final int SM_TYPE_INT_NUMBER = 1;
   public static final int SM_TYPE_NPC_NAME = 2;
   public static final int SM_TYPE_ITEM_NAME = 3;
   public static final int SM_TYPE_SKILL_NAME = 4;
   public static final int SM_TYPE_CASTLE_NAME = 5;
   public static final int SM_TYPE_LONG_NUMBER = 6;
   public static final int SM_TYPE_ZONE_NAME = 7;
   public static final int SM_TYPE_ELEMENT_NAME = 9;
   public static final int SM_TYPE_INSTANCE_NAME = 10;
   public static final int SM_TYPE_DOOR_NAME = 11;
   public static final int SM_TYPE_PLAYER_NAME = 12;
   public static final int SM_TYPE_SYSTEM_STRING = 13;
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
   // S2-T05: character identity captured directly from CharSelectInfo (0x13) at handshake,
   // so the build knows who it really is even before UserInfo/CharInfo arrive.
   private String charSelectName = null;
   private int charSelectClassId = 0;
   private int charSelectRaceId = 0;
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
   private final java.util.Map<Integer, Long> inventoryItems = new ConcurrentHashMap<>();

   // ============ WPT-29: datapack-backed name resolution (NPC + item display names) ============
   private DatapackNames datapackNames = new DatapackNames();

   // ============ WPT-24: Inventory v2 (equipped vs loose) ============
   // Per-objectId inventory records parsed from ItemList(0x1B) with the real 36-byte item writer
   // layout (ItemList.java writeImpl -> AbstractItemPacket.writeItem). Guarded by inventoryLock.
   private final Object inventoryLock = new Object();
   private final java.util.List<InventoryItem> inventoryRecords = new java.util.ArrayList<>();
   private final java.util.Set<Integer> equippedItemIds = new ConcurrentHashMap<>().newKeySet();

   // ============ WPT-22: SystemMessage + chat typed telemetry ============
   private final Object messageLock = new Object();
   private static final int MAX_MESSAGE_EVENTS = 512;
   private final java.util.List<SystemMessageEvent> systemMessageEvents = new java.util.ArrayList<>();
   private volatile SystemMessageEvent lastSystemMessage = null;
   private final java.util.List<ChatEvent> chatEvents = new java.util.ArrayList<>();
   private volatile ChatEvent lastChatEvent = null;
   private volatile int systemMessageCount = 0;
   private volatile int chatCount = 0;

   // Quest tracking (Task 36)
   private int activeQuestCount = 0;

   // ============ WPT-27: quest journal telemetry ============
   // Real per-quest state parsed from the top-level QUEST_LIST (0x80) packet the server sends on
   // enter-world. questId -> 4-byte journal state (a positive cond step, or the completedStepFlags
   // bitmask with bit 31 set when steps were skipped). A fresh 0x80 REPLACES the whole journal, so
   // we rebuild the map each time it arrives rather than merge.
   private final ConcurrentHashMap<Integer, Integer> activeQuests = new ConcurrentHashMap<>();
   private int questListCount = 0;
   private int totalQuestCount = 0;

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
            case OP_CHAR_SELECT_INFO:
               parseCharSelectInfo(buf);
               break;
            case OP_USER_INFO:
               parseUserInfo(buf);
               break;
            // Movement-sync: server broadcast at MOVE_START / STOP (see layout comments at constants).
            case OP_CHAR_MOVE_TO_LOCATION:
               parseCharMoveToLocation(buf);
               break;
            case OP_STOP_MOVE:
               parseStopMove(buf);
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
            case OP_QUEST_LIST:
               questListCount++;
               parseQuestList(buf);
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
            // WPT-22: server->client chat broadcasts -> typed 'chat' events.
            case OP_NPC_SAY:
            case OP_CREATURE_SAY:
               parseChat(buf, opcode);
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
    * Parse CharSelectInfo packet (opcode 0x13) - the character-select screen snapshot. Layout from
    * SourceCode CharSelectionInfo.java writeImpl (Interlude):
    *   [count:int]
    *   per char: [name:UTF-16LE+null][objectId:int][account:UTF-16LE+null][sessionId:int]
    *             [clanId:int][builder:int][sex:int][race:int][baseClassId:int]...
    * We parse only the FIRST character entry (bots run a single-character account; charSlot is
    * applied later by GameServerClient), capturing NAME / RACE / CLASS (baseClassId).
    * buf is positioned right after the opcode.
    */
   private void parseCharSelectInfo(ByteBuffer buf)
   {
      try
      {
         int count = buf.getInt();
         if (count < 1)
         {
            return;
         }
         String name = readUtf16Le(buf);
         int objId = buf.getInt();
         readUtf16Le(buf);       // account name
         buf.getInt();           // session id
         buf.getInt();           // clan id
         buf.getInt();           // builder level
         buf.getInt();           // sex
         int raceId = buf.getInt();
         int classId = buf.getInt();
         charSelectName = name;
         charSelectClassId = classId;
         charSelectRaceId = raceId;
         if (charName == null && name != null && !name.isEmpty())
         {
            charName = name;
         }
         LOGGER.info("[PACKET-LOG] [" + playerName + "] CHAR_SELECT_INFO: name=" + name
            + " objId=" + objId + " raceId=" + raceId + " classId=" + classId);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] CharSelectInfo parse incomplete");
      }
   }

   /**
    * Public entry point for the handshake loop (GameServerClient): feed a raw CharSelectInfo
    * payload (starting with the opcode byte) so the build records the char's name/class/race.
    */
   public void recordCharSelectInfo(byte[] payloadBody)
   {
      if (payloadBody == null || payloadBody.length < 2)
      {
         return;
      }
      try
      {
         ByteBuffer buf = ByteBuffer.wrap(payloadBody);
         buf.order(ByteOrder.LITTLE_ENDIAN);
         buf.get(); // skip opcode
         parseCharSelectInfo(buf);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] CharSelectInfo record incomplete");
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

   /**
    * Parse CharMoveToLocation packet (opcode 0x01, SERVER->CLIENT). Layout from
    * serverpackets/MoveToLocation.java writeImpl (Interlude): [objId][xDest][yDest][zDest][x][y][z].
    * The server broadcasts this to every known player when a Creature starts a walk. Interlude does
    * NOT stream ValidateLocation per-tick while walking, so this is the ONLY mid-walk self-position
    * feed the bot has; without it the engine's playerX/Y/Z stays frozen at the last UserInfo stop and
    * the fleet's hop routes are abandoned as "unreachable" even though the server walked the char
    * exactly there (live-verified TIM-001 / 2026-08-15).
    */
   private void parseCharMoveToLocation(ByteBuffer buf)
   {
      try
      {
         int objId = buf.getInt();
         int xDest = buf.getInt();
         int yDest = buf.getInt();
         int zDest = buf.getInt();
         int x = buf.getInt();
         int y = buf.getInt();
         int z = buf.getInt();
         boolean isSelf = selfObjectId == 0 || objId == selfObjectId;
         EntityInfo entity = entitiesById.get(objId);
         if (entity != null)
         {
            entity.x = x;
            entity.y = y;
            entity.z = z;
         }
         if (isSelf)
         {
            playerX = x;
            playerY = y;
            playerZ = z;
            if (buf.remaining() >= 4)
            {
               playerHeading = buf.getInt();
            }
         }
         LOGGER.fine("[" + playerName + "] CHAR_MOVE_TO_LOCATION objId=" + objId
            + " cur=(" + x + "," + y + "," + z + ") dest=(" + xDest + "," + yDest + "," + zDest
            + ") self=" + isSelf);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] CharMoveToLocation parse incomplete");
      }
   }

   /**
    * Parse StopMove (opcode 0x47) — the server acknowledges the char reached its destination.
    * Layout from serverpackets/StopMove.java writeImpl: [objId][x][y][z][heading]. Updates the
    * self position to the server-confirmed arrival point (and any tracked entity).
    */
   private void parseStopMove(ByteBuffer buf)
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
         LOGGER.fine("[" + playerName + "] STOP_MOVE objId=" + objId
            + " pos=(" + x + "," + y + "," + z + ") self=" + isSelf);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] StopMove parse incomplete");
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
         // The packet's isAttackable field IS the server's ground truth for "can this player attack this NPC":
         //   _isAttackable = cha.isAutoAttackable(attacker)  from AbstractNpcInfo.java.
         // Monsters (Attackable template) → true → 1; town NPCs (merchants/guards/quest givers) → false → 0.
         // The old range-heuristic fallback (npcId < 200000) tagged merchants/guards as hostile, causing the
         // fleet to chase them forever with zero XP. Rely on the packet truth alone.
         boolean isHostile = isAttackable != 0;

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
    * Parse ItemList packet (opcode 0x1B) - inventory contents.
    *
    * <p>WPT-24 (Inventory v2): per-item writer layout verified against
    * SourceCode/.../serverpackets/ItemList.java writeImpl -> AbstractItemPacket.writeItem:
    * <pre>
    *   [type1:short][objectId:int][itemId:int][count:int][type2:short][customType1:short]
    *   [equipped:short][slot:int][enchant:short][customType2:short][augmentation:int][mana:int] = 36 bytes
    * </pre>
    * {@code equipped} (0/1) and {@code slot} (body-part mask) distinguish equipped vs loose items,
    * and item display names are resolved through the WPT-29 {@link DatapackNames} resolver.
    */
   private void parseItemList(ByteBuffer buf)
   {
      try
      {
         int showWindow = buf.getShort() & 0xFFFF;
         int itemCount = buf.getShort() & 0xFFFF;
         inventoryItems.clear();
         java.util.List<InventoryItem> records = new java.util.ArrayList<>(itemCount);
         java.util.Set<Integer> equipped = new java.util.HashSet<>();
         // WPT-24: the real server writes 36 bytes/item (verified writeImpl). Some pre-WPT-24
         // engine fixtures (StreamETradeTest.writeItem) use a historical 28-byte stride. itemId +
         // count share the same offsets in both, so we auto-detect the stride from the frame length.
         int remaining = buf.remaining();
         int stride = 36;
         if (remaining < (long) itemCount * 36)
         {
            stride = 28; // legacy 28-byte stride (2+4+4+4+2+2+4+2+2+2)
            if (remaining < (long) itemCount * 28)
            {
               stride = 16; // minimal identity-only intro (type1/objId/itemId/count + 2 skip)
            }
         }
         for (int i = 0; i < itemCount; i++)
         {
            if (buf.remaining() < stride) break;
            buf.getShort();                       // type1
            int objectId = buf.getInt();
            int itemId = buf.getInt();
            long count = buf.getInt() & 0xFFFFFFFFL;
            boolean isEquipped = false;
            int slot = 0;
            if (stride == 36)
            {
               buf.getShort();                    // type2
               buf.getShort();                    // customType1 (filler)
               isEquipped = (buf.getShort() & 0xFFFF) == 1; // 0 = loose, 1 = equipped
               slot = buf.getInt();               // body-part mask (slot)
               buf.getShort();                    // enchant
               buf.getShort();                    // customType2
               buf.getInt();                      // augmentation
               buf.getInt();                      // mana
            }
            else if (stride == 28)
            {
               // historical stride: skip the remaining tail (no equipped/slot info).
               for (int t = 0; t < 14; t++) buf.get();
            }
            else
            {
               for (int t = 0; t < 2; t++) buf.get(); // 16-byte identity-only intro
            }
            if (itemId == 57) this.adena = (int) Math.min(count, Integer.MAX_VALUE);
            inventoryItems.put(itemId, count);
            if (isEquipped) equipped.add(itemId);
            records.add(new InventoryItem(objectId, itemId, count, isEquipped, slot,
               datapackNames.resolveItemName(itemId)));
         }
         synchronized (inventoryLock)
         {
            inventoryRecords.clear();
            inventoryRecords.addAll(records);
            equippedItemIds.clear();
            equippedItemIds.addAll(equipped);
         }
         int totalSlots = 120;
         this.inventoryUsagePercent = (int)((itemCount * 100.0) / totalSlots);
         if (inventoryUsagePercent > 100) inventoryUsagePercent = 100;
         LOGGER.info("[PACKET-LOG] [" + playerName + "] ITEM_LIST: showWindow=" + showWindow
                 + " itemCount=" + itemCount + " equipped=" + equipped.size() + " stride=" + stride
                 + " usage=" + inventoryUsagePercent
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
    * WPT-27: Parse the top-level QUEST_LIST (0x80) "quest journal" packet — the one the server
    * sends on enter-world with the character's full log. Verified Interlude layout
    * (QuestList.java writeImpl): [count:2][per quest: questId:4][state:4]. The 4-byte state is
    * either the current cond step or the completedStepFlags bitmask (bit 31 set = some steps were
    * skipped). We record every entry; a quest counts as active (in the journal with progress)
    * whenever its state is non-zero.
    */
   private void parseQuestList(ByteBuffer buf)
   {
      try
      {
         int questCount = buf.getShort() & 0xFFFF;
         activeQuests.clear();
         int active = 0;
         for (int i = 0; i < questCount && buf.remaining() >= 8; i++)
         {
            int questId = buf.getInt();
            int state = buf.getInt();
            activeQuests.put(questId, state);
            if (state != 0) active++;
         }
         this.totalQuestCount = activeQuests.size();
         this.activeQuestCount = active;
         LOGGER.info("[PACKET-LOG] [" + playerName + "] QUEST_LIST: total=" + totalQuestCount
            + " active=" + activeQuestCount);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] QuestList parse incomplete");
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

   /** Parse SystemMessage packet (0x64) into a typed human-readable event (WPT-22). */
   private void parseSystemMessage(ByteBuffer buf)
   {
      try
      {
         // Layout (SystemMessage.java writeImpl): [msgId:int][paramCount:int][(type:int, value...)*]
         int msgId = buf.getInt();
         int paramCount = buf.getInt();
         if (paramCount < 0) paramCount = 0;
         if (paramCount > 32) paramCount = 32; // hard cap against malformed frames
         java.util.List<String> decoded = new java.util.ArrayList<>(paramCount);
         java.util.List<Arg> args = new java.util.ArrayList<>(paramCount);
         for (int i = 0; i < paramCount; i++)
         {
            int type = buf.getInt();
            Arg arg = decodeSysMsgParam(buf, type);
            if (arg == null) break;
            args.add(arg);
            decoded.add(arg.rendered);
         }
         String human = buildSystemMessageText(msgId, decoded);
         long now = System.currentTimeMillis();
         SystemMessageEvent event = new SystemMessageEvent(msgId, now, human, args);
         synchronized (messageLock)
         {
            systemMessageCount++;
            systemMessageEvents.add(event);
            while (systemMessageEvents.size() > MAX_MESSAGE_EVENTS) systemMessageEvents.remove(0);
            lastSystemMessage = event;
         }
         LOGGER.info("[PACKET-LOG] [" + playerName + "] SYSMESSAGE: " + human);
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] SystemMessage parse incomplete");
      }
   }

   /** Decode one SystemMessage param of the given type, returning a rendered Arg or null. */
   private Arg decodeSysMsgParam(ByteBuffer buf, int type)
   {
      switch (type)
      {
         case SM_TYPE_TEXT:
         case SM_TYPE_PLAYER_NAME:
            return new Arg(type, readUtf16String(buf));
         case SM_TYPE_LONG_NUMBER:
            return new Arg(type, Long.toString(buf.getLong()));
         case SM_TYPE_SKILL_NAME:
         {
            int skillId = buf.getInt();
            int skillLvl = buf.getInt();
            return new Arg(type, "skill:" + skillId + " (lv " + skillLvl + ")");
         }
         case SM_TYPE_ZONE_NAME:
         {
            int x = buf.getInt();
            int y = buf.getInt();
            int z = buf.getInt();
            return new Arg(type, "zone(" + x + "," + y + "," + z + ")");
         }
         case SM_TYPE_NPC_NAME:
         {
            int npcId = buf.getInt();
            return new Arg(type, datapackNames.resolveNpcName(npcId));
         }
         case SM_TYPE_ITEM_NAME:
         {
            int itemId = buf.getInt();
            return new Arg(type, datapackNames.resolveItemName(itemId));
         }
         case SM_TYPE_INT_NUMBER:
         case SM_TYPE_CASTLE_NAME:
         case SM_TYPE_ELEMENT_NAME:
         case SM_TYPE_INSTANCE_NAME:
         case SM_TYPE_DOOR_NAME:
         case SM_TYPE_SYSTEM_STRING:
            return new Arg(type, Integer.toString(buf.getInt()));
         default:
            return null; // unknown type: stop decoding conservatively
      }
   }


   /** Build a human-readable one-line string from a msgId + decoded params. */
   static String buildSystemMessageText(int msgId, java.util.List<String> params)
   {
      StringBuilder sb = new StringBuilder(64);
      sb.append("sysmsg#").append(msgId);
      if (params != null && !params.isEmpty())
      {
         sb.append(" [");
         for (int i = 0; i < params.size(); i++)
         {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i));
         }
         sb.append(']');
      }
      return sb.toString();
   }

   /**
    * Parse server->client chat broadcasts (NpcSay 0x02 / CreatureSay 0x4A) into typed 'chat'
    * events (WPT-22). NpcSay layout is unambiguous (NpcSay.java writeImpl):
    *   [objectId:int][textType:int][npcId+1000000:int][text:UTF-16LE NUL]
    * CreatureSay is best-effort (the common player-chat form):
    *   [objectId:int][chatType:int][senderName:UTF-16LE NUL][text:UTF-16LE NUL]
    */
   private void parseChat(ByteBuffer buf, int opcode)
   {
      try
      {
         int objectId = buf.getInt();
         int chatType = buf.getInt();
         long now = System.currentTimeMillis();
         if (opcode == OP_NPC_SAY)
         {
            int encodedNpcId = buf.getInt(); // npc template displayId + 1000000 (NpcSay.java)
            int npcId = encodedNpcId - 1_000_000;
            if (npcId < 0) npcId = 0;
            String text = readUtf16String(buf);
            if (text == null) return;
            ChatEvent event = new ChatEvent(now, "npc", chatType,
               datapackNames.resolveNpcName(npcId), text, objectId);
            recordChat(event);
         }
         else
         {
            String senderName = readUtf16String(buf);
            String text = readUtf16String(buf);
            if (text == null) return;
            ChatEvent event = new ChatEvent(now, "player", chatType, senderName, text, objectId);
            recordChat(event);
         }
      }
      catch (Exception e)
      {
         LOGGER.fine("[" + playerName + "] Chat parse incomplete");
      }
   }

   private void recordChat(ChatEvent event)
   {
      synchronized (messageLock)
      {
         chatCount++;
         chatEvents.add(event);
         while (chatEvents.size() > MAX_MESSAGE_EVENTS) chatEvents.remove(0);
         lastChatEvent = event;
      }
      LOGGER.info("[PACKET-LOG] [" + playerName + "] CHAT kind=" + event.kind
         + " speaker=" + event.speaker + " text=\"" + event.text + "\"");
   }

   /** Read an L2J UTF-16LE, NUL-terminated string from the buffer (no length prefix). */
   private String readUtf16String(ByteBuffer buf)
   {
      if (buf.remaining() < 2) return null;
      StringBuilder sb = new StringBuilder();
      while (buf.remaining() >= 2)
      {
         char c = buf.getChar(); // buffer is LITTLE_ENDIAN, so getChar decodes LE correctly
         if (c == 0) break;
         sb.append(c);
      }
      return sb.toString();
   }

   /** Simple value holder for one decoded SystemMessage param. */
   public static final class Arg
   {
      public final int type;
      public final String rendered;
      Arg(int type, String rendered)
      {
         this.type = type;
         this.rendered = rendered == null ? "" : rendered;
      }
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
   /** S2-T05: character name from CharSelectInfo (0x13) at handshake; null until seen. */
   public String getCharSelectName() { return charSelectName; }
   /** S2-T05: character classId (base class) from CharSelectInfo (0x13); 0 until seen. */
   public int getCharSelectClassId() { return charSelectClassId; }
   /** S2-T05: character raceId from CharSelectInfo (0x13); 0 until seen. */
   public int getCharSelectRaceId() { return charSelectRaceId; }
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

   // ============ WPT-29: datapack name resolution wiring ============
   /** Override the name resolver (test/telemetry hook); defaults to the lazy datapack resolver. */
   public void setDatapackNames(DatapackNames resolver)
   {
      this.datapackNames = resolver != null ? resolver : new DatapackNames();
   }

   /** Resolve an NPC template id to its datapack display name (WPT-29). */
   public String resolveNpcName(int npcId) { return datapackNames.resolveNpcName(npcId); }

   /** Resolve an item template id to its datapack display name (WPT-29). */
   public String resolveItemName(int itemId) { return datapackNames.resolveItemName(itemId); }

   // ============ WPT-24: Inventory v2 getters ============
   /** All per-item inventory records (objectId / itemId / count / equipped / slot / name). */
   public java.util.List<InventoryItem> getInventoryRecords()
   {
      synchronized (inventoryLock)
      {
         return new java.util.ArrayList<>(inventoryRecords);
      }
   }

   /** Item template ids currently equipped (equipped flag set in the ItemList frame). */
   public java.util.Set<Integer> getEquippedItemIds()
   {
      synchronized (inventoryLock)
      {
         return new java.util.LinkedHashSet<>(equippedItemIds);
      }
   }

   public int getEquippedItemCount() { synchronized (inventoryLock) { return equippedItemIds.size(); } }
   public boolean isItemEquipped(int itemId) { synchronized (inventoryLock) { return equippedItemIds.contains(itemId); } }
   public int getInventoryRecordCount() { synchronized (inventoryLock) { return inventoryRecords.size(); } }

   // ============ WPT-22: SystemMessage / chat getters ============
   public SystemMessageEvent getLastSystemMessage() { return lastSystemMessage; }
   public int getSystemMessageCount() { return systemMessageCount; }
   public int getChatCount() { return chatCount; }
   public ChatEvent getLastChatEvent() { return lastChatEvent; }

   /** Bounded copy of all retained SystemMessage events (oldest -> newest). */
   public java.util.List<SystemMessageEvent> getSystemMessageEvents()
   {
      synchronized (messageLock)
      {
         return new java.util.ArrayList<>(systemMessageEvents);
      }
   }

   /** Bounded copy of all retained chat events (oldest -> newest). */
   public java.util.List<ChatEvent> getChatEvents()
   {
      synchronized (messageLock)
      {
         return new java.util.ArrayList<>(chatEvents);
      }
   }

   // Quest tracking getters (Task 36)
   public int getActiveQuestCount() { return activeQuestCount; }
   public int getQuestInfoCount() { return questInfoCount; }

   // ============ WPT-27: quest journal telemetry getters ============
   /** Number of top-level QUEST_LIST (0x80) packets parsed. */
   public int getQuestListCount() { return questListCount; }
   /** Total quests in the latest journal (regardless of in-progress vs completed). */
   public int getTotalQuestCount() { return totalQuestCount; }
   /** WPT-27: live snapshot of the quest journal as {questId, state} pairs (QUEST_LIST 0x80). */
   public java.util.List<int[]> getActiveQuestList()
   {
      java.util.List<int[]> out = new java.util.ArrayList<>(activeQuests.size());
      for (java.util.Map.Entry<Integer, Integer> e : activeQuests.entrySet())
      {
         out.add(new int[] { e.getKey(), e.getValue() });
      }
      return out;
   }

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


   // ============ WPT-22: typed SystemMessage / Chat event classes ============
   /** One decoded SystemMessage (0x64). Carried as kind 'sysmsg' by the EventRing. */
   public static final class SystemMessageEvent
   {
      public final int msgId;
      public final long millis;
      public final String text;        // human-readable, e.g. "sysmsg#99 [Adena, Orc Fighter]"
      public final java.util.List<Arg> params;

      public SystemMessageEvent(int msgId, long millis, String text, java.util.List<Arg> params)
      {
         this.msgId = msgId;
         this.millis = millis;
         this.text = text;
         this.params = params == null ? java.util.Collections.emptyList() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(params));
      }

      @Override
      public String toString()
      {
         return "SystemMessageEvent[" + text + "]";
      }
   }

   /** One chat line from a server SAY broadcast (0x02/0x4A). Carried as kind 'chat'. */
   public static final class ChatEvent
   {
      public final long millis;
      public final String kind;        // "npc" or "player"
      public final int chatType;       // ChatType client id
      public final String speaker;     // resolved NPC name or player name
      public final String text;
      public final int objectId;

      public ChatEvent(long millis, String kind, int chatType, String speaker, String text, int objectId)
      {
         this.millis = millis;
         this.kind = kind;
         this.chatType = chatType;
         this.speaker = speaker == null ? "" : speaker;
         this.text = text == null ? "" : text;
         this.objectId = objectId;
      }

      @Override
      public String toString()
      {
         return "ChatEvent[" + kind + " " + speaker + ": " + text + "]";
      }
   }

   // ============ WPT-24: Inventory v2 record ============
   /** One item row from an ItemList(0x1B) frame: identity + equipped/loose + slot + resolved name. */
   public static final class InventoryItem
   {
      public final int objectId;
      public final int itemId;
      public final long count;
      public final boolean equipped;
      public final int slot;
      public final String name;

      public InventoryItem(int objectId, int itemId, long count, boolean equipped, int slot, String name)
      {
         this.objectId = objectId;
         this.itemId = itemId;
         this.count = count;
         this.equipped = equipped;
         this.slot = slot;
         this.name = name == null ? "item#" + itemId : name;
      }

      @Override
      public String toString()
      {
         return "InventoryItem[" + name + " x" + count + (equipped ? " EQUIPPED" : " loose")
            + " slot=0x" + Integer.toHexString(slot) + "]";
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
