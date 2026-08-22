# 🚀 START HERE — read first, every session, every agent instance

> Fast orientation for any agent in this repo. This file is evergreen (no changelogs):
> **current state lives in `STATUS.md`**, open work in `Documentation/TASKS.md`,
> all rules in `Documentation/WORKFLOW.md`, the active program in
> `Documentation/UpgradePlan/README.md`.

## 0. The ONE goal
Transform the AIPlayerEngine into the **Living Server**: a small cast of smart AI citizens
(quest arcs, parties with humans, economy, schedules) that real players join and play WITH.
Quality over quantity — five memorable souls beat fifty anonymous grinders.

Honest one-paragraph state: external-socket bot engine, **all tests green**, 50-bot
mixed-race fleet farms organically with a live web dashboard; `phase0`/`engine` namespaces
are gone (post-EP-3 package tree); zero hardcoded credentials. Exact wave status, hashes and
next steps: **`STATUS.md`**.

## 0.5 The core basis — learn this FIRST (every agent, every session)
> The full picture lives in **`Architecture.md`** (repo root) — read it before touching code.

- **What:** L24Lude = a Living Server. A cast of smart AI citizens running **next to** a
  vanilla L2JMobius Interlude server; humans join and play *with* them. Quality over quantity.
- **Never edit the server**: `SourceCode/` and `ServerBuild/` are read-only ground truth.
  The engine is **external sockets only** — bots speak REAL client packets; that's the ONLY
  gameplay path ("client-parity").
- **Three planes**: AIPlayerEngine (own JVM) ⇄ ControlPlane middleware ⇄ vanilla GameServer
  + a future `scripts/custom/ServerBridge` (datapack script, loopback+token, read-mostly,
  fail-closed). The server core stays vanilla — no forking.
- **How a bot thinks**: per-bot tick (~300 ms, virtual threads): packets → `BotSnapshot` →
  goal ladder `QUEST > HUNT > RELOCATE > RESTOCK > IDLE > SLEEP` → planners → packets out.
  Decisions in `behavior/`, facts in `knowledge/`, learning in `learning/`, observations via
  `web/EventRing` → `logs/fleet/events.jsonl` (LW-*).
- **Where the roadmap points**: hardcoded knowledge tables → JSON extracted from the datacopy
  (GK-*); the open learning loop gets closed (IN-*); souls get DB identity + wake/sleep
  schedules (LI-*/EN-*); everything controllable from a secured admin API (P6).

## 1. Run it (bring the fleet to life)
```bash
# Login/Game on JDK25 (system java is JDK21; server JARs need JDK25)
export PATH=~/.jdk/jdk-25.0.4+7/bin:$PATH
cd /home/dadj/Projects/l24lude/ServerBuild/login && ./LoginServerTask.sh
cd /home/dadj/Projects/l24lude/ServerBuild/game  && ./GameServerTask.sh
# wait for "Login client listener started on 0.0.0.0:2106", then GameServer
# "Server loaded in N seconds" + "Registered on login as Server N"

# bot fleet + web dashboard (servers must be up first; secrets from scripts/fleet_env.local)
cd /home/dadj/Projects/l24lude && scripts/fleet_launch.sh 50 8210 ai_rand_ 500000 ELF,DARK_ELF,ORC,DWARF,HUMAN
# open http://<host-ip>:8210/?token=<DASH_TOKEN>  (not localhost from another machine)
# ops: scripts/health_check.sh 50   scripts/rotate_logs.sh   scripts/keep_alive.sh   scripts/backup_db.sh
# stop servers: pkill -f "GameServer(Task.sh|.jar)"; pkill -f "LoginServer(Task.sh|.jar)"

# build/verify (must stay green before AND after every task)
mvn -o -f AIPlayerEngine/pom.xml test
```
Server JARs are prebuilt Ant artifacts (`ServerCode map: Documentation/SOURCE_CODE_MAP.md`);
never rebuild or edit them — the engine is external sockets only.

## 2. Doc map (read only what you need)
| Doc | Read when |
|---|---|
| `Architecture.md` | **first** — the core basis: planes, engine internals, hard lines |
| `STATUS.md` | you need current wave/test/ops state (updated every milestone) |
| `Documentation/TASKS.md` | picking work — the ONLY live task board (100 tasks, phases) |
| `Documentation/WORKFLOW.md` | rules: sessions, commits, doc-sync, RuntimeLogs, style |
| `Documentation/UpgradePlan/README.md` | the active program (Living Server waves, audit facts) |
| `Documentation/REVIEWED_TASKS.md` | checking what is already DONE (anti-redo registry) |
| `AIPlayerEngine/README.md` | engine architecture + old→new package translation table |
| `Documentation/MODE_PARTIAL_INDEX.md` | touching a `MODE:PARTIAL` file |
| `Documentation/SOURCE_CODE_MAP.md` | reading server source / datapack (read-only ground truth) |
| `Documentation/RuntimeLogs/` | per-task evidence (≤70-line records) |
| `Documentation/_archive/_ARCHIVE_INDEX.md` | anything historical — check before trusting old paths |

## 3. Code routing table
| You want to touch | Read first |
|---|---|
| Fleet launcher (thin) | `examples/FleetPlay.java` → `core/FleetConfig.java` (args/knobs), `core/BotSession.java` (session machine), `web/DashboardBoot.java` |
| Frame wiring / login+play loop | `core/CoreWiring.java` |
| Behavior integration seam | `core/EngineWiring.java` |
| Live bot state / snapshots | `core/BotSnapshot.java`, `core/GameStateMirror.java` |
| Goal/play controller | `behavior/` root (BotPlayController, BotBrain, Director…) |
| Combat / movement / quests | `behavior/combat/`, `behavior/movement/`, `behavior/quest/` |
| Humanization (anti-detect) | `behavior/humanize/` |
| Static game knowledge | `knowledge/` (RaceGuide, QuestDatabase, VendorDatabase…) |
| Learning / self-improvement | `learning/` (ReinforcementEngine, AdaptiveLearner…) |
| Config | `src/main/resources/config/ai-player.properties` — keys are `engine.*` |
| Dead code (do not resurrect lightly) | `AIPlayerEngine/attic/` (see its README) |
| Dashboard API (frozen v1 contract) | `README.md` §API routes + `web/DashboardApi.java` |

## 4. Hard rules (full set + workflow: `Documentation/WORKFLOW.md`)
1. **Never edit server source** (`SourceCode/`, `ServerBuild/`) — the engine is external sockets only.
2. `mvn -o -f AIPlayerEngine/pom.xml test` must stay **green** before and after every task.
3. **One task = one commit** (`type(scope): brief`), pushed to master immediately; update the
   TASKS.md row + a RuntimeLog (`Documentation/RuntimeLogs/<date>-<ID>-<slug>.md`, ≤70 lines).
4. Always `git pull --rebase origin master` before push; `git push origin master` right after.
5. Prove with tests + live evidence, never fake logs; leave the repo cleaner than found.
6. **No secrets in code/scripts**: passwords/tokens come from `scripts/fleet_env.local`
   (gitignored; copy `fleet_env.local.example`) or env — `AI_ACCOUNT_PASSWORD`, `DASH_TOKEN`,
   `DB_USER`/`DB_PASS`; the engine fails fast without them.

## 5. Session boot
```bash
scripts/session_start.sh          # resume-aware orientation (orients + reality-check)
```
If `SESSION_IN_PROGRESS.md` exists at repo root, resume it — the last session was cut off
mid-work (protocol: `WORKFLOW.md` §Resumability).
