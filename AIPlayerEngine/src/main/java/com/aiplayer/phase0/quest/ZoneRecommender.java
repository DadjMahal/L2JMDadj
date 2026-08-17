package com.aiplayer.phase0.quest;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recommends level-appropriate farm and quest zones for AI Players.
 * Maps player level, class archetype, and play style to optimal locations.
 *
 * Zones are scored by:
 * - Level appropriateness (mobs within +/- 5 levels)
 * - Class fit (mage-friendly vs melee-friendly)
 * - Solo vs party viability
 * - Quest density (zones with many concurrent quests score higher)
 * - Travel distance from current location
 * - Danger level (lower = safer for solo)
 *
 * Integration with Task 11 (Farm Zone Intelligence) will extend this
 * with real-time density tracking and respawn timing.
 */
public final class ZoneRecommender {

    public enum ZoneTag {
        MELEE_FRIENDLY,    // Tight quarters OK, low casters
        MAGE_FRIENDLY,     // Open space, good for kiting
        RANGED_FRIENDLY,   // Open sightlines
        SOLO_VIABLE,       // Safe for solo play
        PARTY_RECOMMENDED, // Better in group
        HIGH_DENSITY,      // Many mobs per area
        LOW_DENSITY,       // Spread out mobs
        CASTERS_PRESENT,   // Magic-using mobs (dangerous)
        AGGRESSIVE_MOBS,   // Mobs aggro easily
        PASSIVE_MOBS,      // Mobs don't aggro
        GOOD_LOOT,         // Valuable drops
        POOR_LOOT,         // Junk drops
        NEAR_TOWN,         // Short run from town
        FAR_FROM_TOWN      // Long run, need SS stock
    }

    public static final class ZoneInfo {
        public final String name;
        public final int minLevel;
        public final int maxLevel;
        public final int avgMobLevel;
        public final int centerX;
        public final int centerY;
        public final int centerZ;
        public final int radius;          // Approximate farm radius
        public final String nearestTown;
        public final int townDistance;    // Rough distance to nearest town
        public final Set<ZoneTag> tags;
        public final List<Integer> questIds; // Quests tied to this zone
        public final double baseXpPerHour;   // Estimated XP/hour at avg level

        public ZoneInfo(String name, int minLevel, int maxLevel, int avgMobLevel,
                        int centerX, int centerY, int centerZ, int radius,
                        String nearestTown, int townDistance,
                        Set<ZoneTag> tags, List<Integer> questIds,
                        double baseXpPerHour) {
            this.name = name;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.avgMobLevel = avgMobLevel;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.radius = radius;
            this.nearestTown = nearestTown;
            this.townDistance = townDistance;
            this.tags = tags != null ? tags : Collections.emptySet();
            this.questIds = questIds != null ? questIds : Collections.emptyList();
            this.baseXpPerHour = baseXpPerHour;
        }

        public boolean isLevelAppropriate(int level) {
            return level >= minLevel && level <= maxLevel;
        }

        public boolean isClassFriendly(boolean isMage, boolean isRanged) {
            if (isMage && tags.contains(ZoneTag.MELEE_FRIENDLY) && !tags.contains(ZoneTag.MAGE_FRIENDLY)) {
                return false; // Tight quarters bad for mages
            }
            if (isRanged && tags.contains(ZoneTag.MELEE_FRIENDLY) && !tags.contains(ZoneTag.RANGED_FRIENDLY)) {
                return false;
            }
            return true;
        }
    }

    // Static zone database
    private static final List<ZoneInfo> ZONES = new ArrayList<>();

    static {
        registerZones();
    }

    private ZoneRecommender() {}

    // ================================================================
    // PUBLIC API
    // ================================================================

    /**
     * Get all zones appropriate for this player's level.
     */
    public static List<ZoneInfo> getZonesForLevel(int level) {
        List<ZoneInfo> result = new ArrayList<>();
        for (ZoneInfo z : ZONES) {
            if (z.isLevelAppropriate(level)) {
                result.add(z);
            }
        }
        return result;
    }

