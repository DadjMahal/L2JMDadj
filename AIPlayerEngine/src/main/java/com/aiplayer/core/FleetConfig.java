package com.aiplayer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.aiplayer.knowledge.PlayerRace;

/**
 * EP-4 extraction: everything the FleetPlay launcher parses/owns — CLI args, env-style runtime
 * overrides, and the live-loop tuning knobs read from {@link AIConfiguration} (defaults = the
 * proven live values). Parsing semantics are byte-identical to the pre-split FleetPlay.main:
 * positional args 1..9 = count host gamePort loginPort dashPort [movement] prefix charIdBase races.
 * The session machine ({@link BotSession}) statically imports the tuning knobs, so they read the
 * same here as they did as FleetPlay statics.
 */
public final class FleetConfig
{
    // ---- parsed launcher args ----
    public final int count;
    public final String host;
    public final int gamePort;
    public final int loginPort;
    public final int dashPort;
    /** TIM-001 proof hook: optional 6th arg "movement" force-enables engine.movement at runtime. */
    public final boolean forceMovement;
    public final String accountPrefix;
    public final int charIdBase;
    /** Empty array = all HUMAN (preserves the original all-Human-Fighter behaviour). */
    public final PlayerRace[] raceRotation;

    private FleetConfig(int count, String host, int gamePort, int loginPort, int dashPort,
                        boolean forceMovement, String accountPrefix, int charIdBase,
                        PlayerRace[] raceRotation)
    {
        this.count = count;
        this.host = host;
        this.gamePort = gamePort;
        this.loginPort = loginPort;
        this.dashPort = dashPort;
        this.forceMovement = forceMovement;
        this.accountPrefix = accountPrefix;
        this.charIdBase = charIdBase;
        this.raceRotation = raceRotation;
    }

    /** Parse the launcher args exactly as the pre-split FleetPlay.main did (same defaults, same prints). */
    public static FleetConfig parse(String[] args)
    {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        int gamePort = args.length > 2 ? Integer.parseInt(args[2]) : 7777;
        int loginPort = args.length > 3 ? Integer.parseInt(args[3]) : 2106;
        int dashPort = args.length > 4 ? Integer.parseInt(args[4]) : 8080;
        boolean forceMovement = args.length > 5 && "movement".equalsIgnoreCase(args[5]);
        // Optional 7th/8th args: account prefix + charId base, so an operator can point the launch at a
        // SPECIFIC (e.g. brand-new) account rather than the default ai_combat_01..05 pool. Defaults keep
        // the original behaviour (ai_combat_%02d / charId 100000+).
        String accountPrefix = args.length > 6 ? args[6] : "ai_combat_";
        int charIdBase = args.length > 7 ? Integer.parseInt(args[7]) : 100000;
        // Optional 9th arg: race distribution. "random" -> each bot gets a uniformly random race;
        // a comma list like "ELF,ORC,DWARF" rotates across those races; absent/empty -> all HUMAN.
        PlayerRace[] raceRotation = resolveRaces(args.length > 8 ? args[8] : "");
        if (args.length > 8)
        {
            System.out.println("[FleetPlay] race mode: "
                + ("random".equalsIgnoreCase(args[8]) ? "random per bot" : args[8]));
        }
        return new FleetConfig(count, host, gamePort, loginPort, dashPort, forceMovement,
            accountPrefix, charIdBase, raceRotation);
    }

    /** TIM-001: "movement" forces engine.enabled + engine.movement ON for this run only
     *  (never edits config/ai-player.properties; the default remains OFF). */
    public void applyRuntimeOverrides()
    {
        if (forceMovement)
        {
            AIConfiguration cfg = AIConfiguration.getInstance();
            cfg.setProperty("engine.enabled", "true");
            cfg.setProperty("engine.movement", "true");
            System.out.println("[FleetPlay] engine.movement FORCED ON for this run (6th arg 'movement')");
        }
    }

    /**
     * Parse the 9th launcher arg into a race rotation. "random" -> the 5 races shuffled (balanced
     * 10-each over a 50-bot fleet); a comma list like "ELF,ORC,DWARF" rotates across those races;
     * absent/empty -> empty array (callers fall back to all HUMAN, preserving old behaviour).
     */
    private static PlayerRace[] resolveRaces(String spec)
    {
        if (spec == null || spec.trim().isEmpty())
        {
            return new PlayerRace[0];
        }
        if ("random".equalsIgnoreCase(spec.trim()))
        {
            List<PlayerRace> l = new ArrayList<>();
            for (PlayerRace r : PlayerRace.values())
            {
                l.add(r);
            }
            Collections.shuffle(l, DeterministicRandom.forFleet("race-rotation"));
            return l.toArray(new PlayerRace[0]);
        }
        List<PlayerRace> out = new ArrayList<>();
        for (String p : spec.split(","))
        {
            try
            {
                out.add(PlayerRace.valueOf(p.trim().toUpperCase()));
            }
            catch (IllegalArgumentException ignore)
            {
                // unknown race token -> skip
            }
        }
        return out.toArray(new PlayerRace[0]);
    }

