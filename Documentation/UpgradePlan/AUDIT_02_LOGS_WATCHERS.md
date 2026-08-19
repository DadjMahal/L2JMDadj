# 📡 AUDIT 02 — Logs, watchers & session/progress (goal 2)

> Owner question: *"Our log system should be smarter. Create rules for Deepseek (Cline) to
> create proper watchers. Watchers look at how players play and write data. Main files for AI
> (sessions, progress, tasks) should be refactored."*

## 1. Current state (verified)

| Stream | Producer | Format | Destination | Rotation | Survives reboot? |
|---|---|---|---|---|---|
| Engine stdout | FleetPlay via java.util.logging | free text | `/tmp/fleet_launch_<prefix>.log` (fleet_launch.sh redirect) | scripts/rotate_logs.sh | ❌ /tmp |
| Fleet watcher | scripts/watch_fleet.py (polls `/json` every N s) | human line or JSONL | `/tmp/watch_fleet.log` + `.state` | none | ❌ /tmp |
| Dashboard events | web/EventRing + HistoryRing (in-memory rings) | JSON in RAM | none on disk | n/a | ❌ lost at exit |
| Health ops | health_check.sh, keep_alive.sh, server_health.sh, backup_db.sh | text | stdout/`/tmp` | none | ❌ mostly |
| Quest progress | phase0/quest/QuestProgressTracker | Java objects | **saveToRedis()/loadFromRedis() are documented no-ops** — in-memory only | n/a | ❌ resets |
| Session records | BotLoop inside FleetPlay.java (in-memory) | objects | none | n/a | ❌ |
| Human logs | Documentation/RuntimeLogs/*.md (per task) | markdown | repo | git | ✅ |
| Server logs | ServerBuild/*/log/ | text | ServerBuild | server-side | ✅ |

Watcher defects (scripts/watch_fleet.py):
- Race is **guessed from the account-name index modulo 5** (`ai_rand_<i>` → `RN[i % 5]`) instead
  of reading the bot's race field the dashboard already exposes (per-race telemetry exists since S8-T08).
- Aggregate-only (per-race counts) — **no per-bot observation rows**, so nothing usable as a
  learning dataset.
- Fixed-duration loop; dies silently; no failure escalation; output unversioned (schema-free).
- 45+ scripts in scripts/ mix one-shot historical proofs (b3–b10, tim001_*, c5/c7) with live ops
  — a new agent cannot tell which watchers are canonical.

## 2. Target design

### 2.1 Logging (engine side)
- One **structured event bus**: every bot emits `BotEvent {ts, bot, level, type, data}` — the
  EventRing already models this; add a **file sink** `logs/fleet/events.jsonl` (append, daily
  rotate, gzip after 7d). Free-text JUL logs stay for humans, but machine data flows through events.
- Log levels policy: ERROR = needs human, WARN = degraded bot, INFO = lifecycle, FINE = tick noise
  (off by default).

### 2.2 Watcher taxonomy + contract (the rules Deepseek must follow)
Every watcher lives in `scripts/watchers/`, is a small Python module with:
1. **id + purpose** (health | fleet-aggregate | per-bot behavior | progress);
2. **source** (dashboard API / events.jsonl / DB) — never screen-scraping text logs;
3. **output** = JSONL under `logs/watch/<id>/YYYY-MM-DD.jsonl` with a **frozen schema** documented
  at file top; NEVER /tmp; state file next to output;
4. **interval + duration** (interval required, duration optional = run forever);
5. **failure policy**: source unreachable → log a WARN row and continue; 5 consecutive failures →
  write a `watcher_unhealthy` event and exit non-zero (systemd/keep_alive restarts it);
6. **idempotent + resumable**: re-running continues from state (last seq/ts), no duplicate rows;
7. **no secrets** in output; **no per-row allocations** that grow unbounded (cap ring buffers).
Full rules doc = deliverable `Documentation/WATCHER_RULES.md` (task LW-2).

### 2.3 Behavior dataset (feeds goal 4 learning)
`BehaviorWatcher` joins `events.jsonl` + dashboard `/json` snapshots into
`data/observations/YYYY-MM-DD.jsonl`: one row per bot per minute
`{ts, bot, race, class, level, xpDelta, kills, state, pos, hopSuccess, deaths, potions}` — this
becomes the reward stream for IN-* tasks.

### 2.4 Sessions / progress / tasks refactor (owner item 2.3)
- `BotSession` (post EP-4) gets a **session resume file** per bot: `data/sessions/<account>.json`
  (quest state, last anchor, deaths window, reconnect backoff) written on change, read on boot —
  bots resume, not restart from zero.
- `QuestProgressTracker` gets real file persistence (same directory) replacing the no-op
  save/load — the code comments literally say "resets on restart until a real persistence need
  is measured"; the UpgradePlan is that need.
- "Tasks" (the board) stays human markdown — but a tiny `scripts/task_status.sh` grep helper
  gives watchers/CI the plan status without parsing by hand.

## 3. Task prompts for Deepseek-v4-flash

| ID | Task | Effort | Depends | Status |
|---|---|---|---|---|
| LW-1 | Event file sink: EventRing → logs/fleet/events.jsonl (rotated) | M | – | TODO |
| LW-2 | Write Documentation/WATCHER_RULES.md + watchers template | S | – | TODO |
| LW-3 | Canonical watcher suite: health / fleet / behavior / progress | M | LW-1, LW-2 | TODO |
| LW-4 | Kill /tmp: all persistent outputs under logs/ + data/, rotation everywhere | S | LW-3 | TODO |
| LW-5 | Session + quest-progress persistence (data/sessions/, tracker file store) | M | EP-4 | TODO |