    /**
     * Recommend the best zone for this player.
     *
     * @param level        Player level
     * @param isMage       True if mage archetype
     * @param isRanged     True if archer/summoner
     * @param isSolo       True if playing solo
     * @param currentX     Current X coordinate
     * @param currentY     Current Y coordinate
     * @param activeQuests Active quest IDs (zones with these quests score higher)
     * @return Best zone, or null if none found
     */
    public static ZoneInfo recommendZone(int level, boolean isMage, boolean isRanged,
                                          boolean isSolo, int currentX, int currentY,
                                          Set<Integer> activeQuests) {
        List<ZoneInfo> candidates = getZonesForLevel(level);
        if (candidates.isEmpty()) return null;

        ZoneInfo best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (ZoneInfo zone : candidates) {
            double score = scoreZone(zone, level, isMage, isRanged, isSolo,
                                     currentX, currentY, activeQuests);
            if (score > bestScore) {
                bestScore = score;
                best = zone;
            }
        }
        return best;
    }

    /**
     * Recommend top N zones, sorted by score.
     */
    public static List<ZoneInfo> recommendTopZones(int level, boolean isMage, boolean isRanged,
                                                    boolean isSolo, int currentX, int currentY,
                                                    Set<Integer> activeQuests, int topN) {
        List<ZoneInfo> candidates = getZonesForLevel(level);
        List<ScoredZone> scored = new ArrayList<>();

        for (ZoneInfo zone : candidates) {
            double score = scoreZone(zone, level, isMage, isRanged, isSolo,
                                     currentX, currentY, activeQuests);
            scored.add(new ScoredZone(zone, score));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<ZoneInfo> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, scored.size()); i++) {
            result.add(scored.get(i).zone);
        }
        return result;
    }

    /**
     * Get zone by exact name.
     */
    public static ZoneInfo getZoneByName(String name) {
        for (ZoneInfo z : ZONES) {
            if (z.name.equalsIgnoreCase(name)) return z;
        }
        return null;
    }

    /**
     * Get zones that have active quests.
     */
    public static List<ZoneInfo> getQuestZones(Set<Integer> activeQuestIds) {
        List<ZoneInfo> result = new ArrayList<>();
        for (ZoneInfo z : ZONES) {
            for (int qid : z.questIds) {
                if (activeQuestIds.contains(qid)) {
                    result.add(z);
                    break;
                }
            }
        }
        return result;
    }

    // ================================================================
    // SCORING
    // ================================================================

    private static double scoreZone(ZoneInfo zone, int level, boolean isMage,
                                     boolean isRanged, boolean isSolo,
                                     int currentX, int currentY,
                                     Set<Integer> activeQuests) {
        double score = 0;

        // Level fit: peak at center of range, penalty for edges
        double levelCenter = (zone.minLevel + zone.maxLevel) / 2.0;
        double levelDist = Math.abs(level - levelCenter);
        double levelScore = Math.max(0, 100 - levelDist * 15);
        score += levelScore;

        // Class fit
        if (!zone.isClassFriendly(isMage, isRanged)) {
            score -= 50;
        } else if ((isMage && zone.tags.contains(ZoneTag.MAGE_FRIENDLY)) ||
                   (isRanged && zone.tags.contains(ZoneTag.RANGED_FRIENDLY))) {
            score += 20;
        }

        // Solo fit
        if (isSolo) {
            if (zone.tags.contains(ZoneTag.SOLO_VIABLE)) score += 30;
            if (zone.tags.contains(ZoneTag.PARTY_RECOMMENDED)) score -= 40;
            if (zone.tags.contains(ZoneTag.AGGRESSIVE_MOBS)) score -= 20;
            if (zone.tags.contains(ZoneTag.CASTERS_PRESENT)) score -= 15;
            if (zone.tags.contains(ZoneTag.PASSIVE_MOBS)) score += 15;
        } else {
            if (zone.tags.contains(ZoneTag.PARTY_RECOMMENDED)) score += 20;
        }

        // Quest overlap
        for (int qid : zone.questIds) {
            if (activeQuests != null && activeQuests.contains(qid)) {
                score += 40; // Strong incentive to farm where quests are
            }
        }

        // Distance penalty (closer is better)
        double dist = Math.hypot(zone.centerX - currentX, zone.centerY - currentY);
        double distPenalty = dist / 5000.0; // -1 per 5000 units
        score -= distPenalty;

        // Town proximity (for restocking)
        if (zone.townDistance < 30000) score += 15;
        else if (zone.townDistance > 100000) score -= 20;

        // XP potential
        score += zone.baseXpPerHour / 5000.0;

        // Loot quality
        if (zone.tags.contains(ZoneTag.GOOD_LOOT)) score += 10;
        if (zone.tags.contains(ZoneTag.POOR_LOOT)) score -= 10;

        // Density preference
        if (zone.tags.contains(ZoneTag.HIGH_DENSITY)) score += 15;

        return score;
    }

    private static final class ScoredZone {
        final ZoneInfo zone;
        final double score;
        ScoredZone(ZoneInfo zone, double score) {
            this.zone = zone;
            this.score = score;
        }
    }

    // ================================================================
    // ZONE DATABASE — Interlude
    // ================================================================

    private static void registerZones() {
        // Level 1-10: Starting areas
        ZONES.add(new ZoneInfo("Talking Island", 1, 10, 5,
            -99500, 237500, -3500, 15000,
            "Talking Island Village", 5000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.PASSIVE_MOBS, ZoneTag.NEAR_TOWN, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY),
            Arrays.asList(30001), 15000));

        ZONES.add(new ZoneInfo("Elven Forest", 5, 15, 10,
            10000, 50000, -3000, 20000,
            "Elven Village", 8000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.PASSIVE_MOBS, ZoneTag.NEAR_TOWN, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.RANGED_FRIENDLY),
            Arrays.asList(30002), 25000));

        ZONES.add(new ZoneInfo("Dark Elven Swampland", 8, 18, 13,
            20000, 10000, -3000, 18000,
            "Dark Elven Village", 6000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.NEAR_TOWN, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY),
            Arrays.asList(30003), 30000));

        // Level 10-20: Gludio area
        ZONES.add(new ZoneInfo("Gludio Plains", 10, 20, 15,
            -60000, 140000, -3000, 25000,
            "Gludin Village", 10000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.PASSIVE_MOBS, ZoneTag.NEAR_TOWN, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.RANGED_FRIENDLY),
            Arrays.asList(30001, 10001, 10002, 10003, 10004, 10005), 40000));

        ZONES.add(new ZoneInfo("Ruins of Agony", 18, 28, 23,
            -50000, 120000, -3000, 20000,
            "Gludin Village", 15000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Arrays.asList(30004, 40002), 60000));

        ZONES.add(new ZoneInfo("Ruins of Despair", 20, 30, 25,
            -45000, 115000, -3000, 18000,
            "Gludin Village", 12000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY),
            Collections.emptyList(), 55000));

        // Level 25-35: Abandoned Camp / Ant Nest
        ZONES.add(new ZoneInfo("Abandoned Camp", 25, 35, 30,
            -20000, 130000, -3000, 22000,
            "Gludin Village", 20000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Arrays.asList(30005, 40003), 80000));

        ZONES.add(new ZoneInfo("Ant Nest", 30, 40, 35,
            30000, 160000, -3000, 15000,
            "Dion Castle Town", 25000,
            tags(ZoneTag.PARTY_RECOMMENDED, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.CASTERS_PRESENT, ZoneTag.MELEE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Arrays.asList(30006), 100000));

        // Level 35-45: Execution Grounds / Cruma
        ZONES.add(new ZoneInfo("Execution Grounds", 35, 45, 40,
            50000, 150000, -3000, 25000,
            "Dion Castle Town", 20000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.CASTERS_PRESENT, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Arrays.asList(30007, 20001, 20002, 20003, 20004, 20005, 20006, 20007, 20008, 20009, 20010, 20011, 40004), 120000));

        ZONES.add(new ZoneInfo("Cruma Marshlands", 40, 50, 45,
            80000, 180000, -3000, 30000,
            "Dion Castle Town", 35000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.RANGED_FRIENDLY),
            Arrays.asList(30008), 140000));

        ZONES.add(new ZoneInfo("Cruma Tower", 40, 55, 48,
            85000, 185000, -3000, 10000,
            "Dion Castle Town", 40000,
            tags(ZoneTag.PARTY_RECOMMENDED, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.CASTERS_PRESENT, ZoneTag.MELEE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Collections.emptyList(), 180000));

        // Level 45-55: Dragon Valley / Forest of Outlaws
        ZONES.add(new ZoneInfo("Dragon Valley", 45, 55, 50,
            120000, 110000, -3000, 35000,
            "Giran Castle Town", 30000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.CASTERS_PRESENT, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.RANGED_FRIENDLY, ZoneTag.GOOD_LOOT),
            Arrays.asList(30009, 40005), 160000));

        ZONES.add(new ZoneInfo("Forest of Outlaws", 50, 60, 55,
            90000, 80000, -3000, 28000,
            "Giran Castle Town", 25000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Arrays.asList(30010), 150000));

        ZONES.add(new ZoneInfo("Devastated Castle", 50, 62, 56,
            95000, 75000, -3000, 20000,
            "Giran Castle Town", 28000,
            tags(ZoneTag.PARTY_RECOMMENDED, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.CASTERS_PRESENT, ZoneTag.MELEE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Collections.emptyList(), 170000));

        // Level 55-65: Timak Outpost / Wall of Argos
        ZONES.add(new ZoneInfo("Timak Outpost", 55, 65, 60,
            70000, 60000, -3000, 30000,
            "Oren Castle Town", 35000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Arrays.asList(30011), 180000));

        ZONES.add(new ZoneInfo("Wall of Argos", 60, 70, 65,
            150000, 50000, -3000, 35000,
            "Aden Castle Town", 40000,
            tags(ZoneTag.SOLO_VIABLE, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.CASTERS_PRESENT, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY),
            Arrays.asList(30012), 200000));

        ZONES.add(new ZoneInfo("Blazing Swamp", 60, 72, 66,
            140000, 40000, -3000, 25000,
            "Aden Castle Town", 35000,
            tags(ZoneTag.PARTY_RECOMMENDED, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.CASTERS_PRESENT, ZoneTag.MAGE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Collections.emptyList(), 220000));

        // Level 65-76: Varka / Ketra / Imperial Tomb / Monastery
        ZONES.add(new ZoneInfo("Varka Silenos", 65, 76, 70,
            100000, -100000, -3000, 40000,
            "Goddard Castle Town", 50000,
            tags(ZoneTag.PARTY_RECOMMENDED, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.CASTERS_PRESENT, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Arrays.asList(30013), 250000));

        ZONES.add(new ZoneInfo("Ketra Orc", 65, 76, 70,
            120000, -120000, -3000, 40000,
            "Goddard Castle Town", 55000,
            tags(ZoneTag.PARTY_RECOMMENDED, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.CASTERS_PRESENT, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Collections.emptyList(), 250000));

        ZONES.add(new ZoneInfo("Imperial Tomb", 70, 80, 74,
            50000, -50000, -3000, 35000,
            "Aden Castle Town", 45000,
            tags(ZoneTag.PARTY_RECOMMENDED, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.CASTERS_PRESENT, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Arrays.asList(30014), 280000));

        ZONES.add(new ZoneInfo("Monastery of Silence", 70, 80, 75,
            60000, -60000, -3000, 30000,
            "Aden Castle Town", 50000,
            tags(ZoneTag.PARTY_RECOMMENDED, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.CASTERS_PRESENT, ZoneTag.MELEE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Collections.emptyList(), 300000));

        ZONES.add(new ZoneInfo("Stakato Nest", 72, 80, 76,
            80000, -80000, -3000, 25000,
            "Goddard Castle Town", 60000,
            tags(ZoneTag.PARTY_RECOMMENDED, ZoneTag.AGGRESSIVE_MOBS, ZoneTag.CASTERS_PRESENT, ZoneTag.MELEE_FRIENDLY, ZoneTag.MAGE_FRIENDLY, ZoneTag.GOOD_LOOT),
            Collections.emptyList(), 320000));
    }

    private static Set<ZoneTag> tags(ZoneTag... tags) {
        return new HashSet<>(Arrays.asList(tags));
    }
}
