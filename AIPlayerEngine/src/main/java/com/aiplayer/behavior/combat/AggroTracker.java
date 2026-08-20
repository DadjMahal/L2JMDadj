package com.aiplayer.behavior.combat;

/** MODE: COMPLETE. Rewritten in-memory, same public API as the original Redis-backed version. */

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.aiplayer.behavior.ProfileStore;

/**
 * Per-AI-Player aggro tracking. In-memory, 2-minute TTL (matches the
 * original Redis-backed design's expiry) — replaces the raw Jedis calls
 * that required a Redis server. See ProfileStore.java for why: no new
 * infrastructure at this scale.
 */
public class AggroTracker {
    private static final long TTL_MS = 120_000;

    private final String accountName;
    private final Map<Integer, Entry> aggro = new ConcurrentHashMap<>();

    public AggroTracker(String accountName) {
        this.accountName = accountName;
    }

    /**
     * Record that a mob has aggroed this AI Player.
     */
    public void addAggro(int mobObjId, int mobLevel) {
        aggro.put(mobObjId, new Entry(mobLevel, System.currentTimeMillis() + TTL_MS));
    }

    /**
     * Remove aggro record (mob died, moved away, or reset).
     */
    public void removeAggro(int mobObjId) {
        aggro.remove(mobObjId);
    }

    /**
     * Check if a specific mob is aggroed on this AI Player.
     */
    public boolean isAggroedBy(int mobObjId) {
        Entry e = aggro.get(mobObjId);
        if (e == null) return false;
        if (System.currentTimeMillis() >= e.expiresAt) {
            aggro.remove(mobObjId);
            return false;
        }
        return true;
    }

    /**
     * Get all current aggro mob IDs (expired entries excluded and cleaned up).
     */
    public Set<Integer> getAggroList() {
        long now = System.currentTimeMillis();
        Set<Integer> result = new HashSet<>();
        for (Iterator<Map.Entry<Integer, Entry>> it = aggro.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Entry> e = it.next();
            if (now >= e.getValue().expiresAt) {
                it.remove();
            } else {
                result.add(e.getKey());
            }
        }
        return result;
    }

    /**
     * Clear all aggro (e.g., after death or zone change).
     */
    public void clear() {
        aggro.clear();
    }

    /**
     * Get count of mobs currently aggroed.
     */
    public int getAggroCount() {
        return getAggroList().size();
    }

    private static final class Entry {
        final int mobLevel;
        final long expiresAt;

        Entry(int mobLevel, long expiresAt) {
            this.mobLevel = mobLevel;
            this.expiresAt = expiresAt;
        }
    }
}
