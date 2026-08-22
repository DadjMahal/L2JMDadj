# RuntimeLog — 2026-08-22 GK-12 — verify_live.py (live-verification job)

## Task
GK-12 — Live-verification job: sample generated JSON against live server values.

## What was done
`scripts/datapack/verify_live.py` — a standalone verification JOB (not a data generator).
What "live server values" means for this XML-datapack server: static truth = the datapack
files the running L2JMobius server loads; runtime truth = the `gameserver` MySQL DB.

- **PART A (always, offline-safe)** — re-samples 6 deterministic generated JSON values
  back against the exact datapack files the server loads:
  1. npc 20223 Mandragora Sprout name+level vs `stats/npcs/*`
  2. item 1 Short Sword name/price/weapon_type vs `stats/items/*`
  3. trainer 30010 Auron classIds vs `SkillLearn.xml`
  4. teleport Roxxy 30006 → Dwarven Village coords vs `teleporters/town/30006.xml`
  5. quest 6 giver (startNpc 30006) + start dialog page on disk vs quest html
  6. skills class-0 ladder size vs `classSkillTree classId=0` XML
  **6/6 PASS**; any mismatch = exit 1 (catches extractor drift / stale JSON).
- **PART B (best-effort, EP-6)** — with DB_USER/DB_PASS (scripts/fleet_env.local, same as
  backup_db.sh), queries `gameserver.characters` for the live `ai_%` fleet (count/level
  range). With no creds or DB unreachable it prints an honest `SKIP` (exit 0) — never a
  fake pass.

## Pasted (audit acceptance)
```
$ python3 scripts/datapack/verify_live.py
[1] npc 20223 name+level: PASS — JSON(Mandragora Sprout/20) vs XML(Mandragora Sprout/20)
[4] Roxxy to Dwarven Village: PASS — JSON(115120,-178112) vs XML(115120,-178112)
...
[DB] SKIP — no DB_USER/DB_PASS set (scripts/fleet_env.local)
[verify-live] OK — all samples match the live server datapack
[validate] files=11 issues=0
mvn: Tests run: 573, Failures: 0, Errors: 0
```

## Evidence / gate
- Feat+claim commit `3a0cc836` (script is a job — no new generated data file, so no new
  Java test needed; the gate stays green via the existing 573 tests). Pushed to master.
- The stale GK-8 WIP (`KnowledgeBase.java`, uncommitted) stays untouched.