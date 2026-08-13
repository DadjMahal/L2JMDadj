# 2026-08-13 — Leftover sweep 1→6 (team dispatch): final state

Date: 2026-08-13 (EEST). Lane: lead + 4 teammates (ops-live, docs-sync, qe-tests, quest-nav).

## Summary of the six leftovers

| # | Leftover | Outcome |
|---|----------|---------|
| 1 | Servers DOWN (Login 2106/9014, Game 7777) | **RESOLVED** — stack brought back UP on JDK25 by ops-live; Login `*:2106` + `[::ffff:127.0.0.1]:9014`, Game `*:7777`; registered on login as Sieghardt (game/log/java0.log). MySQL UP, 5 chars. |
| 2 | Bots reset L22→L5 + Long Swords (H5 proof) | **DECISION: KEEP L5** — it is the long-term organic-XP farming baseline (fleet farms TI L1–4). Restoring L22 would remove them from the farm range. Restore path preserved in `scripts/reset_fleet_xp.sh` (`MODE=baseline` snapshot + `MODE=reset` SQL-anonymize, not auto-run). Live `characters.level = 5` for all 5 bots, confirmed 2026-08-13. |
| 3 | Teammate-dispatch infra reportedly broken | **RESOLVED** — full team dispatch (spawn 4 teammates, 6 tasks) worked end-to-end this session; no auth/capacity failures. |
| 4 | Audit 46 P0 (MoveTelemetry honesty + hop-gate tests) | **RESOLVED** — commits `2b010086` (MoveTelemetryTest 5 sent/3 moved/2 degraded + HopGate helper + FleetPlay refactor) + `4c9b432c` (docs). Suite 223→**230**. |
| 5 | Audit 48 W5 quest-NPC nav | **Stage A RESOLVED** — commit `bcd7d8c6`: `QuestNpcNavigator` (ack-gated ZoneRouter.hops ≤4800u + HopGate send/advance/resend + stuck abandon; target from QuestDatabase TALK/RETURN + QuestProgressTracker). 12 tests. Suite 230→**242**. Stages B (0xb0), C (0x21), D remain future. |
| 6 | Stale test-count in docs (218 vs 223) | **RESOLVED** — commit `36f583cc`: START_HERE/STATUS/TASKS synced to 223/223, stack state DOWN→(brought back UP), all 33 WPT DONE-PUSHED, TIM-001 RESOLVED. |

## Final suite
`mvn -o test` (JDK25) → **Tests run: 242, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**.

## Note
UNTRACKED artifact `1786570459532_z2q60.json` (team-runtime JSON) was left uncommitted intentionally.