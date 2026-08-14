package com.aiplayer.phase0.play;

import java.util.List;

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

    private BotPlayController()
    {
    }

    /** Decide the single next action for a bot this tick. Never returns a NONE/idle decision. */
    public static GoalDecision decide(PlayContext ctx, BotPlayConfig cfg)
    {
        if (ctx == null)
        {
            return GoalDecision.wait(PlayerGoal.REST, "rest", "no context; hold");
        }
        BotPlayConfig c = cfg != null ? cfg : BotPlayConfig.DEFAULT;

        // 1. SURVIVE: too low on HP to keep fighting (hostiles present -> dangerous spot).
        //    Instead of standing still (old WAIT), retreat away from the nearest hostile so the
        //    bot disengages and its HP can regen.
        if (c.surviveHpFraction > 0 && ctx.hpMax > 0
                && (double) ctx.hpCurrent / ctx.hpMax <= c.surviveHpFraction)
        {
            Hostile danger = nearestHostile(ctx, c.sightRange);
            if (danger != null)
            {
                // Flee away from the nearest hostile: push through the player in the opposite
                // direction, then clamp to a single RETREAT_HOP (same as FleetPlay CHASE_HOP cap).
                int rx = ctx.x + (ctx.x - danger.x);
                int ry = ctx.y + (ctx.y - danger.y);
                Chase flee = chaseStep(ctx.x, ctx.y, ctx.z, rx, ry, ctx.z, RETREAT_HOP);
                return GoalDecision.retreat(PlayerGoal.SURVIVE, flee.x, flee.y, flee.z,
                    "retreat",
                    "hp " + fraction(ctx.hpCurrent, ctx.hpMax) + " too low; retreat from "
                        + danger.objId);
            }
        }

        // 1.5 RESTOCK: when inventory is too full to keep farming, return a deliberate REST
        //    instead of engaging COMBAT/HUNT so the bot stops fighting and (later) retreats.
        if (ctx.inventoryPct >= c.restockThreshold)
        {
            return GoalDecision.wait(PlayerGoal.REST, "restock",
                "inventory " + ctx.inventoryPct + "% full; restock/retreat");
        }

        // 2. COMBAT: a hostile we can hit right now.
        Hostile combat = nearestHostile(ctx, c.combatRange);
        if (combat != null)
        {
            return GoalDecision.combatTarget(PlayerGoal.FARM, combat.objId,
                "fight:" + combat.objId,
                "nearest hostile at " + combat.x + "," + combat.y);
        }

        // 3. HUNT: a hostile is visible but out of range -> walk toward it before questing on.
        Hostile seen = nearestHostile(ctx, c.sightRange);
        if (seen != null)
        {
            return GoalDecision.moveTo(PlayerGoal.FARM, seen.x, seen.y, seen.z,
                "hunt:" + seen.objId,
                "advance on hostile at " + seen.x + "," + seen.y);
        }

        // 4. QUEST: advance the active quest, or go acquire one when none is active. When the goal is
        //    an NPC step (QUEST/ACQUIRE) and we are already at that NPC, stop routing in place and
        //    open its dialog: emit BYPASS (STEP 2) so the fleet loop turns the empty bypassCommand into
        //    the single next validated bypass from the NPC's actual NpcHtmlMessage.
        GoalDecision quest = QuestGoalPlanner.decide(ctx.level, ctx.activeJournal,
            ctx.x, ctx.y, ctx.z, ctx.stepIndex);
        if (quest != null)
        {
            if (quest.action == GoalAction.MOVE_TO
                    && (quest.goal == PlayerGoal.QUEST || quest.goal == PlayerGoal.ACQUIRE)
                    && quest.questTargetId != 0
                    && within(quest.targetX, quest.targetY, quest.targetZ,
                        ctx.x, ctx.y, ctx.z, c.talkRange))
            {
                return GoalDecision.bypass(
                    quest.goal, "",
                    "quest-dialog:" + quest.questTargetId,
                    "at quest NPC " + quest.questTargetId + "; open dialog");
            }
            return quest;
        }

        // 5. REST: nothing acquirable/active anywhere -> deliberate hold, not idle-wander.
        return GoalDecision.wait(PlayerGoal.REST, "rest",
            "no quest and no target in sight; hold and re-check");
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
            new BotPlayConfig(0.25, 400, 2000, 300, 100);

        /** HP fraction at/below which a bot stops fighting while hostiles are near (SURVIVE). */
        public final double surviveHpFraction;
        /** Distance within which a hostile is considered a direct combat target. */
        public final int combatRange;
        /** Distance within which a hostile is worth walking to (hunt-advance). */
        public final int sightRange;
        /** STEP 2: distance within which being at a QUEST/ACQUIRE NPC means "open its dialog" (BYPASS). */
        public final int talkRange;
        /**
         * Inventory usage percentage threshold for restock intent (0..100). When a bot's inventory
         * usage percent >= restockThreshold and it would otherwise engage COMBAT or HUNT, the
         * controller returns REST-reason="restock" instead, so the bot stops farming and retreats.
         * Default 100 means disabled (never restock).
         */
        public final int restockThreshold;

        public BotPlayConfig(double surviveHpFraction, int combatRange, int sightRange)
        {
            this(surviveHpFraction, combatRange, sightRange, 300, 100);
        }

        public BotPlayConfig(double surviveHpFraction, int combatRange, int sightRange, int talkRange)
        {
            this(surviveHpFraction, combatRange, sightRange, talkRange, 100);
        }

        public BotPlayConfig(double surviveHpFraction, int combatRange, int sightRange, int talkRange,
                             int restockThreshold)
        {
            this.surviveHpFraction = surviveHpFraction;
            this.combatRange = combatRange;
            this.sightRange = sightRange;
            this.talkRange = talkRange;
            this.restockThreshold = Math.max(0, Math.min(100, restockThreshold));
        }
    }
}