### PROMPT LW-1
```
TASK LW-1: Structured event file sink

CONTEXT
- com.aiplayer.web.EventRing holds events in RAM only. Engine logs go to stdout → /tmp.
- Read Documentation/UpgradePlan/AUDIT_02_LOGS_WATCHERS.md §2.1.

GOAL
Every event that enters EventRing is ALSO appended to logs/fleet/events.jsonl (repo-relative
under AIPlayerEngine/working dir; create dir). Rotation: at local midnight or 50 MB, whichever
first, keep 7 previous gzip'd. Write must be async (bounded queue, drop-oldest on overflow with
a counter event).

STEPS
1. Extract the JSON serialization EventRing already does into one serializer method; reuse it
   for the sink (no schema drift between API and file).
2. Add a tiny JsonlSink class (core/) with injectable path for tests; unit-test rotation +
   drop-oldest + reopen-on-rotate.
3. Wire DashboardApi/EventRing to the sink; add JVM flag -Dfleet.logDir override.

ACCEPTANCE
- Tests green incl. 3 new sink tests; 3-bot 2-min live run → paste `wc -l` + first/last line of
  events.jsonl; RuntimeLog.
```

### PROMPT LW-2
```
TASK LW-2: WATCHER_RULES.md + watcher template

CONTEXT
- Audit 02 §2.2 defines the watcher contract. Deepseek will create future watchers using ONLY
  this rules doc — it must be self-sufficient.

GOAL
Documentation/WATCHER_RULES.md (≤120 lines): taxonomy, the 7-point contract, output schema
conventions (JSONL, frozen header comment), failure policy, directory layout
(logs/watch/<id>/, data/observations/), and a copy-paste skeleton scripts/watchers/_template.py.
Also migrate scripts/watch_fleet.py → scripts/watchers/fleet_watcher.py conforming to the rules
(read race from the bot JSON field, not account-name modulo).

ACCEPTANCE
- Rules doc renders correctly; template passes `python3 -m py_compile`; new fleet_watcher run
  3 min against a live dashboard → paste 3 output rows (correct races!); old watch_fleet.py
  kept as thin wrapper calling the new one (no caller breakage); RuntimeLog.
```

### PROMPT LW-3
```
TASK LW-3: Canonical watcher suite (health, behavior, progress)

CONTEXT
- After LW-1/LW-2. BehaviorWatcher output is the learning dataset (Audit 02 §2.3).

GOAL
scripts/watchers/ contains: health_watcher.py (ports 2106/7777/9014, GS process, DB ping +
character counts; status row every 60 s), behavior_watcher.py (per-bot per-minute rows to
data/observations/YYYY-MM-DD.jsonl joining events.jsonl + /json), progress_watcher.py (per-bot
level/quest-journal/adena deltas into logs/watch/progress/). All follow WATCHER_RULES.md.

STEPS
1. Implement each per the template; state files enable resume (no duplicate rows on restart).
2. Add scripts/watchers/README.md table: watcher, source, interval, output.
3. Deprecate duplicated legacy scripts (health_check.sh, watch_fleet.py direct use) by making
   them call the watchers; one-off proof scripts stay untouched.

ACCEPTANCE
- Tests/py_compile green; 10-min live run with ≥5 bots → paste one row per watcher output +
  `ls -R logs/watch data/observations`; kill -9 + restart of behavior_watcher shows resume
  without duplicate rows (paste tail); RuntimeLog.
```

### PROMPT LW-4
```
TASK LW-4: No more /tmp

CONTEXT
- fleet_launch.sh writes /tmp/fleet_launch_<prefix>.log; watchers used /tmp before LW-3;
  anything persistent must live in-repo.

GOAL
All persistent outputs under AIPlayerEngine-relative logs/ and data/ (gitignored, except
.gitignore entries), rotation wired for every long-lived file; scripts/fleet_launch.sh honors
FLEET_LOG_DIR env (default logs/fleet/console-<ts>.log); keep_alive.sh/rotate_logs.sh updated
to the new paths.

ACCEPTANCE
- grep -n "/tmp" scripts/*.sh scripts/watchers/*.py → only genuinely-temporary uses (mktemp)
  remain, each with a comment; 5-min live run shows console log + events.jsonl + watcher rows
  under logs/ (paste tree); RuntimeLog.
```

### PROMPT LW-5
```
TASK LW-5: Session & quest-progress persistence

CONTEXT
- BotSession (post EP-4) loses everything on restart; QuestProgressTracker.save/load are no-ops
  (phase0/quest/QuestProgressTracker.java:214-224 — comment says progress resets).

GOAL
data/sessions/<account>.json holds: activeQuests/journal, quest dialog state, last anchor,
relocation/escape counters, death-window, potion cooldowns, reconnect backoff. Written on change
(debounced 1 s), atomic (tmp+rename), read on bot boot → bot RESUMES. QuestProgressTracker
persists activeQuests/completedQuests through the same files (replace the no-op methods with a
file store behind a small interface so tests can use a temp dir).

STEPS
1. Define SessionState record + JSON (de)serialization; unit tests round-trip.
2. Wire BotSession save points (state transitions from Audit: quest accept/turn-in, hop, death,
   reconnect) + load on start.
3. Replace QuestProgressTracker no-ops with the file store; existing tests updated; add a
   restart-resume test (create tracker, complete quest, new instance same dir → state present).

ACCEPTANCE
- Tests green (+3 new); live proof: run 3 bots 2 min, stop fleet, relaunch, paste evidence that
  bot resumes prior quest state (journal non-empty at t=10 s after boot); RuntimeLog.
```
