package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class RaidBossAI {
    private static final Logger LOGGER = Logger.getLogger(RaidBossAI.class.getName());

    public enum BossType { MAJOR, MINOR, EVENT, DAILY }
    public enum DropType { WEAPON, ARMOR, ACCESSORY, MATERIAL, PET }

    public static class RaidConfig {
        public final String bossName;
        public final BossType type;
        public final int minLevel;
        public final int maxPlayers;
        public final int respawnHours;
        public final List<DropType> possibleDrops;

        public RaidConfig(String name, BossType t, int lvl, int maxP, int respawn, List<DropType> drops) {
            bossName = name; type = t; minLevel = lvl; maxPlayers = maxP; respawnHours = respawn; possibleDrops = drops;
        }
    }

    public static Map<String, RaidConfig> BOSS_REGISTRY = new LinkedHashMap<>();
    static {
        BOSS_REGISTRY.put("Valkyrie", new RaidConfig("Valkyrie", BossType.MAJOR, 60, 30, 300, List.of(DropType.WEAPON, DropType.ACCESSORY)));
        BOSS_REGISTRY.put("Frantz", new RaidConfig("Frantz", BossType.EVENT, 55, 50, 168, List.of(DropType.PET, DropType.ACCESSORY)));
        BOSS_REGISTRY.put("Antharas", new RaidConfig("Antharas", BossType.MINOR, 50, 40, 240, List.of(DropType.WEAPON, DropType.MATERIAL)));
    }

    public static boolean shouldAttempt(String bossName, int playerLevel, int partySize, long lastAttempt) {
        RaidConfig cfg = BOSS_REGISTRY.get(bossName);
        if (cfg == null) return false;
        return playerLevel >= cfg.minLevel &&
               partySize >= cfg.maxPlayers / 2 &&
               (System.currentTimeMillis() - lastAttempt) > cfg.respawnHours * 3600000L;
    }

    public static DropType predictDrop(String bossName) {
        RaidConfig cfg = BOSS_REGISTRY.get(bossName);
        if (cfg == null || cfg.possibleDrops.isEmpty()) return DropType.MATERIAL;
        return cfg.possibleDrops.get(0);
    }
}
