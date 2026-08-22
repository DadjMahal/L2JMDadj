package com.aiplayer.behavior;

import java.util.List;

import com.aiplayer.knowledge.PlayerRace;
import com.aiplayer.core.BotSnapshot;
import com.aiplayer.examples.FleetPlay;
import com.aiplayer.protocol.PacketLogger;
import com.aiplayer.behavior.RestockPlanner;
import com.aiplayer.behavior.RestockPlanner.RestockPlan;

/**
 * MODE: COMPLETE. The decision ladder — picks ONE deliberate {@link GoalDecision} every tick so a
 *       bot is always doing something meaningful (fight / quest / travel / rest) and never idles.
 *
 * Pure logic: no IO, no packets, no threads. It reads a small real-data input (a {@link PlayContext}
 * built by the fleet loop straight from the live {@code PacketLogger} / {@code BotSnapshot}) and
 * returns an immutable {@link GoalDecision} the fleet loop executes through already-proven
 * primitives (combat frames, server-ack-gated hop travel).
 *
 * Priority ladder (top wins; the bot is never allowed to fall through to {@code NONE}):
 *  1. SURVIVE   — dangerously low HP while hostiles are near: stop and hold the beat.
 *  2. COMBAT    — a hostile is within combat range: attack the nearest one (FARM goal).
 *  3. HUNT      — a hostile is visible just beyond range: advance toward the nearest one.
 *  4. QUEST     — push the active quest: QuestGoalPlanner returns a QUEST/ACQUIRE move.
 *  5. REST      — nothing to do anywhere: a deliberate short hold (still a goal, never idle-wander).
 */
public final class BotPlayController
{
    /** Maximum distance of a single retreat hop (matching CHASE_HOP in FleetPlay,
     * well under the server's ~9900u single-move rejection). */
    private static final int RETREAT_HOP = 4800;
    /** FINAL-MILE: within this distance of a quest NPC the bot routes to it before fighting. */
    public static final int QUEST_PRIORITY_DIST = 5000;

    /**
     * EB-03: one rung of the decision ladder. The ladder order is no longer hardcoded — it is
     * configurable per profile via {@link BotPlayConfig#priority}. The default order below is
     * exactly the historical ladder (so DEFAULT behavior is unchanged). Profiles can reorder /
     * drop rungs (e.g. an ORC values COMBAT before QUEST; a merchant Dwarf values RESTOCK early);
     * SURVIVE always evaluates first regardless of order (it is the hard safety rung).
     */
    public enum Rung
    {
        /** Dangerously low HP with hostiles near: retreat / stop fighting. */
        SURVIVE,
        /** Standing on the quest NPC: open its dialog (BYPASS) rather than fight. */
        QUEST_TALK,
        /** Inventory too full to farm: walk to the town vendor to restock. */
        RESTOCK,
        /** Hostile in melee range: attack the nearest one (FARM). */
        COMBAT,
        /** Hostile just out of range: advance toward it (HUNT). */
        HUNT,
        /** Advance the active quest / go acquire one (QUEST). */
        QUEST
    }

    /** The historical (default) ladder order. */
    public static final java.util.List<Rung> DEFAULT_LADDER = java.util.Collections.unmodifiableList(
        java.util.Arrays.asList(Rung.SURVIVE, Rung.QUEST_TALK, Rung.RESTOCK, Rung.COMBAT, Rung.HUNT, Rung.QUEST));

    private BotPlayController()
    {
    }

    /** S6-T08: range multiplier by level — L1-3 use 0.6x (weak starter gear), L4+ the full base. */
    public static double levelRangeScale(int level)
    {
        return level <= 3 ? 0.6 : 1.0;
    }

