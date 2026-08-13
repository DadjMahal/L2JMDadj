# 📊 STATUS — Phase: PLAY

**Phase: PLAY** — the ONE goal is 3–5 AI player bots that **actually play the game** (fight, level,
travel, pass quests — never idle). Suite is **242/242 green**; TIM-001 is done and the fleet already
farms organically. The only live task board is `Documentation/TASKS.md` — active lanes are **STEP 1
BotPlay controller** (IN_PROGRESS, play-builder) and **STEP 2 quest accept/turn-in live loop** (TODO),
followed by STEP 3 (5-bot live run + play evidence) and STEP 4 (smartness polish). All historical,
audit, and evidence docs were archived 2026-08-13 to `Documentation/_archive/` (see its
`_ARCHIVE_INDEX.md`); do not redo them. Read `START_HERE.md` for how to bring up Login/Game on JDK25,
launch FleetPlay 5 + the dashboard, and the hard rules (no server-source edits, mvn test green, one
task one commit, push, no new audits). Anti-redo registry: `Documentation/REVIEWED_TASKS.md`.
