package com.aiplayer.core;

/** MODE: COMPLETE. Fields added per PATCH_GameStateMirror.txt (Task 2), PATCH_GameStateMirror_Task3.txt (Task 3), and PATCH_GameStateMirror_level_field.txt (Task 5-11 level field). All field requirements covered. */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mirrors game state from parsed server packets.
 * BotSnapshot and behavior modules read from here instead of using mock data.
 *
 * Updated by the packet reader thread when StatusUpdate, CharInfo,
 * NPC_INFO, Die, Revive packets arrive.
 */
public class GameStateMirror {
    private static final GameStateMirror INSTANCE = new GameStateMirror();
    public static GameStateMirror getInstance() { return INSTANCE; }

    private final ConcurrentHashMap<String, BotStateSnapshot> bots = new ConcurrentHashMap<>();

    public BotStateSnapshot get(String botName) {
        return bots.computeIfAbsent(botName, k -> new BotStateSnapshot());
    }

    public void updateHP(String botName, int current, int max) {
        BotStateSnapshot s = get(botName);
        s.hpCurrent = current;
        s.hpMax = max;
    }

    public void updateMP(String botName, int current, int max) {
        BotStateSnapshot s = get(botName);
        s.mpCurrent = current;
        s.mpMax = max;
    }

    public void updatePosition(String botName, int x, int y, int z) {
        BotStateSnapshot s = get(botName);
        s.x = x; s.y = y; s.z = z;
    }

    public void setTarget(String botName, int targetObjId, boolean isMob) {
        BotStateSnapshot s = get(botName);
        s.targetObjId = targetObjId;
        s.targetIsMob = isMob;
        s.hasTarget = targetObjId != 0;
    }

    public void setDead(String botName, boolean dead) {
        get(botName).isDead = dead;
    }

    public void addNearbyEntity(String botName, int objId, String name, int x, int y, int z, boolean isMob) {
        get(botName).nearby.put(objId, new EntitySnapshot(objId, name, x, y, z, isMob));
    }

    public void removeEntity(String botName, int objId) {
        get(botName).nearby.remove(objId);
    }

    /** Returns nearby visible entities (real equivalent of BotSnapshot.getNearbyEntities). */
    public List<EntitySnapshot> getVisibleEntities(BotStateSnapshot self, int radius) {
        List<EntitySnapshot> result = new ArrayList<>();
        for (EntitySnapshot e : self.nearby.values()) {
            double dist = Math.hypot(e.x - self.x, e.y - self.y);
            if (dist <= radius) result.add(e);
        }
        return result;
    }

    /** No-arg overload: returns all nearby entities within 2000 units of the default bot. */
    public List<EntitySnapshot> getVisibleEntities() {
        return getVisibleEntities(get("default"), 2000);
    }

    /** Returns the self snapshot for the given account (alias for get()). */
    public BotStateSnapshot getSelfSnapshot(String botName) {
        return get(botName);
    }

    /** No-arg overload: returns the default bot's snapshot. */
    public BotStateSnapshot getSelfSnapshot() {
        return get("default");
    }

    public static class BotStateSnapshot {
        public volatile int hpCurrent = 100, hpMax = 100;
        public volatile int mpCurrent = 100, mpMax = 100;
        public volatile int cpCurrent = 0, cpMax = 0;
        public volatile int x, y, z;
        public volatile int targetObjId = 0;
        public volatile boolean hasTarget = false;
        public volatile boolean targetIsMob = true;
        public volatile boolean isDead = false;
        public final ConcurrentHashMap<Integer, EntitySnapshot> nearby = new ConcurrentHashMap<>();

        // === Task 2 Extension: Combat Targeting & Aggro ===
        public int level = 1;
        public int classId = 0;
        public int objId = 0;
        public int aggroRange = 200;
        public boolean isAggressive = false;
        public boolean isBoss = false;
        public boolean isElite = false;
        public boolean isAttackable = true;
        public boolean isPlayer = false;
        public boolean isEnemy = false;
        public int mobType = 0;
        public int templateId = 0; // NPC template ID for respawn tracking

        // === Task 2 Extension: Party Member Snapshot ===
        public List<PartyMemberSnapshot> partyMembers = new ArrayList<>();

        // === Task 3 Extension: Movement State ===
        public boolean isMoving = false;
        public boolean isRunning = true;
        public int destX = 0;
        public int destY = 0;
        public int destZ = 0;
        public long lastMoveTime = 0;

        // === Task 4 Extension: Death & Respawn ===
        public long deathTime = 0;
        public int respawnX = 0, respawnY = 0, respawnZ = 0;
        public boolean isRecentlyRespawned = false;

        // === Task 5 Extension: Inventory & Consumables ===
        public int adena = 0;
        public List<com.aiplayer.core.ItemSnapshot> inventory = new ArrayList<>();
        public int inventoryUsagePercent = 0;
        public int inventorySlotsUsed = 0;
        public int inventorySlotsMax = 0;
        public boolean isOverweight = false;

        // === Task 7 Extension: Town / Vendor ===
        public boolean isInTown = false;
        public List<String> knownNpcNames = new ArrayList<>();

        // === Task 9 Extension: Quest & Leveling ===
        public int currentXp = 0;
        public int xpGainedThisSession = 0;

        // === Social / Misc ===
        public String name = "";
        public int raceId = 0;
        public String playerClass = "";
        public String className = "";
        public boolean isMageClass = false;
        public boolean isPhysicalClass = false;
        public boolean isInCombat = false;

        // === Convenience computed fields ===
        public double cpPercent() {
            return cpMax > 0 ? (cpCurrent * 100.0 / cpMax) : 0;
        }

        // === Task 3 Extension: Helper ===
        public boolean isRecentlyMoved(long withinMs) {
            return System.currentTimeMillis() - lastMoveTime < withinMs;
        }
    }

    public static class EntitySnapshot {
        public final int objId;
        public final String name;
        public final int x, y, z;
        public final boolean isMob;

        // === Task 2 Extension ===
        public int level = 0;
        public int aggroRange = 0;
        public boolean isAggressive = false;
        public boolean isBoss = false;
        public boolean isElite = false;
        public boolean isAttackable = true;
        public boolean isDead = false;
        public boolean isPlayer = false;
        public boolean isEnemy = false;
        public int mobType = 0;
        public int templateId = 0; // NPC template ID for respawn tracking

        public EntitySnapshot(int objId, String name, int x, int y, int z, boolean isMob) {
            this.objId = objId; this.name = name; this.x = x; this.y = y; this.z = z; this.isMob = isMob;
        }

        public EntitySnapshot(int objId, String name, int x, int y, int z, boolean isMob,
                             int level, int aggroRange, boolean isAggressive, boolean isBoss,
                             boolean isElite, boolean isAttackable, boolean isDead, boolean isPlayer,
                             boolean isEnemy, int mobType) {
            this.objId = objId; this.name = name; this.x = x; this.y = y; this.z = z; this.isMob = isMob;
            this.level = level; this.aggroRange = aggroRange; this.isAggressive = isAggressive;
            this.isBoss = isBoss; this.isElite = isElite; this.isAttackable = isAttackable;
            this.isDead = isDead; this.isPlayer = isPlayer; this.isEnemy = isEnemy;
            this.mobType = mobType;
        }
    }

    /** Party member snapshot — added Task 2 patch */
    public static class PartyMemberSnapshot {
        public int objectId;
        public String name;
        public int targetObjId;
        public boolean isLeader;
        public int level;
        public int hpPercent;
    }
}
