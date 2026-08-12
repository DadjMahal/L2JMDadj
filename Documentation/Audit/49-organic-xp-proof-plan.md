# Audit 49 - organic-XP (H5) proof plan

Date: 2026-08-12 . Lane: task_0025 . Tooling shipped, NOT run against a live DB.

## Why
Fleet chars were seeded with exp=1400000 (L20-22), so level presence proves nothing. To prove H5
(organic XP / real leveling) we need a real XP delta attributed to bot activity.

## Procedure (safe)
1. Baseline: run scripts/reset_fleet_xp.sh baseline (read-only snapshot to tmp/exp_baseline).
2. Run the fleet short multi-hop / combat for N minutes (movement enabled).
3. After: run status again; diff exp against baseline.
4. Expected evidence line: per char, BEFORE exp / AFTER exp / delta GT 0 and reported level-up events in
   /api/v1/events (from PacketLogger level-up parse).

## Caveat
- EXP accrues only on ACTUAL kills accepted server-side. If bots only move (no accepted combat kills),
  exp will not move -- so this proof must pair movement with a verified kill path.
- Do not reset a live DB carelessly: reset_fleet_xp.sh reset prints SQL but never executes.