    /** Decide the single next action for a bot this tick. Never returns a NONE/idle decision. */
    public static GoalDecision decide(PlayContext ctx, BotPlayConfig cfg)
    {
        if (ctx == null)
        {
            return GoalDecision.wait(PlayerGoal.REST, "rest", "no context; hold");
        }
        BotPlayConfig c = cfg != null ? cfg : BotPlayConfig.DEFAULT;
        // S6-T08: fresh (L1-3) bots have weak starter gear -> engage closer; from L4+ use the base ranges.
        double rangeScale = levelRangeScale(ctx.level);
        int combatRange = (int) Math.round(c.combatRange * rangeScale);
        int sightRange = (int) Math.round(c.sightRange * rangeScale);

        // EB-03: SURVIVE is the hard safety rung — always evaluated first regardless of the profile's
        // ordered list (a profile that drops SURVIVE entirely disables the escape hatch).
        if (c.priority.contains(Rung.SURVIVE))
        {
            GoalDecision survive = rungSurvive(ctx, c, sightRange);
            if (survive != null)
            {
                return survive;
            }
        }
        for (Rung rung : c.priority)
        {
            if (rung == Rung.SURVIVE)
            {
                continue; // already evaluated first (safety stays first regardless of order)
            }
            GoalDecision d = evaluateRung(rung, ctx, c, combatRange, sightRange);
            if (d != null)
            {
                return d;
            }
        }
        // 5. REST: nothing acquirable / active anywhere -> deliberate hold, never idle-wander.
        return GoalDecision.wait(PlayerGoal.REST, "rest",
            "no quest and no target in range; hold and re-check");
    }

    /** EB-03: dispatch one ladder rung to its decision; null = rung declines this tick. */
    private static GoalDecision evaluateRung(Rung rung, PlayContext ctx, BotPlayConfig c,
                                             int combatRange, int sightRange)
    {
        switch (rung)
        {
            case SURVIVE:
                return rungSurvive(ctx, c, sightRange);
            case QUEST_TALK:
                return rungQuestTalk(ctx, c);
            case RESTOCK:
                return rungRestock(ctx, c);
            case COMBAT:
                return rungCombat(ctx, combatRange);
            case HUNT:
                return rungHunt(ctx, sightRange);
            case QUEST:
                return rungQuest(ctx, c);
            default:
                return null;
        }
    }

    // ================================================================
    // LADDER RUNGS (EB-03) — one pure decision method per rung; each returns
    // null when that rung declines this tick, so decide() falls through the ladder.
    // ================================================================

    /** SURVIVE: too low on HP with hostiles near -> retreat away from the nearest hostile. */
    private static GoalDecision rungSurvive(PlayContext ctx, BotPlayConfig c, int sightRange)
    {
        if (c.surviveHpFraction <= 0 || ctx.hpMax <= 0
                || (double) ctx.hpCurrent / ctx.hpMax > c.surviveHpFraction)
        {
            return null;
        }
        Hostile danger = nearestHostile(ctx, sightRange);
        if (danger == null)
        {
            return null;
        }
        // Flee away from the nearest hostile: push through the player in the opposite direction,
        // then clamp to a single RETREAT_HOP (same as FleetPlay CHASE_HOP cap).
        int rx = ctx.x + (ctx.x - danger.x);
        int ry = ctx.y + (ctx.y - danger.y);
        Chase flee = chaseStep(ctx.x, ctx.y, ctx.z, rx, ry, ctx.z, RETREAT_HOP);
        return GoalDecision.retreat(PlayerGoal.SURVIVE, flee.x, flee.y, flee.z,
            "retreat",
            "hp " + fraction(ctx.hpCurrent, ctx.hpMax) + " too low; retreat from " + danger.objId);
    }

    /** QUEST_TALK: standing on the quest NPC (≤ talkRange) -> open dialog (BYPASS); near it -> route. */
    private static GoalDecision rungQuestTalk(PlayContext ctx, BotPlayConfig c)
    {
        GoalDecision earlyQuest = QuestGoalPlanner.decide(ctx.level, ctx.activeJournal,
            ctx.x, ctx.y, ctx.z, ctx.stepIndex, c.varietySeed);
        if (earlyQuest != null && earlyQuest.action == GoalAction.MOVE_TO
                && (earlyQuest.goal == PlayerGoal.QUEST || earlyQuest.goal == PlayerGoal.ACQUIRE)
                && earlyQuest.questTargetId != 0)
        {
            double toNpc = Math.hypot(ctx.x - (double) earlyQuest.targetX,
                ctx.y - (double) earlyQuest.targetY);
            if (toNpc <= c.talkRange)
            {
                return GoalDecision.bypass(earlyQuest.goal, "",
                    "quest-dialog:" + earlyQuest.questTargetId,
                    "at quest NPC " + earlyQuest.questTargetId + "; open dialog");
            }
            if (toNpc <= QUEST_PRIORITY_DIST)
            {
                // FINAL-MILE: route to the giver before engaging combat.
                return earlyQuest;
            }
        }
        return null;
    }

