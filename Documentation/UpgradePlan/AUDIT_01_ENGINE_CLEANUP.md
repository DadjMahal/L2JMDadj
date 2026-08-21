# 🧹 AUDIT 01 — Engine architecture & cleanup (goal 1)

> Owner question: *"Fully cleanup our project, especially AIPlayerEngine — clear architecture and
> folders for real humans, no `phase0` imports, best practices + security, fewer files,
> perfect docs."*
> All facts verified 2026-08-19 by direct inspection (see README §5 for method).

## 1. Current state

```
AIPlayerEngine/src/main/java/com/aiplayer/   (320 files, 45,402 LOC)
├── engine/      141  ← legacy pile; ONLY 10 classes used by live code:
│                     CombatAI, AIBrain, AIPlayer, GameServerClient, CombatFramePlanner,
│                     CombatDecision, Phase0Integration, Phase0Config, AIPlayerEngine,
│                     AIConfiguration.  The other 131 have ZERO references from
│                     FleetPlay/phase0/web (verified by per-class grep).
├── examples/     23  ← 15 @Deprecated probes (8+ still hardcode default password
│                     "ai123pass") + FleetPlay.java (1,387 lines, the REAL launcher)
├── phase0/      ~120  ← the actual behavior engine, 19 subpackages (play, quest, town,
│                     movement, combat, guide, brain, social, chat, party, inventory,
│                     humanize, imperfection, farm, death, director, cabinet, protocol, root)
├── protocol/     11  ← L2JProtocol, PacketLogger (2,190 lines!), crypt/
├── web/           4  ← DashboardApi (694), EventRing, HistoryRing
├── advanced/      4  ← ReinforcementEngine, AdaptiveLearner, EmotionalState, PersonalityProfile
├── neural/        2  ← DeepLearningCore, PatternMemory
├── social/        3, economy/ 3, monitor/ 2, metrics/ 1  ← more legacy satellites
tests: 67 files / 383 green
```

## 2. Findings

| # | Finding | Evidence | Severity |
|---|---|---|---|
| F1 | "phase0" naming is meaningless to any human dev; it leaks everywhere (`Phase0Wiring`, `Phase0Config`, `Phase0Brain`, `MODE: PARTIAL` headers) | `grep -r "phase0" src/main` ≈ every behavior file | High |
| F2 | 131 dead classes still compiled, tested around, and read by every future dev/AI | per-class reference grep (README §5) | High |
| F3 | `FleetPlay.java` is a god class: arg parsing, provisioning, dashboard, per-bot `BotLoop` session machine (~1,000 lines inner), quest-dialog driver, potion logic | examples/FleetPlay.java:65-1387 | High |
| F4 | `PacketLogger.java` 2,190 lines mixes decode, state tracking, and HP/position bookkeeping | protocol/PacketLogger.java | Med |
| F5 | Password default `ai123pass` hardcoded in 8+ probe files (they are deprecated but still compiled) | MoveProbe.java:60, LoginProbe.java:15, PartyProbe.java:112-114, … | High (sec) |
| F6 | Dashboard auth off by default (`tokenAuth=false`) while fleet binds to LAN :8210 | web/DashboardApi.java:37-38 + fleet_launch.sh | High (sec) |
| F7 | Classic `new Thread(`/fixed pools; JDK 25 virtual threads unused (matters for RS scaling) | grep across main | Med |
| F8 | Micro-packages inflate file count: `imperfection` (3 files), `cabinet` (2), `director` (2), `humanize` (6), `brain` presets (10) — thin classes that belong together | package tree | Med |
| F9 | Config split between `AIConfiguration` and `Phase0Config` facade; properties read ad hoc (`getLongProperty("bot.tickMs",…)`) | FleetPlay.java:74-138 | Med |
| F10 | Scripts dir mixes 15+ one-shot historical proof scripts with live ops; `__pycache__` untracked dir present | scripts/ listing | Low |

## 3. Target architecture (package-by-feature, human-clear)

```
com.aiplayer
├── core/        config (AIConfiguration), BotSnapshot, wiring glue   [from phase0 root + engine/Phase0*]
├── protocol/    packets, crypt, PacketLogger (split: decoder + tracker) [unchanged name]
├── net/         GameServerClient, connection/session transport        [from engine/]
├── behavior/    BotPlayController, decision ladder, brain/state, presets [phase0/play + brain + director + cabinet]
│   ├── combat/  CombatAI, rotations, target scoring, frame planner     [phase0/combat + engine/CombatAI,CombatDecision]
│   ├── hunting/ farm scoring, zone density                             [phase0/farm]
│   ├── movement/ relocation, routing, humanized paths                  [phase0/movement]
│   ├── lifecycle/ death, respawn                                       [phase0/death]
│   ├── party/, social/ (chat+social merged), town/, inventory/, quest/ [1:1 from phase0]
│   └── humanize/ (humanize+imperfection merged)
├── knowledge/   RaceGuide + generated datapack JSON + loaders         [phase0/guide + new GK-*]
├── learning/    ReinforcementEngine, AdaptiveLearner, DeepLearningCore [advanced/ + neural/]
├── web/         dashboard (unchanged)
├── cli/         FleetLauncher (thin), provisioning, smoke probes      [examples/ after split]
└── attic/       (NOT compiled) archived legacy classes                [131 engine files]
```

