# AGENTS.md — start here, then follow the routing

This repo = L2JMobius Interlude server (read-only ground truth: `SourceCode/`, `ServerBuild/`)
+ external-socket AI bot engine (`AIPlayerEngine/`, Maven, all tests must stay green).

Read, in order (each doc owns exactly one job — don't look for this info elsewhere):
1. `START_HERE.md` — orientation: the ONE goal, how to run, code routing table, critical rules.
2. `STATUS.md` — current state (wave/test count/ops). Updated every milestone.
3. `Documentation/TASKS.md` — the ONLY live task board (open work; done work is in
   `Documentation/REVIEWED_TASKS.md` — check it before starting anything).
4. `Documentation/WORKFLOW.md` — the ONE rules reference (sessions, commits, doc-sync, style).

Non-negotiables (details in WORKFLOW.md): never edit `SourceCode/`/`ServerBuild/`;
`mvn -o -f AIPlayerEngine/pom.xml test` green before and after every task; one task = one
commit pushed to master; no secrets in code (use `scripts/fleet_env.local`); no fake evidence.
