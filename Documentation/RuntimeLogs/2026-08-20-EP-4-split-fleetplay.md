# EP-4 RuntimeLog — Split the FleetPlay god class

**Date**: 2026-08-20
**Commit**: 2b4cda1b
**Task**: EP-4 (AUDIT_01) — FleetPlay → thin launcher + core/FleetConfig + core/BotSession + web/DashboardBoot

## What changed
- `examples/FleetPlay.java` **1,391 → 76 lines**: main = FleetConfig.parse → applyRuntimeOverrides →
  shared fleet structures → DashboardBoot.boot → spawn BotSession threads
- NEW `core/FleetConfig.java` (200): arg parsing (positional semantics byte-identical to old main)
  + all live-loop tuning knobs (TICK_MS, CHASE_*, REGEN_HOLD_MS, death guard, reconnect backoff…)
  read from `engine.*`/`bot.*` config keys; `resolveRaces()` moved verbatim
- NEW `core/BotSession.java` (1,130): the whole per-bot session machine (was inner BotLoop) —
  tick loop, quest-dialog driver, potions, relocation, survival guards, reconnect/death guard.
  Collaborators (bots map, EventRing, HistoryRing, FleetMetrics) passed via constructor
- MOVED `examples/BotInfo.java` → `core/BotInfo.java` (git rename, 93% similar)
- NEW `web/DashboardBoot.java` (80): dashboard HTTP boot + static routes (/, /json, /report,
  /telemetry); /api/v1/* stays in DashboardApi
- `web/DashboardApi.java`: BotInfo import updated; gained `topItems()` + `entitySnapshot()`
  payload helpers moved from FleetPlay (dashboard payloads now have one home)
- NEW test `core/FleetConfigTest.java` (5 cases): verbatim fleet_launch.sh arg string, defaults,
  random-race mode, case-insensitive race list w/ unknown-token skip, non-"movement" 6th arg

## Verification
- `mvn -o -f AIPlayerEngine/pom.xml test` → **414 tests, 0 failures, 0 errors** (409 + 5 new)
- `wc -l`: FleetPlay 76 ✅ (<250), FleetConfig 200, DashboardBoot 80
- ⚠️ BotSession **1,130** vs <900 target — accepted deviation: extracted verbatim to guarantee
  zero behavior change; further shrinking (quest-dialog driver, survival guards) needs its own
  carefully-tested decomposition pass, deferred to a follow-up rather than risked here
- Live 3-bot smoke SKIPPED — game/login servers not running this session; full unit suite +
  compile green. Run `scripts/fleet_launch.sh 3` + `/api/v1/health` when servers are next up.

## Notes
- One test failed on first run: my test expected prefix/base at positions 6/7 when the
  "movement" flag is absent, but the ORIGINAL main always reads them at 7/8 (fixed positions).
  Checked `git show HEAD:FleetPlay.java` — code was right, test was wrong; fixed the test.
  Lesson: when extracting, the pre-split source on git is the ground truth, not intuition.
- Shared fleet statics (bots/events/history/metrics) became constructor params — BotSession
  holds no global state, so EP-7 (virtual threads) can construct sessions freely
