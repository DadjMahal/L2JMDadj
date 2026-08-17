package com.aiplayer.phase0.quest;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */


import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates quest efficiency and compares questing vs pure grinding.
 * Produces a numeric score that LevelingPlanner uses to decide
 * whether to quest, grind, or mix both in a session.
 *
 * Scoring factors:
 * - XP/min vs grinding baseline at that level
 * - Adena reward value
 * - Item reward utility (Phase 1: placeholder)
 * - Travel overhead (distance to zone, quest chain length)
 * - Death risk (zone danger, player gear)
 * - Quest progress (partially completed quests get completion bonus)
 */
public final class QuestRewardEvaluator {

    // Baseline XP/min for pure grinding by level (empirical Interlude estimates)
    private static final Map<Integer, Double> GRIND_XP_PER_MIN = new HashMap<>();
    static {
        GRIND_XP_PER_MIN.put(1, 50.0);    GRIND_XP_PER_MIN.put(10, 400.0);
        GRIND_XP_PER_MIN.put(20, 1500.0); GRIND_XP_PER_MIN.put(30, 3500.0);
        GRIND_XP_PER_MIN.put(40, 7000.0); GRIND_XP_PER_MIN.put(50, 12000.0);
        GRIND_XP_PER_MIN.put(60, 20000.0);GRIND_XP_PER_MIN.put(70, 35000.0);
        GRIND_XP_PER_MIN.put(76, 50000.0);
    }

    private QuestRewardEvaluator() {}

    /**
     * Score a quest for this player context.
     * Returns value > 1.0 => quest is better than grinding.
     * Returns value < 0.5 => skip this quest.
     */
    public static double scoreQuest(QuestInfo quest, int level, int currentX, int currentY,
                                     boolean hasActiveQuestInSameZone,
                                     double gearScore, // 0.0-1.0 rough gear quality
                                     boolean isSolo) {
        if (quest == null) return 0;

        double score = 0.0;

        // 1. XP efficiency
        double grindBaseline = getGrindXpPerMin(level);
        double questXpPerMin = quest.xpPerMinute();
        double xpRatio = questXpPerMin / Math.max(1.0, grindBaseline);
        score += xpRatio * 50.0; // Up to 50 points for matching grind speed, 100 for double

        // 2. Adena value (convert to XP-equivalent rough value)
        double adenaValue = quest.reward.adena / 100.0; // 1 adena ~ 0.01 XP equivalent for scoring
        score += adenaValue / Math.max(1.0, quest.estimatedMinutes);

        // 3. Travel penalty
        double travelPenalty = estimateTravelPenalty(quest, currentX, currentY);
        score -= travelPenalty;

        // 4. Zone synergy bonus (already farming there = no travel cost)
        if (hasActiveQuestInSameZone) {
            score += 25.0; // Strong incentive to stack quests
        }

        // 5. Class-change mandatory bonus
        if (quest.type == QuestInfo.QuestType.CLASS_CHANGE) {
            score += 200.0; // Always prioritize class changes
        }

        // 6. Risk penalty (overlevel = safer, underlevel = riskier)
        int levelDiff = level - quest.recommendedLevel;
        if (levelDiff < -3) {
            score -= (-levelDiff) * 15.0; // Underleveled = dangerous
        } else if (levelDiff > 5) {
            score -= (levelDiff - 5) * 5.0; // Overleveled = inefficient
        }

        // 7. Gear risk modifier
        if (gearScore < 0.3 && quest.minLevel >= 40) {
            score -= 20.0; // Poor gear in high zones = risky
        }

        // 8. Solo penalty for party-recommended zones
        if (isSolo && quest.type == QuestInfo.QuestType.COMBAT_ABILITY) {
            score -= 15.0;
        }

        // 9. Completion momentum (partial progress gives small bonus)
        // Applied externally via QuestProgressTracker state

        return Math.max(0, score);
    }

    /**
     * Compare quest vs grind and return recommendation.
     */
    public enum ActivityType { QUEST, GRIND, MIXED }

    public static ActivityType recommendActivity(List<QuestInfo> availableQuests,
                                                   int level, int currentX, int currentY,
                                                   boolean hasSameZoneQuest,
                                                   double gearScore, boolean isSolo) {
        double bestQuestScore = 0;
        for (QuestInfo q : availableQuests) {
            double s = scoreQuest(q, level, currentX, currentY, hasSameZoneQuest, gearScore, isSolo);
            if (s > bestQuestScore) bestQuestScore = s;
        }

        if (bestQuestScore >= 80.0) return ActivityType.QUEST;
        if (bestQuestScore >= 40.0) return ActivityType.MIXED;
        return ActivityType.GRIND;
    }

    /**
     * Estimate travel penalty based on distance to quest start NPC / zone.
     */
    private static double estimateTravelPenalty(QuestInfo quest, int currentX, int currentY) {
        if (quest.steps.isEmpty()) return 0;
        QuestInfo.QuestStep firstStep = quest.steps.get(0);
        double dist = Math.hypot(firstStep.zoneX - currentX, firstStep.zoneY - currentY);
        // Penalty: -1 per 10k units, cap at -30
        return Math.min(30.0, dist / 10000.0);
    }

    private static double getGrindXpPerMin(int level) {
        // Interpolate between known points
        int lower = 1;
        int upper = 76;
        double lowerXp = GRIND_XP_PER_MIN.get(1);
        double upperXp = GRIND_XP_PER_MIN.get(76);

        for (Map.Entry<Integer, Double> e : GRIND_XP_PER_MIN.entrySet()) {
            if (e.getKey() <= level && e.getKey() > lower) {
                lower = e.getKey();
                lowerXp = e.getValue();
            }
            if (e.getKey() >= level && e.getKey() < upper) {
                upper = e.getKey();
                upperXp = e.getValue();
            }
        }

        if (lower == upper) return lowerXp;
        double t = (level - lower) / (double) (upper - lower);
        return lowerXp + t * (upperXp - lowerXp);
    }
}
