package com.aiplayer.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WPT-23 / WPT-25 / WPT-26 regression tests for the PacketLogger combat & skill-cast layer.
 *
 * Frames are built EXACTLY per the Interlude (protocol 746) server writers:
 *   StatusUpdate       (0x0E) - SourceCode/.../serverpackets/StatusUpdate.java writeImpl
 *   Attack             (0x05) - SourceCode/.../serverpackets/Attack.java writeImpl
 *   Die                (0x06) - SourceCode/.../serverpackets/Die.java writeImpl
 *   MagicSkillUse      (0x48) - SourceCode/.../serverpackets/MagicSkillUse.java writeImpl
 *   MagicSkillLaunched (0x76) - SourceCode/.../serverpackets/MagicSkillLaunched.java writeImpl
 *   Client 0x2F gate          - SourceCode/.../clientpackets/RequestMagicSkillUse.java readImpl
 *                               + Documentation/Audit/42-skill-send-gate-opened.md (live proven)
 */
public class PacketLoggerExtraTest
{
   private static final int SELF = 1000;

   private static final int[][] ALL_NINE_ATTRS = {
      { 0x01, 22 },         // LEVEL
      { 0x02, 1_400_000 },  // EXP
      { 0x09, 480 },        // CUR_HP
      { 0x0A, 512 },        // MAX_HP
      { 0x0B, 41 },         // CUR_MP
      { 0x0C, 80 },         // MAX_MP
      { 0x0D, 3_300 },      // SP
      { 0x21, 120 },        // CUR_CP
      { 0x22, 200 },        // MAX_CP
   };

   // ==== HELPERS ====
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

   private static ByteBuffer statusUpdatePayload(int objectId, int[][] attrs)
   {
      ByteBuffer b = payload(0x0E);
      b.putInt(objectId);
      b.putInt(attrs.length);
      for (int[] a : attrs)
      {
         b.putInt(a[0]);
         b.putInt(a[1]);
      }
      return b;
   }

   private static ByteBuffer attackPayload(int attackerId, int targetId, int damage, int flags)
   {
      ByteBuffer b = payload(0x05);
      b.putInt(attackerId);
      b.putInt(targetId);
      b.putInt(damage);
      b.put((byte) flags);
      b.putInt(10);
      b.putInt(20);
      b.putInt(30);                       // attacker location
      b.putShort((short) 0);              // hits.size() - 1
      b.putInt(5);
      b.putInt(6);
      b.putInt(7);                        // target location
      return b;
   }

   private static ByteBuffer diePayload(int objectId)
   {
      ByteBuffer b = payload(0x06);
      b.putInt(objectId);
      b.putInt(0);                        // canTeleport
      b.putInt(0);
      b.putInt(0);
      b.putInt(0);                        // hideAway / castle / hq
      b.putInt(0);                        // sweepable
      b.putInt(0);                        // fixedRes
      return b;
   }

   private static ByteBuffer magicSkillUsePayload(int casterId, int targetId, int skillId,
                                                  int skillLevel, int hitTime, int reuseDelay, int critical)
   {
      ByteBuffer b = payload(0x48);
      b.putInt(casterId);
      b.putInt(targetId);
      b.putInt(skillId);
      b.putInt(skillLevel);
      b.putInt(hitTime);
      b.putInt(reuseDelay);
      b.putInt(100);
      b.putInt(200);
      b.putInt(30);                        // caster location
      b.putInt(critical);
      if (critical != 0) b.putShort((short) 0);
      b.putInt(50);
      b.putInt(60);
      b.putInt(7);                         // target location
      return b;
   }

   private static ByteBuffer magicSkillLaunchedPayload(int casterId, int skillId, int skillLevel,
                                                       int... targets)
   {
      ByteBuffer b = payload(0x76);
      b.putInt(casterId);
      b.putInt(skillId);
      b.putInt(skillLevel);
      b.putInt(targets.length);
      for (int t : targets) b.putInt(t);
      return b;
   }

