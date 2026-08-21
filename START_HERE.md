# 🚀 START HERE — read first, every session, every agent instance

> Fast binary orientation for any agent/session in this repo. The active program is the
> **UpgradePlan** (`Documentation/UpgradePlan/README.md`); the task board is
> `Documentation/TASKS.md`. Status vocabulary + hard rules live in the UpgradePlan README.

## 0. The ONE goal
Transform the AIPlayerEngine into the **Living Server**: a small cast of smart AI citizens
(quest arcs, parties with humans, economy, schedules) that real players join and play WITH.
Quality over quantity — five memorable souls beat fifty anonymous grinders.

**Current state (2026-08-20):** **414/414 tests green**, `phase0` namespace is DEAD (0 matches).
UpgradePlan **Wave 1 (engine purge)** underway: **EP-1 ✅ `4827ac0f`** (90 dead classes →
`attic/`), **EP-2 ✅ `9601dd77`** (51 classes relocated), **EP-3 ✅ `c049e612`** (package-by-feature
rename), **EP-4 ✅ `2b4cda1b`** (FleetPlay god class split: 1,391→76-line launcher + core/FleetConfig
+ core/BotSession + web/DashboardBoot), **EP-5 ✅ `5e61be6b`** (micro-packages merged: 230→218
files; presets→ClassPreset, DirectorAI+NameGenerator→Director, humanize lows→Humanization,
BotProfile→core), **EP-6 ✅ `8bacb021`** (security: 0 hardcoded creds — bot password + DB creds
resolve from `scripts/fleet_env.local` (copy `fleet_env.local.example`); LAN dashboard requires
`DASH_TOKEN`, SPA opens `/?token=…`). Package tree is now clean: `behavior/` (+9 subpackages),
`core/`, `knowledge/`, `learning/`, `net/`, `protocol/`, `web/`, `monitor/`, `metrics/`, `cli/`,
`examples/`.

## 1. Run it (bring the fleet to life)
```bash
# Login/Game on JDK25 (system java is JDK21; server JARs need JDK25)
export PATH=~/.jdk/jdk-25.0.4+7/bin:$PATH
cd /home/dadj/Projects/l24lude/ServerBuild/login && ./LoginServerTask.sh
cd /home/dadj/Projects/l24lude/ServerBuild/game  && ./GameServerTask.sh

# bot fleet + web dashboard (servers must be up first)
cd /home/dadj/Projects/l24lude && scripts/fleet_launch.sh 50 8210 ai_rand_ 500000 ELF,DARK_ELF,ORC,DWARF,HUMAN
# open http://<host-ip>:8210  (not localhost from another machine)
# ops: scripts/health_check.sh 50   scripts/rotate_logs.sh   scripts/keep_alive.sh   scripts/backup_db.sh
```
Build/verify (must stay green before AND after every task):
```bash
cd /home/dadj/Projects/l24lude && mvn -o -f AIPlayerEngine/pom.xml test
```

## 2. Active lane — UpgradePlan execution order
Wave 1 next up (EP-4/5/6 ✅ — Wave 1 engine purge COMPLETE except threads+docs):
- **EP-7** — virtual threads (BotSession + dashboard executors on Thread.ofVirtual)
- **EP-8** — docs unification (engine README + architecture diagram) — small
Parallel-safe anytime: **GK-1** (knowledge extractor skeleton), **LW-1** (events.jsonl sink),
**LW-2** (WATCHER_RULES.md + watcher template). Then Waves 2-5 per UpgradePlan README.

Task prompts are in each `AUDIT_*.md` (`### PROMPT EP-4` etc.) — read the prompt before starting.

## 3. Routing table (post-EP-3 package names — `phase0` no longer exists)
| You want to touch | Read first |
|---|---|
| Fleet launcher (thin, post EP-4) | `examples/FleetPlay.java` → `core/FleetConfig.java` (args/knobs), `core/BotSession.java` (session machine), `web/DashboardBoot.java` |
| Frame wiring / login+play loop | `core/CoreWiring.java` (was Phase0Wiring) |
| Behavior integration seam | `core/EngineWiring.java` (was Phase0Integration) |
| Live bot state / snapshots | `core/BotSnapshot.java`, `core/GameStateMirror.java` |
| Goal/play controller | `behavior/` root (BotPlayController, BotBrain, DirectorAI…) |
| Combat / movement / quests | `behavior/combat/`, `behavior/movement/`, `behavior/quest/` |
| Humanization (anti-detect) | `behavior/humanize/` |
| Static game knowledge | `knowledge/` (RaceGuide, QuestDatabase, VendorDatabase…) |
| Learning / self-improvement | `learning/` (ReinforcementEngine, AdaptiveLearner…) |
| Config | `src/main/resources/config/ai-player.properties` — keys are `engine.*` (was `phase0.*`) |
| Dead code (do not resurrect lightly) | `attic/` (90 classes, see `attic/README.md`) |
| **Task board / plan** | **`Documentation/TASKS.md`** + `Documentation/UpgradePlan/README.md` |

## 4. Hard rules
1. **Never edit server source** (`SourceCode/`, `ServerBuild/`) — the engine is external sockets only.
2. `mvn -o -f AIPlayerEngine/pom.xml test` must stay **green** (414/414) before and after every task.
3. **One task = one commit** (`type(scope): brief`), pushed to master immediately; update the
   AUDIT status table + TASKS.md row + RuntimeLog (`Documentation/RuntimeLogs/<date>-<ID>-<slug>.md`, ≤70 lines).
4. Always `git pull --rebase origin master` before push; `git push origin master` right after.
5. Prove with tests + live evidence, never fake logs; leave the repo cleaner than found.
6. **No secrets in code/scripts** (EP-6): passwords/tokens come from `scripts/fleet_env.local`
   (gitignored) or env — `AI_ACCOUNT_PASSWORD`, `DASH_TOKEN`, `DB_USER`/`DB_PASS`; the engine
   fails fast without them.
