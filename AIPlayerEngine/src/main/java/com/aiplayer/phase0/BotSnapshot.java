package com.aiplayer.phase0;

/** MODE: COMPLETE. Real state snapshot backed 1:1 by live PacketLogger getters. No fake fields. */

import com.aiplayer.protocol.PacketLogger;
import com.aiplayer.protocol.PacketLogger.EntityInfo;

/**
 * Immutable snapshot of an AI Player's real state, built directly from the
 * live, already-proven PacketLogger — not from a hand-maintained parallel
 * copy.
 *
 * WHY THIS CLASS EXISTS: GameStateMirror (the Phase 0 base, from the original
 * Kimi zip) introduced its own EntitySnapshot/BotStateSnapshot with fields
 * that no packet parser ever populated — the level field added by an
 * 11-file patch across Tasks 5-11 is the clearest example: every dependent
 * module silently ran at level=1 forever, because nothing fed it real data.
 * PacketLogger already parses CharInfo/StatusUpdate/NpcInfo packets and has
 * had a real, populated getLevel() (and everything else below) the whole
 * time — GameStateMirror was solving an already-solved problem, badly.
 *
 * Every field below is commented with the exact PacketLogger getter it maps
 * to. No field exists here that PacketLogger cannot actually fill.
 *
 * GameStateMirror is not deleted by this change (that would touch/break
 * whatever already imports it) — new phase0 modules should read from
 * BotSnapshot instead of GameStateMirror going forward. See
 * INTEGRATION_GAPS.md for which existing phase0 files still need migrating.
 */
public final class BotSnapshot {
    public final String accountName;

    // --- from PacketLogger.getLevel() / getCurHp() / getMaxHp() / getCurMp() / getMaxMp() ---
    public final int level;
    public final int hpCurrent;
    public final int hpMax;
    public final int mpCurrent;
    public final int mpMax;

    // --- from PacketLogger.getHpPercentage() / getMpPercentage() — real methods, already exist,
    //     no need to hand-compute this the way earlier phase0 patches did ---
    public final double hpPercent;
    public final double mpPercent;

    // --- from PacketLogger.getPlayerX() / getPlayerY() / getPlayerZ() / getPlayerHeading() ---
    public final int x;
    public final int y;
    public final int z;
    public final int heading;

    // --- from PacketLogger.getAdena() ---
    public final int adena;

    // --- from PacketLogger.getInventoryUsagePercent() / isInventoryFull() ---
    public final int inventoryUsagePercent;
    public final boolean inventoryFull;

    // --- from PacketLogger.getActiveQuestCount() ---
    public final int activeQuestCount;

    // --- from PacketLogger.getHostileEntities() / getEntityCount() ---
    public final EntityInfo[] hostileEntities;
    public final int entityCount;

    // --- from PacketLogger.getLastNpcHtml() ---
    public final String lastNpcHtml;

    private BotSnapshot(String accountName, int level, int hpCurrent, int hpMax, int mpCurrent, int mpMax,
                         double hpPercent, double mpPercent, int x, int y, int z, int heading, int adena,
                         int inventoryUsagePercent, boolean inventoryFull, int activeQuestCount,
                         EntityInfo[] hostileEntities, int entityCount, String lastNpcHtml) {
        this.accountName = accountName;
        this.level = level;
        this.hpCurrent = hpCurrent;
        this.hpMax = hpMax;
        this.mpCurrent = mpCurrent;
        this.mpMax = mpMax;
        this.hpPercent = hpPercent;
        this.mpPercent = mpPercent;
        this.x = x;
        this.y = y;
        this.z = z;
        this.heading = heading;
        this.adena = adena;
        this.inventoryUsagePercent = inventoryUsagePercent;
        this.inventoryFull = inventoryFull;
        this.activeQuestCount = activeQuestCount;
        this.hostileEntities = hostileEntities;
        this.entityCount = entityCount;
        this.lastNpcHtml = lastNpcHtml;
    }

    /**
     * Build a snapshot from the live PacketLogger. Every argument-free call
     * below is a real, already-existing, already-populated getter — nothing
     * here requires new packet parsing.
     */
    public static BotSnapshot from(String accountName, PacketLogger logger) {
        return new BotSnapshot(
            accountName,
            logger.getLevel(),
            logger.getCurHp(), logger.getMaxHp(),
            logger.getCurMp(), logger.getMaxMp(),
            logger.getHpPercentage(), logger.getMpPercentage(),
            logger.getPlayerX(), logger.getPlayerY(), logger.getPlayerZ(), logger.getPlayerHeading(),
            logger.getAdena(),
            logger.getInventoryUsagePercent(), logger.isInventoryFull(),
            logger.getActiveQuestCount(),
            logger.getHostileEntities(), logger.getEntityCount(),
            logger.getLastNpcHtml()
        );
    }

    /**
     * Nearest hostile within maxDistance, or null. Thin pass-through to the
     * real PacketLogger method — kept here so callers only ever depend on
     * BotSnapshot, not on PacketLogger directly, without duplicating any data.
     */
    public EntityInfo findNearestHostile(int maxDistance, PacketLogger logger) {
        return logger.findNearestHostile(x, y, z, maxDistance);
    }

    /**
     * All entities (not just hostile) within radius of this bot's position.
     * Real data via PacketLogger.getNearbyEntities() — this is what several
     * still-unmigrated Task 5-11 modules call GameStateMirror.getVisibleEntities()
     * for; that method never existed anywhere, this is the real equivalent.
     */
    public EntityInfo[] getNearbyEntities(int radius, PacketLogger logger) {
        return logger.getNearbyEntities(x, y, radius);
    }

    /**
     * Full inventory as ItemSnapshot list — joins real PacketLogger item
     * counts with real ItemDatabase metadata. See ItemSnapshot's own javadoc
     * for which fields are genuinely real vs. placeholder.
     */
    public java.util.List<ItemSnapshot> getInventory(PacketLogger logger) {
        java.util.List<ItemSnapshot> result = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Integer, Long> e : logger.getInventoryItems().entrySet()) {
            result.add(ItemSnapshot.from(e.getKey(), e.getValue()));
        }
        return result;
    }
}
