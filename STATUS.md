# 📊 STATUS — Phase: PLAY

**Phase: PLAY** — the ONE goal is 3–5 AI player bots that **actually play the game** (fight, level,
travel, pass quests — never idle). Suite is green; TIM-001 is done and the fleet farms organically.
The only live task board is `Documentation/TASKS.md` — STEPs 1–4 are DONE, **STEP 5 (despawned-target
`DeleteObject` lifecycle) is DONE (verified)** on the P2 fixed build: bots no longer chase a corpse after
a target despawns — 1606 `RE_TARGET` despawn-hops, 1468 kill-XP receipts, 0 server exceptions over a
1h50m 5-bot soak, all 5 chars accruing large persisted XP (02/03/04/05 → L8). Active lane: **STEP 6**
(idle-relocation empty-zone dead-end — an out-of-hostile bot freezes at `movedLast60=0` because idle
far-travel MoveToLocation doesn't persist server movement; world → last-XP/nearest-mate routing).
**GUIDE-MAP has landed (`ce3e2426`)** — `com.aiplayer.phase0.guide.RaceGuide` is a real-source per-race/
profession map (newbie Q1–Q10, tutorial, Path Q401–418, Trial Q211–235, Saga Q70–100, teleport legs,
hunt zones) where every coordinate is a real in-world point; `idleAnchor(race,level)` now returns a
verified landmark/gatekeeper location instead of the void `(16600,17000,434)` bots idle at — feed it
into STEP 6's displacement target. Full suite green (315 tests, incl. 14 `RaceGuideTest`).
All historical, audit, and evidence docs were archived 2026-08-13 to `Documentation/_archive/` (see its
`_ARCHIVE_INDEX.md`); do not redo them. Read `START_HERE.md` for how to bring up Login/Game on JDK25,
launch FleetPlay 5 + the dashboard, and the hard rules (no server-source edits, mvn test green, one
task one commit, push, no new audits). Anti-redo registry: `Documentation/REVIEWED_TASKS.md`.