Renames are mechanical: `phase0.quest` → `behavior.quest`, `phase0.play` → `behavior`,
`phase0.guide` → `knowledge`, `advanced`+`neural` → `learning`, etc. **File-count path:
320 → ~175 after attic (−131) and probes (−15+2 kept) → ~150 after micro-package merges.**

## 4. Security findings → tasks

- S1 (High): purge hardcoded password defaults (F5) → folded into EP-6.
- S2 (High): dashboard on LAN without token (F6) → EP-6 enforces token or loopback-only.
- S3 (Med): audit scripts/*.sh quoting/injection (esp. any `eval`, unquoted `$VARS` in curl/mysql) → EP-6.
- S4 (Low): verify no credentials in RuntimeLogs/_archive (grep) → EP-6.

## 5. Task prompts for Deepseek-v4-flash

| ID | Task | Effort | Depends | Status |
|---|---|---|---|---|
| EP-1 | Archive 131 dead engine classes to non-compiled attic/ | M | – | DONE-PUSHED 4827ac0f (compile-true closure: 90 archived, 51 live remain — audit's "10" under-counted transitive deps; see RuntimeLog) |
| EP-2 | Relocate the 10 live engine classes into target packages; remove engine/ | M | EP-1 | DONE-PUSHED 9601dd77 (51 classes per EP-1 closure; Phase0Config→EngineConfig, Phase0Integration→EngineWiring) |
| EP-3 | Rename phase0/* to behavior/* domain packages; purge the "phase0" token repo-wide | L | EP-2 | DONE-PUSHED c049e612 |
| EP-4 | Split FleetPlay god class: BotLoop → core session class; thin launcher in cli/ | L | EP-3 | DONE-PUSHED 2b4cda1b (1391→76 lines; launcher stayed in examples/, not cli/ — see RuntimeLog; BotSession 1130 vs <900 accepted, verbatim extraction; 414 tests green) |
| EP-5 | Merge micro-packages (humanize+imperfection; cabinet+director into behavior; brain presets → 2 files) | M | EP-3 | DONE-PUSHED 5e61be6b (230→218 main files; presets→ClassPreset, DirectorAI+NameGenerator→Director, humanize lows→Humanization, BotProfile→core) |
| EP-6 | Security pass: password purge, dashboard auth-by-default, script lint, secret grep | M | – | DONE-PUSHED 8bacb021 (30 pwd literals killed; LAN dashboard now token-gated w/ boot guard; DB creds required via fleet_env.local) |
| EP-7 | Virtual threads: BotLoop + dashboard executors on Thread.ofVirtual | M | EP-4 | TODO |
| EP-8 | Docs unification: engine README w/ new architecture diagram, cross-link sync, archive stale docs | S | EP-3 | TODO |

### PROMPT EP-1
```
TASK EP-1: Archive dead legacy classes

CONTEXT
- Repo /home/dadj/Projects/l24lude. Read Documentation/UpgradePlan/README.md §5 first.
- 131 of 141 classes in AIPlayerEngine/src/main/java/com/aiplayer/engine/ have ZERO references
  from live code (FleetPlay, phase0/**, web/**). KEEP these 10 in place: CombatAI, AIBrain,
  AIPlayer, GameServerClient, CombatFramePlanner, CombatDecision, Phase0Integration,
  Phase0Config, AIPlayerEngine, AIConfiguration (they stay in engine/ for now — EP-2 moves them).

GOAL
Move every OTHER engine/*.java class to AIPlayerEngine/attic/engine/ (a plain directory OUTSIDE
src/, so it is not compiled), preserving package declarations as comments at file top.

STEPS
1. Build the dead-list yourself: for each engine class, grep references in
   src/main/java/com/aiplayer/{examples/FleetPlay.java,phase0,web} AND src/test/java — a class
   referenced ONLY from other dead classes is still dead (compute closure, don't trust the list
   blindly). Paste the final keep/remove counts.
2. git mv the dead files to AIPlayerEngine/attic/engine/ (create dir + a short attic/README.md
   explaining what this is and how to resurrect a file).
3. Fix any compile fallout (should be none if the closure is right).

ACCEPTANCE
- mvn -o -f AIPlayerEngine/pom.xml clean test → BUILD SUCCESS, 383 tests green (paste tail).
- `find src/main/java/com/aiplayer/engine -name '*.java' | wc -l` → 10.
- attic/README.md exists; RuntimeLog + status-table update per template.
```

### PROMPT EP-2
```
TASK EP-2: Relocate live engine classes to target packages

CONTEXT
- After EP-1, engine/ holds 10 live classes. Target homes (Audit 01 §3): GameServerClient →
  com.aiplayer.net; CombatAI, CombatDecision, CombatFramePlanner → com.aiplayer.behavior.combat;
  AIBrain → com.aiplayer.behavior; AIPlayer → com.aiplayer.net; AIConfiguration, Phase0Config
  (rename class to EngineConfig), Phase0Integration (rename to EngineWiring) → com.aiplayer.core;
  AIPlayerEngine → com.aiplayer.cli (it is an old CLI entry; keep behavior).

GOAL
All 10 classes live in their target packages with updated package/imports repo-wide; engine/
directory deleted. No behavior change — this is a pure move+rename.

STEPS
1. One git mv + package/import fix per target package (5 small commits or 1 — your call, but
   tests green at each stop).
2. Update references in phase0/**, web/**, examples/**, tests.
3. grep -r "com.aiplayer.engine" src/ → 0 hits.

ACCEPTANCE
- Tests green (paste tail); grep above returns 0; engine/ dir gone; RuntimeLog.
```

### PROMPT EP-3
```
TASK EP-3: Kill the "phase0" namespace

CONTEXT
- com/aiplayer/phase0/** (~120 files) is the real engine. Target map in Audit 01 §3; the
  essential renames: phase0 → behavior (play/ root files), phase0.guide → knowledge,
  phase0.farm → behavior.hunting, phase0.death → behavior.lifecycle, phase0.imperfection →
  merged into behavior.humanize (EP-5 does the file merge — here just move), phase0 root
  classes (BotSnapshot, Phase0Wiring→CoreWiring, Phase0Config already moved) → core.
  advanced/+neural/ → learning/. social/+economy/ satellites: reference-check like EP-1 and
  attic them if dead, else → behavior.social / behavior.town.

GOAL
No "phase0", "Phase0" string remains anywhere under AIPlayerEngine/src or pom/resources
(except attic/ and Documentation archives). Package tree matches Audit 01 §3 exactly.

STEPS
1. Write scripts/rename_phase0.sh doing directory git-mv + sed of package/import statements.
   Run it. Review `git status` for accidental hits (comments are fine to update too).
2. Rename classes containing Phase0 in their NAME (Phase0Wiring→CoreWiring, Phase0Brain→
   BotBrain, Phase0ProtocolExt→ProtocolExt, Phase0Config if still present) with full caller
   updates. Also strip stale "MODE: PARTIAL" header comments ONLY in files you touch.
3. grep -ri "phase0" src/ → 0.

ACCEPTANCE
- Tests green (paste tail); grep 0 hits; package tree printed in RuntimeLog matches §3;
  RuntimeLog + status update.
```

### PROMPT EP-4
```
TASK EP-4: Split the FleetPlay god class

CONTEXT
- examples/FleetPlay.java is 1,387 lines: CLI args, fleet provisioning, dashboard boot, and the
  inner class BotLoop (~1,000 lines) implementing the whole per-bot session machine (tick loop,
  quest-dialog driver, potions, relocation, reconnect, death guard).

GOAL
FleetPlay becomes a thin launcher (~200 lines); the session machine becomes
com.aiplayer.core.BotSession (own file); config parsing becomes com.aiplayer.core.FleetConfig;
dashboard boot moves to a small com.aiplayer.web.DashboardBoot helper. ZERO behavior change —
same packets out, same timings.

STEPS
1. Extract FleetConfig (parse args/env exactly as today; add a parser test with the real arg
   string from scripts/fleet_launch.sh: "50 127.0.0.1 7777 2106 8210 movement ai_rand_ 500000 ELF,DARK_ELF,ORC,DWARF,HUMAN").
2. Extract BotSession from BotLoop (same fields, same methods; pass collaborators explicitly).
3. Extract DashboardBoot. FleetPlay.main = parse → provision → boot dashboard → spawn sessions.
4. Run the full suite; then a 3-bot live smoke (servers up, 90s run, paste dashboard /api/v1/health).

ACCEPTANCE
- wc -l: FleetPlay < 250, BotSession < 900, all compile; tests green; live smoke shows ≥3 bots
  online with xp gaining; RuntimeLog with pasted health JSON.
```

### PROMPT EP-5
```
TASK EP-5: Merge micro-packages (file-count reduction)

CONTEXT
- Post EP-3: behavior/humanize has 9 files (6 humanize + 3 imperfection) of tiny classes;
  brain presets = 10 files (ArcherPreset, FighterPreset, MagePreset, HealerPreset, BufferPreset,
  ClassPreset, PresetFactory…); cabinet/ (BotProfile) and director/ (DirectorAI, NameGenerator)
  are 1-2-file packages.

GOAL
Logical concatenation with NO public-API loss where callers exist:
- humanize+imperfection → behavior/humanize/Humanization.java (single facade) + keep tiny helper
  classes only if >80 lines each; target ≤4 files from 12.
- presets → behavior/ClassPresets.java (one file, nested records) + PresetFactory logic folded in.
- cabinet+director → behavior/Director.java (DirectorAI + NameGenerator) and core/BotProfile.java.

STEPS
1. For each merge: reference-check callers, merge into the target file, update imports, delete
   emptied files. Preserve all behavior used by live code; dead public methods die with the merge.
2. Update MODE_PARTIAL_INDEX (Documentation/) to reflect removals.

ACCEPTANCE
- Tests green; net main-file count drops ≥12 (paste before/after `find … | wc -l`);
  RuntimeLog.
```

### PROMPT EP-6
```
TASK EP-6: Security pass

CONTEXT
- web/DashboardApi.java:37-38 defaults bind=127.0.0.1, tokenAuth=false — but ops run it on LAN
  (:8210) unauthenticated. Deprecated probes carry default password "ai123pass"
  (MoveProbe:60, LoginProbe:15, PartyProbe:112, TradeProbe:81, QuestFlowLoop:48, GoalDrivenLoop:59,
  QuestProbe:86 …). scripts/*.sh never audited for quoting/injection.

GOAL
1. grep -rn "ai123pass" AIPlayerEngine/src → 0. Probes read the password ONLY from
   AIConfiguration/env; if a default is unavoidable for a deprecated probe, fail fast with a
   clear message instead.
2. DashboardApi: if bind != loopback and tokenAuth==false → refuse to start unless
   --insecure-acknowledged flag passed; fleet_launch.sh updated to pass a token via env
   DASH_TOKEN; watchers (scripts/watch_fleet.py, health_check.sh) send ?token=.
3. Script lint: run shellcheck (install if needed) over scripts/*.sh; fix unquoted vars, eval
   risks, tmp-race (mktemp); paste the before/after summary.
4. Secret history sweep: grep -rn "ai123pass\|password=" Documentation/RuntimeLogs scripts/
   AIStatusLogs → report hits, redact live files (docs archives may keep mentions in prose but
   no usable credential+host combos).

ACCEPTANCE
- All four greps clean (paste); dashboard still serves with token on 8210 (curl proof);
  tests green; scripts/fleet_launch.sh + watchers still work end-to-end (paste watch line);
  RuntimeLog.
```

### PROMPT EP-7
```
TASK EP-7: Virtual threads for the fleet

CONTEXT
- JDK 25 runtime; bots run on classic threads/pools. Goal 5 wants "launch 500/2000" — the
  cheap first win is virtual threads for the per-bot session loops and dashboard executors.

GOAL
BotSession loops (post EP-4) run on Thread.ofVirtual().name("bot-",i); dashboard + watcher
executors on virtual-thread factories. Sockets keep blocking IO (that's the virtual-thread
design point). No shared-mutable-state regressions: audit synchronized/ConcurrentHashMap usage
in BotSession and note pinning risks (synchronized blocks on shared objects) in the RuntimeLog.

ACCEPTANCE
- Tests green; a 20-bot live run for 5 min: paste thread count (`jcmd <pid> Thread.dump_to_file`
  summary or `jstack | grep -c virtual`) showing virtual threads active and RSS before/after;
  no perf regression in xp/min vs 20-bot baseline (watch_fleet line); RuntimeLog.
```

### PROMPT EP-8
```
TASK EP-8: Documentation unification

CONTEXT
- Post EP-1..EP-6 the repo structure changed materially. Docs referencing phase0/engine are now
  wrong everywhere (START_HERE routing table, SOURCE_CODE_MAP is server-side fine, engine
  README, MODE_PARTIAL_INDEX, TASKS board file-ownership map §4).

GOAL
One consistent doc set a human dev can onboard from in 10 minutes:
1. AIPlayerEngine/README.md rewritten: what it is, architecture diagram (Audit 01 §3 final
   state), build/run/test, package map table old→new, link to Documentation/.
2. Repo START_HERE.md + STATUS.md refreshed (routing table points at new packages; remove
   phase0 references).
3. Documentation/MODE_PARTIAL_INDEX.md pruned to only still-existing files.
4. Documentation/TASKS.md §4 ownership map updated to new paths.
5. Root README.md: keep dashboard/API contract; fix any stale paths.

ACCEPTANCE
- grep -ri "phase0" README.md START_HERE.md STATUS.md Documentation/*.md → only historical
  changelog mentions remain; every relative doc link resolves (spot-check 10);
  RuntimeLog.
```
