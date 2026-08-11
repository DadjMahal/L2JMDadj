# Audit 44 — TIM-001 deep review: bots look static (movement/quest/combat)

**Date:** 2026-08-11 · **Branch:** `fix/tim-001-movement-review` · **Suite:** 154/154 green (+9)

## Why this pass existed

`Documentation/PRIORITY_TASKS.md` (TIM-001, HIGH) reported bots appearing **static** on the
operator dashboard. Staff knowledge said levels were *seeded* (`exp=1400000` → L20/22 at login),
legitimately confusing "has a level" with "is playing". This audit turned the open question into a
code-level verdict and shipped the two things the checklist demanded: an **evidence instrument**
(`MoveTelemetry` + `/telemetry` endpoint) and a **proactive far-travel loop** behind a new,
default-OFF `phase0.movement` flag.

## Deep-review findings (code-level, evidence-first)

| Hypothesis | Verdict | Evidence in code |
|---|---|---|
| **H1 — MoveToLocation(0x01) moves the char server-side** | **PROVEN** (short hops). Cline#4 live-proved +400u persistence BEFORE this branch. BUT far single-moves are REJECTED by a server cap | `MoveToLocation.java:156-163` (`9900*9900` distance check) |
| **H2 — destinations degenerate / stale coords** | **Root causes found:** (a) the `phase0.movement` module is dead — reads the never-populated `GameStateMirror`, frames through `L2JProtocol.sendMove` = **login socket**, old 21-byte frame; (b) HOP-CAP unknown — the fleet sent single moves up to 30,000u that the server silently rejects (`ActionFailed`) | `MovementController`/`StuckDetector`/`KiteController`; `L2JProtocol.sendMove` line 350; `MoveToLocation.java:156-163` |
| **H3 — bots only chase nearby hostiles, never travel** | **TRUE**: the only idle action was `targetSelector`-or-±900-u wander; combat frames rely on server auto-follow into melee | `FleetPlay` `case IDLE` |
| **H4 — DB spawn vs live position mismatch** | **Not caused by the engine loop**; chars inserted at TI, server spawns there; live test automated (`tim001_move_probe.sh` before/after diff) | proof script §1/§4 |
| **H5 — organic XP vs seeded 1.4M** | **Not provable from level alone.** XP-delta evidence added (`expGained`) | `MoveTelemetry.expGained` |

**Key live finding (this audit, 2026-08-11):** running the fleet with movement ON showed
`EVIDENCE-H1 serverMoved=0` for all 5 bots despite 3–4 far moves each — the chars logged in, got
~190 server position samples, but never changed position. Reading the server handler explained it:
`MoveToLocation.java:156-163` refuses any single move with `(dx²+dy²) > 98010000` (i.e. > 9900u).
Our far routes (farm zones up to 130,000u away) were one giant frame → `ActionFailed`. The fix is
to walk far routes in ≤4800u hops (`ZoneRouter.RouteGoal.nextHop()`).

**The real fix is architectural:** the brain (`CombatAI`) emits attack/target decisions — it never
issued movement goals. The `phase0.movement` package *almost* did this but was (a) un-wired (no
`phase0.movement` flag existed in `Phase0Config`), (b) sourced from a mirror that was never fed,
and (c) pointed at the wrong transport/frame. This pass keeps that package as-is (migration is
`Documentation/Claude_upgrade_brief.md` W2/W6) and adds a **proven-path** loop instead.

## What was added (all additive, `phase0.movement` default OFF)

| New file | Role |
|---|---|
| `phase0/movement/MoveTelemetry.java` | Evidence harness: records every MoveToLocation frame + every server-acked position/exp; `report()` emits paste-able `EVIDENCE-H1/H2/H5` lines |
| `phase0/movement/ZoneRouter.java` | Pure decision-maker: real FAR destination when idle — nearest level-appropriate farm zone (real Interlude zone DB) or a bounded random far point, **split into ≤4800u hops** (`nextHop()`) because the server rejects single moves >9900u |
| `scripts/tim001_move_probe.sh` | Live runbook: forces `phase0.movement` on (6th CLI arg), runs the fleet, curls `/telemetry` + `/json`, diffs `gameserver.characters` before/after, prints an H1–H5 verdict block |

| Touched file | Gated hook |
|---|---|
| `Phase0Config` | `isMovementEnabled()`, `getMovementIdleRouteMs()`, `getMovementMin/MaxRadius()` |
| `config/ai-player.properties` | `phase0.movement` + 3 tunables, all `false`/default by default |
| `examples/FleetPlay` | per-tick `telemetry.recordPosition`; IDLE branch routes via `ZoneRouter` when flag ON; `/telemetry` endpoint; `movedLast60`/`movesSent` surfaced in `/json`; 6th CLI arg `movement` force-on for proofs |
| `resources/dashboard/index.html` | grid gains **Δ1m** (server-acked movement) + **Moves** columns — the "are they moving?" truth the operator asked for |
| `web/DashboardApi.java` | compile fix only — pre-existing WPT-01 WIP had `indexJson()` returning `String` via unparenthesized `.getBytes()` (dormant because the file was never recompiled; surfaced by this branch's clean build) |

## Tests (145 → 154)

- `MoveTelemetryTest` (4): frame counting + degenerate detection (H2), server-ack window (H1),
  0,0 pre-world filtering, organic XP (H5), report keys.
- `ZoneRouterTest` (3): bounds respected, farm-zone preference, refuses pre-world route.
- `Phase0MovementConfigTest` (2): movement ANDs with `phase0.enabled`; defaults are sane.

## Honest status

- **PROVEN at code level:** the fleet now *has* a far-travel loop + an instrument that reports
  server-acked movement deltas and XP deltas — runnable with `scripts/tim001_move_probe.sh`.
- **NOT yet live-proven on this box:** no GameServer/LoginServer process is running under
  `/home/dadj/Projects/l24lude` (the live stack lives at `/home/volodro/L2JM`). The proof script
  is the runbook for that box. Do not mark TIM-001 resolved until a `tim001_move_probe.sh` run's
  EVIDENCE-H1/H2/H5 lines + DB diff are pasted into the task's Done notes.
- The legacy `GameStateMirror`-based movement module stays PARTIAL/un-wired (W2/W6 migration).

## Next (for the next Claude/Cline session)

1. Run `scripts/tim001_move_probe.sh` on `/home/volodro/L2JM` (3+ min), paste EVIDENCE-H1/H2/H5.
2. Decide movement ownership per PRIORITY_TASKS scope item 3: keep `ZoneRouter` in FleetPlay or
   migrate `MovementController` to `BotSnapshot` + `GameServerClient` (W2) and wire it (W6).
3. W5 quest-NPC navigation (the visible "running to quest NPCs" goal) once W1/W4 data lands.