   // ==== WPT23 ====
   // =====================================================================
   // WPT-23: full StatusUpdate attribute map per objectId
   // =====================================================================

   @Test
   public void statusUpdateFullAttributeMapCapturedPerObjectId()
   {
      PacketLogger logger = new PacketLogger("protobot");
      logger.setSelfObjectId(SELF);

      send(statusUpdatePayload(SELF, ALL_NINE_ATTRS), logger);

      PacketLogger.StatusSnapshot self = logger.getStatusSnapshot(SELF);
      assertNotNull(self, "self snapshot must exist after a self StatusUpdate");
      assertEquals(22, self.getLevel());
      assertEquals(1_400_000L, self.getExp());
      assertEquals(480, self.getCurHp());
      assertEquals(512, self.getMaxHp());
      assertEquals(41, self.getCurMp());
      assertEquals(80, self.getMaxMp());
      assertEquals(3_300, self.getSp());
      assertEquals(120, self.getCurCp());
      assertEquals(200, self.getMaxCp());
      // All 9 tracked attrs present -> bits 0..8
      assertEquals(0x1FF, self.getSeenMask(),
         "all 9 tracked attributes should be marked in the seen mask");
      assertTrue(self.getLastUpdateMillis() > 0, "snapshot must carry an update timestamp");
      // Accessor aliases agree.
      assertNotNull(logger.getSelfStatusSnapshot());
      assertEquals(self, logger.getSelfStatusSnapshot());
      assertEquals(1, logger.getStatusSnapshotCount());
      // Legacy scalar getters stay consistent for the self object.
      assertEquals(480, logger.getCurHp());
      assertEquals(22, logger.getLevel());
      assertEquals(1_400_000L, logger.getExp());
   }

   @Test
   public void statusUpdateSnapshotsArePerEntityAndMergePartialPackets()
   {
      PacketLogger logger = new PacketLogger("protobot");
      logger.setSelfObjectId(SELF);

      // Self packet 1: only EXP + LEVEL.  Self packet 2: the rest of the tracked set.
      send(statusUpdatePayload(SELF, new int[][] { { 0x02, 900_000 }, { 0x01, 20 } }), logger);
      send(statusUpdatePayload(SELF, new int[][] {
         { 0x09, 300 }, { 0x0A, 400 }, { 0x0B, 35 }, { 0x0C, 60 },
         { 0x0D, 1_100 }, { 0x21, 90 }, { 0x22, 180 },
      }), logger);

      // Nearby wolf packet: only HP - must NOT clobber the self snapshot.
      send(statusUpdatePayload(777, new int[][] { { 0x09, 55 }, { 0x0A, 120 } }), logger);

      PacketLogger.StatusSnapshot self = logger.getStatusSnapshot(SELF);
      assertEquals(20, self.getLevel(), "level merged from packet 1");
      assertEquals(900_000L, self.getExp(), "exp merged from packet 1");
      assertEquals(300, self.getCurHp(), "hp merged from packet 2");
      assertEquals(400, self.getMaxHp());
      assertEquals(1_100, self.getSp());
      assertEquals(90, self.getCurCp());
      assertEquals(0x1FF, self.getSeenMask(), "partial packets must merge into the full set");

      PacketLogger.StatusSnapshot wolf = logger.getStatusSnapshot(777);
      assertNotNull(wolf, "monster StatusUpdate must produce its own snapshot");
      assertEquals(55, wolf.getCurHp());
      assertEquals(120, wolf.getMaxHp());
      assertEquals(0, wolf.getLevel(), "wolf snapshot must not inherit the player's attrs");
      // The self scalar getters must be driven only by the SELF snapshot (Slice 6 safety).
      assertEquals(300, logger.getCurHp());
      assertEquals(2, logger.getStatusSnapshotCount());
      assertNull(logger.getStatusSnapshot(999_999), "unknown objectId must have no snapshot");
   }

   // ==== WPT25 ====
   // =====================================================================
   // WPT-25: combat KPIs - hits, damage in/out, rolling DPS, kills
   // =====================================================================

