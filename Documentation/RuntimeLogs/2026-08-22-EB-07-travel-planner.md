# RuntimeLog — 2026-08-22 EB-07 — Town travel planner (behavior/movement)

## Task
EB-07 — Movement & travel planner: route between zones/towns using map.json + waypoints.

## What was done
1. **`behavor/movement/TravelPlanner.java`** (new, pure, deterministic) — the town/region travel
   DECISION core:
   - `plan(goalTown, fromTown, pos, goalPos, coins, level, teleportEnabled)` → `Plan`
   - `Mode`: **WALK** (goal within 12k), **TELEPORT** (real RaceGuide BFS gatekeeper leg, affordable
     ≤50k + level-eligible), **FALLBACK_WALK** (no route / too poor / too low / teleports disabled)
   - `Plan` carries the exact next leg (`leg`), the walk target (xyz) and the `tail` legs to chain.
   - `RaceGuide.route()` = existing BFS over the gatekeeper/boat network (Giran↔Aden etc., real prices).
- **Wired into the live fleet fallback** (`BotSession` idle relocation): when the relocation aim is a
  town/zone guide landmark, consult `TravelPlanner`; teleport intent marks the thought (actual
  gatekeeper interaction is a later integration task), walking stays the default. Default config
  `engine.teleport=false` → behavior unchanged until enabled.
- **`EngineConfig.isTeleportEnabled()`** — house-style guarded flag (default OFF, like all Phase-0
  switches).
- Test: `TravelPlannerTest` (8) — walks-when-close, unknown-goal, by-coords, TELEPORT on a real
  leg (Giran→Aden 9200a Lv20), too-expensive→walk, too-low-level→walk, teleport-disabled→walk,
  teleport-excludes-walk.

## Evidence / gate
- **GATE GREEN** — 477/477 tests (was 469), style 0 violations, secret-lint clean. (exit=0)
- Surgical: new `TravelPlanner.java` + test; `BotSession` relocation block; `EngineConfig` one flag.
- One task, one commit set (refactor + docs), pushed to master.