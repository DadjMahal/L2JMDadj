# RuntimeLog — 2026-08-22 EB-14 — BotSnapshot completeness (goal/sub-goal/cooldowns → dashboard)

## Task
EB-14 — BotSnapshot completeness: goal + sub-goal + cooldowns in telemetry for dashboard.

## What was done
The dashboard row already carried rich per-bot telemetry (journal, objective, reward, target),
but the bot's CURRENT intent was only free-text (`state`/`thought`), and the per-bot cooldowns
(regen hold / death guard / potion sip) lived only as BotSession privates — invisible to ops.

1. **`core/BotInfo`** (new fields):
   - `goal` (PlayerGoal name: SURVIVE/QUEST/ACQUIRE/FARM/REST),
   - `subGoal` (GoalAction name: MOVE_TO/COMBAT_TARGET/BYPASS/...),
   - `cooldownUntilSec` (seconds until the LONGEST active per-bot cooldown clears; 0 = none).
2. **`core/BotSession`** feed:
   - `info.goal`/`info.subGoal` populated next to the GoalDecision the controller emits each tick.
   - `info.cooldownUntilSec` computed from regenHoldUntilMs, deathGuardUntilMs, and
     lastPotionUseMs + BotSurvival.HP_POTION_COOLDOWN_MS (the potion constant lives there —
     wired with the real 20s value, not a duplicate).
3. **`web/DashboardApi`** — emitted ONLY in the EXTENDED row (`includeTelemetry` → /json +
   /api/players). The frozen /api/v1/bots object keeps its exact section-11 shape (its test
   asserts no `movedLast60`, and now no goal/subGoal/cooldown either — unchanged).

## Evidence / gate
- `DashboardApiTest` — fixture sets goal="FARM", subGoal="COMBAT_TARGET", cooldownSec=14; the
  legacy-combined (/json) test asserts they appear in the extended row; the frozen v1 test still
  passes (exact shape preserved).
- **GATE GREEN — 540/540 tests**, style 0, secret-lint clean (exit=0).
- One commit set, pushed to master.

## Note — P2 pillar complete
EB-01..EB-14 all ship. This closes the "Engine & Behavior Core" phase of the 100-task board.