   @Test
   public void attackFeedsDamageOutHitsAndDps()
   {
      PacketLogger logger = new PacketLogger("protobot");
      logger.setSelfObjectId(SELF);

      send(attackPayload(SELF, 42, 120, 0), logger);
      send(attackPayload(SELF, 42, 80, 0x20), logger);   // critical hit flag

      assertEquals(2, logger.getHitsOut());
      assertEquals(200L, logger.getDamageOut());
      assertEquals(0, logger.getMissesOut());
      assertEquals(1, logger.getCritsOut());
      assertEquals(2, logger.getAttackPacketCount());
      assertTrue(logger.getDps() > 0, "DPS must be > 0 right after damage is dealt");
      assertEquals(0, logger.getHitsIn(), "no incoming damage in this scenario");
   }

   @Test
   public void attackMissAndDamageInAreTracked()
   {
      PacketLogger logger = new PacketLogger("protobot");
      logger.setSelfObjectId(SELF);

      send(attackPayload(777, SELF, 55, 0), logger);     // mob hits the bot
      send(attackPayload(SELF, 42, 0, 0x80), logger);    // bot swings and MISSES

      assertEquals(1, logger.getHitsIn());
      assertEquals(55L, logger.getDamageIn());
      assertEquals(1, logger.getHitsOut());
      assertEquals(1, logger.getMissesOut());
      assertEquals(0L, logger.getDamageOut(), "a missed swing deals 0 damage");
   }

   @Test
   public void dieAttributionCountsKillsAndSelfDeaths()
   {
      PacketLogger logger = new PacketLogger("protobot");
      logger.setSelfObjectId(SELF);

      // Damage wolf 42, then it dies -> 1 kill.
      send(attackPayload(SELF, 42, 250, 0), logger);
      send(diePayload(42), logger);
      // An undamaged entity dying must NOT count.
      send(diePayload(999), logger);
      // A magic kill: cast skill 3 on wolf 50 (0x48), beast dies -> 2nd kill.
      send(magicSkillUsePayload(SELF, 50, 3, 1, 2400, 10_000, 0), logger);
      send(diePayload(50), logger);
      // The bot itself dying counts as a death, not a kill.
      send(diePayload(SELF), logger);

      assertEquals(2, logger.getKills());
      assertEquals(1, logger.getDeaths());
      assertEquals(4, logger.getDiePacketCount());
   }

   @Test
   public void combatKpisSnapshotAndReset()
   {
      PacketLogger logger = new PacketLogger("protobot");
      logger.setSelfObjectId(SELF);

      send(attackPayload(SELF, 42, 150, 0), logger);
      send(attackPayload(777, SELF, 30, 0), logger);
      send(diePayload(42), logger);

      PacketLogger.CombatKpis kpis = logger.getCombatKpis();
      assertEquals(1, kpis.hitsOut);
      assertEquals(150L, kpis.damageOut);
      assertEquals(1, kpis.hitsIn);
      assertEquals(30L, kpis.damageIn);
      assertEquals(1, kpis.kills);
      assertEquals(0, kpis.deaths);
      assertTrue(kpis.dps > 0, "KPI snapshot must include a live DPS figure");

      logger.resetCombatKpis();
      assertEquals(0, logger.getHitsOut());
      assertEquals(0L, logger.getDamageOut());
      assertEquals(0, logger.getHitsIn());
      assertEquals(0, logger.getKills());
      assertEquals(0.0, logger.getDps(), 0.0001);
   }

   // ==== WPT26 ====
   // =====================================================================
   // WPT-26: MagicSkillUse / skill-cast metering
   // =====================================================================