    // ===================== live-loop tuning knobs (moved from FleetPlay, EP-4) =====================
    // S1-T08: read from AIConfiguration at class-init; defaults = the proven live values.

    /**
     * S1/EP-6: bot account password — NEVER hardcoded. Resolution order: launcher arg (callers),
     * then config key ai.account.password, then env AI_ACCOUNT_PASSWORD; missing everywhere =
     * fail fast with setup instructions (no hardcoded default).
     */
    public static String accountPassword()
    {
        AIConfiguration cfg = AIConfiguration.getInstance();
        String pw = cfg.getProperty("ai.account.password", System.getenv("AI_ACCOUNT_PASSWORD"));
        if (pw == null || pw.trim().isEmpty())
        {
            throw new IllegalStateException("ai.account.password not set: put it in "
                + "AIPlayerEngine/src/main/resources/config/ai-player.properties, export "
                + "AI_ACCOUNT_PASSWORD, or pass scripts/fleet_env.local (see "
                + "scripts/fleet_env.local.example) — refusing to run with a hardcoded default");
        }
        return pw;
    }

    static final long TICK_MS = AIConfiguration.getInstance().getLongProperty("bot.tickMs", 300);
    static final long WANDER_INTERVAL_MS =
        AIConfiguration.getInstance().getLongProperty("bot.wanderIntervalMs", 8000);
    static final int WANDER_RADIUS =
        AIConfiguration.getInstance().getIntProperty("bot.wanderRadius", 900);
    // STEP 3 gap-close: a combat target closer than this is "in melee reach" (attack normally); farther
    // and the bot advances toward it once per CHASE_INTERVAL_MS. CHASE_HOP is capped well under the server's
    // ~9900u single-move rejection (MoveToLocation.java:156-163) so each chase move persists server-side.
    static final int CHASE_REACH =
        AIConfiguration.getInstance().getIntProperty("bot.chaseReach", 150);
    static final int CHASE_HOP =
        AIConfiguration.getInstance().getIntProperty("bot.chaseHop", 4800);
    static final long CHASE_INTERVAL_MS =
        AIConfiguration.getInstance().getLongProperty("bot.chaseIntervalMs", 1500);
    // STEP 3 follow-up: if a combat target produces NO XP within this budget, the engagement is
    // force-abandoned so the bot re-acquires a farmable target instead of chasing an un-killable
    // or stale one (town NPCs, despawned mobs). 15s ≈ 10 chase-hop intervals.
    static final long STALE_TARGET_BUDGET_MS =
        AIConfiguration.getInstance().getLongProperty("bot.staleTargetBudgetMs", 15_000);

    /** S6-T02: low-level killers are slow, so give fresh bots a longer no-XP budget (30s vs 15s). */
    static long staleBudgetMs(int level)
    {
        long normal = STALE_TARGET_BUDGET_MS;
        long low = AIConfiguration.getInstance().getLongProperty("bot.staleTargetBudgetLowLevelMs", normal * 2);
        return level < 6 ? low : normal;
    }

    /** S6-T03/T07: current HP fraction (1.0 when unknown) used by the survival guards. */
    static double hpFrac(BotSnapshot s)
    {
        return s.hpMax > 0 ? (double) s.hpCurrent / s.hpMax : 1.0;
    }

    /** S5-T07: per-race relocation radius factor (Elves/Dwarf/Orc wander less than Humans). */
    static double raceRadiusFactor(PlayerRace race)
    {
        if (race == null)
        {
            return 1.0;
        }
        switch (race)
        {
            case ELF:
            case DARK_ELF:
                return 0.7;
            case DWARF:
            case ORC:
                return 0.8;
            default:
                return 1.0;
        }
    }

    // S6-T03/T06/T07/T10: survival guards — post-retreat regen hold, overwhelm back-off, death-loop guard.
    static final long REGEN_HOLD_MS =
        AIConfiguration.getInstance().getLongProperty("bot.regenHoldMs", 8000);
    static final int SURROUND_CAP =
        AIConfiguration.getInstance().getIntProperty("bot.surroundCap", 6);
    static final int DEATH_GUARD_DEATHS =
        AIConfiguration.getInstance().getIntProperty("bot.deathGuardDeaths", 3);
    static final long DEATH_GUARD_MS =
        AIConfiguration.getInstance().getLongProperty("bot.deathGuardMs", 90_000);
    // S2-T07: reconnect backoff — 5s base, doubling to 120s max, +jitter, reset on a clean enter-world.
    static final long RECONNECT_BASE_MS =
        AIConfiguration.getInstance().getLongProperty("bot.reconnectBaseMs", 5000);
    static final long RECONNECT_MAX_MS =
        AIConfiguration.getInstance().getLongProperty("bot.reconnectMaxMs", 120_000);
}