    /** RESTOCK: inventory too full to farm -> walk to the town vendor. */
    private static GoalDecision rungRestock(PlayContext ctx, BotPlayConfig c)
    {
        if (ctx.inventoryPct < c.restockThreshold)
        {
            return null;
        }
        RestockPlanner.RestockPlan plan =
            RestockPlanner.plan(ctx.level, ctx.inventoryPct, 0, c.race);
        return GoalDecision.moveTo(PlayerGoal.REST, plan.vendorX, plan.vendorY, plan.vendorZ,
            "restock",
            "inventory " + ctx.inventoryPct + "% full; walk to vendor to restock");
    }

    /** COMBAT: a hostile we can hit right now -> attack the nearest one. */
    private static GoalDecision rungCombat(PlayContext ctx, int combatRange)
    {
        Hostile combat = nearestHostile(ctx, combatRange);
        if (combat == null)
        {
            return null;
        }
        return GoalDecision.combatTarget(PlayerGoal.FARM, combat.objId,
            "fight:" + combat.objId,
            "nearest hostile at " + combat.x + "," + combat.y);
    }

    /** HUNT: hostile visible but out of range -> advance toward it. */
    private static GoalDecision rungHunt(PlayContext ctx, int sightRange)
    {
        Hostile seen = nearestHostile(ctx, sightRange);
        if (seen == null)
        {
            return null;
        }
        return GoalDecision.moveTo(PlayerGoal.FARM, seen.x, seen.y, seen.z,
            "hunt:" + seen.objId,
            "advance on hostile at " + seen.x + "," + seen.y);
    }

    /** QUEST: advance the active quest / acquire one; at the NPC -> open dialog (BYPASS). */
    private static GoalDecision rungQuest(PlayContext ctx, BotPlayConfig c)
    {
        GoalDecision quest = QuestGoalPlanner.decide(ctx.level, ctx.activeJournal,
            ctx.x, ctx.y, ctx.z, ctx.stepIndex, c.varietySeed);
        if (quest == null)
        {
            return null;
        }
        if (quest.action == GoalAction.MOVE_TO
                && (quest.goal == PlayerGoal.QUEST || quest.goal == PlayerGoal.ACQUIRE)
                && quest.questTargetId != 0
                && within(quest.targetX, quest.targetY, quest.targetZ,
                    ctx.x, ctx.y, ctx.z, c.talkRange))
        {
            return GoalDecision.bypass(quest.goal, "",
                "quest-dialog:" + quest.questTargetId,
                "at quest NPC " + quest.questTargetId + "; open dialog");
        }
        return quest;
    }

    /** Convenience with default config. */
    public static GoalDecision decide(PlayContext ctx)
    {
        return decide(ctx, BotPlayConfig.DEFAULT);
    }

    // ================================================================
    // INTERNAL
    // ================================================================

