# 📊 STATUS — Program: Living Server (UpgradePlan)

**Current program**: the UpgradePlan (`Documentation/UpgradePlan/README.md`) — transform the
engine into the **Living Server**: a small cast of smart AI citizens (quest arcs, parties with
humans, economy, schedules) that real players join and play WITH. Quality over quantity.

**Wave 1 (engine cleanup) is COMPLETE** (2026-08-20): EP-1 dead-code archive `4827ac0f`,
EP-2 relocation `9601dd77`, EP-3 phase0 purge `c049e612`, EP-4 FleetPlay split `2b4cda1b`,
EP-5 micro-package merge `5e61be6b`, EP-6 security pass `8bacb021`, EP-7 virtual threads
`933204d2`, EP-8 docs unification. Suite **415/415 green**; `phase0` namespace dead; zero
hardcoded credentials (secrets via `scripts/fleet_env.local`); fleet sessions, per-bot packet
readers, and the dashboard all run on virtual threads.

**Next**: UpgradePlan Waves 2–5 (living-server features) + the parallel-safe rows on
`Documentation/TASKS.md` (GK-1 knowledge extractor, LW-1 events.jsonl, LW-2 watcher rules).

Live ops snapshot (last verified fleet run): 50 random-race bots farming, dashboard on :8210
(`/?token=…` — EP-6), Login :2106 / Game :7777, watcher + health_check + keep-alive + DB backup
in place. Bring-up commands: `START_HERE.md` §1; rules: `Documentation/WORKFLOW.md`.
Historical docs: `Documentation/_archive/` (`_ARCHIVE_INDEX.md`); anti-redo registry +
board history: `Documentation/REVIEWED_TASKS.md`.
