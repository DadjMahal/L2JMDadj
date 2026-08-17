# 📊 STATUS — Phase: PLAY

**Phase: PLAY** — the ONE goal is 3–5 AI player bots that **actually play the game** (fight, level,
travel, pass quests — never idle). Suite is green; TIM-001 is done and the fleet farms organically.
The only live task board is `Documentation/TASKS.md` — STEPs 1–6 are DONE, **STEP 7 (ultra-smart vol.1)
is DONE-PUSHED `d2c75b4b`**: `RestockPlanner` turns the dead restock-WAIT into a real walk-to-vendor
shop trip (new `GoalAction.BUY`), `FleetSpreadPlanner` anti-clusters the fleet across real hunt zones,
and `QuestGoalPlanner` gained a reward-aware + per-bot `varietySeed`-diverse quest-giver pick.
Suite now **345 green** (was 325). STEP 5 (despawned-target `DeleteObject` lifecycle) is DONE
(verified) on the P2 fixed build: bots no longer chase a corpse after a target despawns — 1606
`RE_TARGET` despawn-hops, 1468 kill-XP receipts, 0 server exceptions over a 1h50m 5-bot soak, all
5 chars accruing large persisted XP (02/03/04/05 → L8). STEP 6 (idle-relocation empty-zone dead-end)
is DONE (world → last-XP/nearest-mate routing + escape gate); GUIDE-MAP has landed (`ce3e2426`) —
`com.aiplayer.phase0.guide.RaceGuide` is a real-source per-race/profession map where every coordinate
is a real in-world point; `idleAnchor(race,level)` now returns a verified landmark/gatekeeper
location instead of the void `(16600,17000,434)`. **Next up (live)**: turnaround the quest
accept→complete→turn-in loop for real on a live char and wire STEP 7's planners into the running
FleetPlay loop with live evidence.
All historical, audit, and evidence docs were archived 2026-08-13 to `Documentation/_archive/` (see its
`_ARCHIVE_INDEX.md`); do not redo them. Read `START_HERE.md` for how to bring up Login/Game on JDK25,
launch FleetPlay 5 + the dashboard, and the hard rules (no server-source edits, mvn test green, one
task one commit, push, no new audits). Anti-redo registry: `Documentation/REVIEWED_TASKS.md`.