   @Test
   public void magicSkillUseServerPacketRecordsConfirmedCast()
   {
      PacketLogger logger = new PacketLogger("protobot");
      logger.setSelfObjectId(SELF);

      send(magicSkillUsePayload(SELF, 42, 3, 2, 2400, 10_000, 0), logger);

      assertEquals(1, logger.getMagicSkillUseCount());
      assertEquals(1, logger.getSelfSkillCastTotal());
      assertEquals(1, logger.getSkillCastCount(3));
      assertEquals(1, logger.getSkillCastCountByTarget(42));
      assertEquals(1, logger.getSkillCastTotal());
      assertEquals(10_000, logger.getSkillReuseDelayMs(3));
      assertTrue(logger.getSkillReuseRemainingMs(3) > 0, "just cast -> cooldown should be running");
      assertEquals(0, logger.getSkillReuseRemainingMs(999), "unknown skill must have no cooldown");
      assertTrue(logger.getLastSkillCastMillis(3) > 0);
      assertTrue(logger.getLastSkillCastMillis() > 0);

      PacketLogger.SkillCastEvent last = logger.getLastSkillCast();
      assertNotNull(last, "a confirmed cast must update the last-cast event");
      assertEquals(3, last.skillId);
      assertEquals(42, last.targetId);
      assertEquals(SELF, last.casterId);
      assertEquals(2, last.skillLevel);
      assertEquals(2400, last.hitTimeMs);
      assertEquals(10_000, last.reuseDelayMs);
      assertTrue(last.confirmed, "server 0x48 packet = confirmed cast");
      assertTrue(last.selfCast);
   }

   @Test
   public void magicSkillUseCriticalVariantParses()
   {
      PacketLogger logger = new PacketLogger("protobot");
      logger.setSelfObjectId(SELF);

      // critical=1 writes an extra short(0) after the int - the critical branch.
      send(magicSkillUsePayload(SELF, 42, 10, 1, 1500, 5000, 1), logger);

      assertEquals(1, logger.getMagicSkillUseCount());
      assertEquals(1, logger.getSkillCastCount(10));
      PacketLogger.SkillCastEvent last = logger.getLastSkillCast();
      assertNotNull(last);
      assertEquals(10, last.skillId);
      assertEquals(42, last.targetId);
      assertEquals(1, last.skillLevel);
   }

   @Test
   public void magicSkillUseByOtherCasterIsCountedButNotSelf()
   {
      PacketLogger logger = new PacketLogger("protobot");
      logger.setSelfObjectId(SELF);

      send(magicSkillUsePayload(777, 9, 101, 3, 1200, 8000, 0), logger);

      assertEquals(1, logger.getMagicSkillUseCount(), "observed enemy casts still counted");
      assertEquals(1, logger.getSkillCastCount(101));
      assertEquals(0, logger.getSelfSkillCastTotal(), "enemy cast must not count as the bot's own");
      assertEquals(1, logger.getSkillCastTotal());
      PacketLogger.SkillCastEvent last = logger.getLastSkillCast();
      assertNotNull(last);
      assertEquals(777, last.casterId);
      assertTrue(!last.selfCast, "enemy cast must be flagged as not self");
   }

   @Test
   public void recordSkillRequestMetersClientGate()
   {
      PacketLogger logger = new PacketLogger("protobot");
      logger.setSelfObjectId(SELF);

      logger.recordSkillRequest(3, 1, 0);   // ctrl pressed, shift not pressed

      assertEquals(1, logger.getSkillRequestCount());
      assertEquals(0, logger.getMagicSkillUseCount(), "request alone is not a server confirmation");

      List<PacketLogger.SkillCastEvent> events = logger.getSkillCastEvents();
      assertEquals(1, events.size());
      PacketLogger.SkillCastEvent req = events.get(0);
      assertEquals(3, req.skillId);
      assertTrue(!req.confirmed, "client 0x2F frame is a request, not a confirmation");
      assertTrue(req.selfCast);
      assertNull(logger.getLastSkillCast(), "last-confirmed cast must stay null until server 0x48");
   }

   @Test
   public void magicSkillLaunchedParsesTargetsAndTimestamps()
   {
      PacketLogger logger = new PacketLogger("protobot");
      logger.setSelfObjectId(SELF);

      send(magicSkillLaunchedPayload(SELF, 3, 2, 42, 43), logger);

      assertEquals(1, logger.getMagicSkillLaunchedCount());
      assertTrue(logger.getLastSkillLaunchedMillis() > 0);
   }
}