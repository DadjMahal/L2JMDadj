package com.aiplayer.phase0.farm;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Records farm session analytics for long-term zone quality learning.
 * Tracks XP/h, adena/h, death rate, kill rate, and spot quality
 * per zone. Kept in-memory (no Redis/Postgres — verified S10-T09)
 * and fed back into FarmZoneScorer for better recommendations.
 *
 * Phase 0: In-memory queue only (no external persistence).
 * Phase 1: Cross-account aggregation for server-wide zone meta.
 *
 * Integration: DynamicZoneManager logs sessions. AIBrain calls
 * flushToRedis() periodically. LevelingPlanner reads aggregates.
 */
public final class FarmSessionRecorder {

    public static final class SessionRecord {
        public final String accountName;
        public final String zoneName;
        public final int spotX;
        public final int spotY;
        public final long startTime;
        public long endTime;
        public int startLevel;
        public int endLevel;
        public long startXp;
        public long endXp;
        public long startAdena;
        public long endAdena;
        public int kills;
        public int deaths;
        public int potionsUsed;
        public int soulshotsUsed;
        public long durationMs;
        public String className;

        public SessionRecord(String accountName, String zoneName, int spotX, int spotY,
                             long startTime, int startLevel, long startXp, long startAdena,
                             String className) {
            this.accountName = accountName;
            this.zoneName = zoneName;
            this.spotX = spotX;
            this.spotY = spotY;
            this.startTime = startTime;
            this.startLevel = startLevel;
            this.startXp = startXp;
            this.startAdena = startAdena;
            this.className = className;
        }

        public void finalize(long endTime, int endLevel, long endXp, long endAdena,
                             int kills, int deaths, int potionsUsed, int soulshotsUsed) {
            this.endTime = endTime;
            this.endLevel = endLevel;
            this.endXp = endXp;
            this.endAdena = endAdena;
            this.kills = kills;
            this.deaths = deaths;
            this.potionsUsed = potionsUsed;
            this.soulshotsUsed = soulshotsUsed;
            this.durationMs = endTime - startTime;
        }

        public double xpPerHour() {
            return durationMs > 0 ? ((endXp - startXp) * 3600000.0) / durationMs : 0;
        }

        public double adenaPerHour() {
            return durationMs > 0 ? ((endAdena - startAdena) * 3600000.0) / durationMs : 0;
        }

        public double killsPerHour() {
            return durationMs > 0 ? (kills * 3600000.0) / durationMs : 0;
        }

        public double deathRate() {
            return kills > 0 ? (double) deaths / kills : 0;
        }

        public boolean isValid() {
            return durationMs >= 60000 && kills >= 3;
        }
    }

    private final String accountName;
    // JedisPool removed — see flushToRedis()/zone-meta methods below
    private final Queue<SessionRecord> pendingRecords = new ConcurrentLinkedQueue<>();
    private SessionRecord activeSession = null;
    private long lastFlushTime = 0;
    private static final long FLUSH_INTERVAL_MS = 120000; // 2 min

    // Redis keys
    private static final String REDIS_FARM_PREFIX = "player:farm:";
    private static final String REDIS_ZONE_META_PREFIX = "zone:meta:";

    public FarmSessionRecorder(String accountName) {
        this.accountName = accountName;
    }

    // Real cross-bot zone-quality aggregation, in-memory instead of Redis:
    // shared across all AI Players in this JVM (was Redis, external to the
    // process — this is the equivalent within a single-process engine).
    private static final Map<String, ZoneMeta> ZONE_META =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static final class ZoneMeta {
        long totalSessions, totalKills, totalDeaths, totalXp, totalAdena, totalDurationMs;
    }

    /**
     * Start a new farm session.
     */
    public void startSession(String zoneName, int spotX, int spotY, BotStateSnapshot self) {
        if (activeSession != null) {
            // Auto-finalize previous
            endSession(self);
        }
        activeSession = new SessionRecord(
            accountName, zoneName, spotX, spotY,
            System.currentTimeMillis(), self.level, self.currentXp, self.adena,
            self.className != null ? self.className : "Unknown"
        );
    }

    /**
     * End the current session and queue for persistence.
     */
    public void endSession(BotStateSnapshot self) {
        if (activeSession == null) return;

        activeSession.finalize(
            System.currentTimeMillis(),
            self.level,
            self.currentXp,
            self.adena,
            0, // kills tracked externally
            0, // deaths tracked externally
            0, // potions tracked externally
            0  // soulshots tracked externally
        );

        if (activeSession.isValid()) {
            pendingRecords.offer(activeSession);
        }
        activeSession = null;
    }

    /**
     * Update kill count for active session.
     */
    public void recordKill() {
        if (activeSession != null) activeSession.kills++;
    }

    /**
     * Update death count for active session.
     */
    public void recordDeath() {
        if (activeSession != null) activeSession.deaths++;
    }

    /**
     * Update consumable usage.
     */
    public void recordPotionUsed() {
        if (activeSession != null) activeSession.potionsUsed++;
    }

    public void recordSoulshotUsed() {
        if (activeSession != null) activeSession.soulshotsUsed++;
    }

    /**
     * Periodic flush to Redis. Call every 2 min from AIBrain.
     */
    public void flushToRedis(long now) {
        if (now - lastFlushTime < FLUSH_INTERVAL_MS) return;
        lastFlushTime = now;

        List<SessionRecord> batch = new ArrayList<>();
        SessionRecord rec;
        while ((rec = pendingRecords.poll()) != null && batch.size() < 50) {
            batch.add(rec);
        }

        if (batch.isEmpty()) return;

        for (SessionRecord r : batch) {
            ZoneMeta meta = ZONE_META.computeIfAbsent(r.zoneName, k -> new ZoneMeta());
            synchronized (meta) {
                meta.totalSessions++;
                meta.totalKills += r.kills;
                meta.totalDeaths += r.deaths;
                meta.totalXp += (r.endXp - r.startXp);
                meta.totalAdena += (r.endAdena - r.startAdena);
                meta.totalDurationMs += r.durationMs;
            }
        }
    }

    /**
     * Get historical average XP/h for a zone from Redis.
     */
    public double getHistoricalXpPerHour(String zoneName) {
        ZoneMeta meta = ZONE_META.get(zoneName);
        if (meta == null || meta.totalDurationMs <= 0) return 0;
        return (meta.totalXp * 3600000.0) / meta.totalDurationMs;
    }

    /**
     * Get historical death rate for a zone.
     */
    public double getHistoricalDeathRate(String zoneName) {
        ZoneMeta meta = ZONE_META.get(zoneName);
        if (meta == null || meta.totalKills <= 0) return 0;
        return (double) meta.totalDeaths / meta.totalKills;
    }

    /**
     * Get recent sessions for this account.
     */
    public List<SessionRecord> getRecentSessions(int limit) {
        List<SessionRecord> result = new ArrayList<>();
        // In Phase 0, return from pending queue
        result.addAll(pendingRecords);
        if (result.size() > limit) {
            return result.subList(result.size() - limit, result.size());
        }
        return result;
    }

    public SessionRecord getActiveSession() {
        return activeSession;
    }

    public boolean hasActiveSession() {
        return activeSession != null;
    }

    public String getStatusReport() {
        int pending = pendingRecords.size();
        String active = activeSession != null
            ? activeSession.zoneName + " (" + activeSession.kills + " kills)"
            : "none";
        return String.format("FarmRecorder[%s: active=%s pending=%d]",
            accountName, active, pending);
    }
}