    /** Nearest hostile within {@code range} of the bot, or null. */
    private static Hostile nearestHostile(PlayContext ctx, int range)
    {
        if (ctx.hostiles == null || ctx.hostiles.isEmpty())
        {
            return null;
        }
        Hostile best = null;
        long bestSq = (long) range * range;
        for (Hostile h : ctx.hostiles)
        {
            if (h == null)
            {
                continue;
            }
            long dx = h.x - ctx.x;
            long dy = h.y - ctx.y;
            long dz = h.z - ctx.z;
            long distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= bestSq)
            {
                bestSq = distSq;
                best = h;
            }
        }
        return best;
    }

    /** True when the point (tx,ty,tz) is within {@code range} of the bot (squared distance compare). */
    private static boolean within(int tx, int ty, int tz, int x, int y, int z, int range)
    {
        long dx = (long) tx - x;
        long dy = (long) ty - y;
        long dz = (long) tz - z;
        long limit = (long) range * range;
        return dx * dx + dy * dy + dz * dz <= limit;
    }

    private static String fraction(int current, int max)
    {
        if (max <= 0)
        {
            return "?";
        }
        return (int) ((current * 100.0) / max) + "%";
    }

    /**
     * STEP 3 gap-close: one chase hop from (sx,sy,sz) toward (tx,ty,tz), clamped to {@code maxHop}
     * units so it stays under the server's single-move cap (MoveToLocation rejects hops &gt; ~9900u; the
     * fleet routes in &lt;=4800u steps). Returns the exact destination to {@code MoveToLocation}; pure
     * math, no IO. Lets a bot that just ran out of a corridor close the distance to an out-of-melee
     * hostile instead of standing still and spamming an unreachable target (the STEP 3 live-run stall).
     */
    public static Chase chaseStep(int sx, int sy, int sz, int tx, int ty, int tz, int maxHop)
    {
        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;
        double dist = java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist <= maxHop)
        {
            return new Chase(tx, ty, tz);
        }
        double k = maxHop / dist;
        return new Chase(sx + (int) java.lang.Math.round(k * dx),
                         sy + (int) java.lang.Math.round(k * dy),
                         sz + (int) java.lang.Math.round(k * dz));
    }

    /** Immutable chase destination (STEP 3 gap-close). */
    public static final class Chase
    {
        public final int x, y, z;

        public Chase(int x, int y, int z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    // ================================================================
    // PURE INPUT / CONFIG VALUE TYPES (no PacketLogger dependency)
    // ================================================================

    /** One nearby hostile target: object id + position. */
    public static final class Hostile
    {
        public final int objId;
        public final int x;
        public final int y;
        public final int z;

        public Hostile(int objId, int x, int y, int z)
        {
            this.objId = objId;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** Immutable per-tick input, built from the live BotSnapshot / PacketLogger by the fleet loop. */
    public static final class PlayContext
    {
        public final int level;
        public final int x;
        public final int y;
        public final int z;
        public final int hpCurrent;
        public final int hpMax;
        /** Active quest journal as {questId, state} pairs (PacketLogger.getActiveQuestList). */
        public final List<int[]> activeJournal;
        /** Nearby hostiles (may be empty); survives null to keep the controller total. */
        public final List<Hostile> hostiles;
        /** 0-based quest step to play; defaults to 0 (fresh -> TALK to the giver). */
        public final int stepIndex;
        /** Inventory usage percentage 0..100 (from PacketLogger.getInventoryUsagePercent). */
        public final int inventoryPct;

        public PlayContext(int level, int x, int y, int z, int hpCurrent, int hpMax,
                           List<int[]> activeJournal, List<Hostile> hostiles, int stepIndex,
                           int inventoryPct)
        {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.hpCurrent = hpCurrent;
            this.hpMax = hpMax;
            this.activeJournal = activeJournal;
            this.hostiles = hostiles != null ? hostiles : java.util.Collections.emptyList();
            this.stepIndex = stepIndex;
            this.inventoryPct = Math.max(0, Math.min(100, inventoryPct));
        }
    }

    /** Tuning knobs for the ladder; DEFAULT is the live run's behaviour. */
    public static final class BotPlayConfig
    {
        public static final BotPlayConfig DEFAULT =
            new BotPlayConfig(0.25, 400, 2000, 300, 100, PlayerRace.HUMAN);

        /** HP fraction at/below which a bot stops fighting while hostiles are near (SURVIVE). */
        public final double surviveHpFraction;
        /** Distance within which a hostile is considered a direct combat target. */
        public final int combatRange;
        /** Distance within which a hostile is worth walking to (hunt-advance). */
        public final int sightRange;
        /** NPC dialog open distance for BYPASS (STEP 2). */
        public final int talkRange;
        /**
         * Inventory usage percentage threshold for restock intent (0..100). When a bot's inventory
         * usage percent >= restockThreshold and it would otherwise engage COMBAT or HUNT, the
         * controller returns REST-reason="restock" instead, so the bot stops farming and walks to
         * the town vendor to shop. Default 100 means disabled (never restock).
         */
        public final int restockThreshold;
        /** Race of the bot, used to pick the vendor landmark for restock trips (default HUMAN). */
        public final PlayerRace race;
        /** S3-T07: per-bot quest-acquire variety seed (0 = classic nearest pick). */
        public final int varietySeed;
        /** EB-03: the decision ladder order (default = historical). Profiles reorder/drop these. */
        public final java.util.List<Rung> priority;

        public BotPlayConfig(double surviveHpFraction, int combatRange, int sightRange)
        {
            this(surviveHpFraction, combatRange, sightRange, 300, 100,
                PlayerRace.HUMAN, 0);
        }

        public BotPlayConfig(double surviveHpFraction, int combatRange, int sightRange, int talkRange)
        {
            this(surviveHpFraction, combatRange, sightRange, talkRange, 100,
                PlayerRace.HUMAN, 0);
        }

        public BotPlayConfig(double surviveHpFraction, int combatRange, int sightRange, int talkRange,
                             int restockThreshold)
        {
            this(surviveHpFraction, combatRange, sightRange, talkRange, restockThreshold,
                PlayerRace.HUMAN, 0);
        }

        public BotPlayConfig(double surviveHpFraction, int combatRange, int sightRange, int talkRange,
                             int restockThreshold, PlayerRace race)
        {
            this(surviveHpFraction, combatRange, sightRange, talkRange, restockThreshold,
                race, 0);
        }

        public BotPlayConfig(double surviveHpFraction, int combatRange, int sightRange, int talkRange,
                             int restockThreshold, PlayerRace race, int varietySeed)
        {
            this(surviveHpFraction, combatRange, sightRange, talkRange, restockThreshold,
                race, varietySeed, DEFAULT_LADDER);
        }

        /** Full constructor — the ladder order is the last knob so old call sites stay source-compatible. */
        public BotPlayConfig(double surviveHpFraction, int combatRange, int sightRange, int talkRange,
                             int restockThreshold, PlayerRace race, int varietySeed,
                             java.util.List<Rung> priority)
        {
            this.surviveHpFraction = surviveHpFraction;
            this.combatRange = combatRange;
            this.sightRange = sightRange;
            this.talkRange = talkRange;
            this.restockThreshold = Math.max(0, Math.min(100, restockThreshold));
            this.race = race != null ? race : PlayerRace.HUMAN;
            this.varietySeed = varietySeed;
            this.priority = priority != null && !priority.isEmpty()
                ? java.util.Collections.unmodifiableList(new java.util.ArrayList<>(priority))
                : DEFAULT_LADDER;
        }

        /** EB-03: profile copy — same knobs, different ladder order (or dropped rungs). */
        public BotPlayConfig withLadder(java.util.List<Rung> ladder)
        {
            return new BotPlayConfig(surviveHpFraction, combatRange, sightRange, talkRange,
                restockThreshold, race, varietySeed, ladder);
        }

        /**
         * EB-03: a race-flavored ladder — per-profile priorities instead of one hardcoded order.
         * Every race keeps SURVIVE (the safety rung); the interesting knob is the rest:
         *  - HUMAN: historical order (balanced quest-first flavor).
         *  - ORC:   aggressive — COMBAT/HUNT above questing (melee-first).
         *  - DWARF: merchant — RESTOCK early (inventory is money), combat before quest dialogs.
         *  - ELF/DARK_ELF: spellcasters — quest-talk first (dialogs = class-defining), then combat.
         * Null race falls back to the historical DEFAULT_LADDER.
         */
        public static java.util.List<Rung> ladderForRace(PlayerRace race)
        {
            if (race == null)
            {
                return DEFAULT_LADDER;
            }
            switch (race)
            {
                case ORC:
                    return java.util.Collections.unmodifiableList(java.util.Arrays.asList(
                        Rung.SURVIVE, Rung.COMBAT, Rung.HUNT, Rung.RESTOCK, Rung.QUEST_TALK, Rung.QUEST));
                case DARK_ELF:
                case ELF:
                    return java.util.Collections.unmodifiableList(java.util.Arrays.asList(
                        Rung.SURVIVE, Rung.QUEST_TALK, Rung.QUEST, Rung.COMBAT, Rung.HUNT, Rung.RESTOCK));
                case DWARF:
                    return java.util.Collections.unmodifiableList(java.util.Arrays.asList(
                        Rung.SURVIVE, Rung.RESTOCK, Rung.COMBAT, Rung.HUNT, Rung.QUEST_TALK, Rung.QUEST));
                default:
                    return DEFAULT_LADDER;
            }
        }

        /**
         * EB-04: build a config whose DECISION ranges are derived from a bot's personality
         * (PersonalityBehavior). Keeps every other knob at the caller's base values — this is the
         * seam that makes PersonalityProfile actually drive risk (survive fraction), pace (engaged
         * ranges) and restock timing instead of being decorative.
         */
        public BotPlayConfig withPersonality(com.aiplayer.behavior.PersonalityBehavior.Knobs knobs)
        {
            if (knobs == null)
            {
                return this;
            }
            int combat = (int) Math.round(combatRange * knobs.combatRangeScale);
            int sight = (int) Math.round(sightRange * knobs.sightRangeScale);
            int restock = knobs.restockThreshold;
            return new BotPlayConfig(knobs.surviveHpFraction, combat, sight, talkRange,
                restock, race, varietySeed, priority);
        }
    }
}
