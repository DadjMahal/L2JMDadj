# 📋 TASK BOARD — THE single source of truth for ALL work

> **Every task for every Cline instance lives in this file.** Read **`START_HERE.md`** (repo root) for orientation FIRST, then come back here to pick work.
> Do **not** create your own task lists anywhere else.

---

## 1. How to use — READ THIS FIRST, every Cline instance, every session

1. `cd /home/dadj/Projects/l24lude && git pull --ff-only origin master` — always start from latest.
2. Read `START_HERE.md` (fast orientation, ~2 min).
3. Open THIS file → pick a `TODO` task you don't collide with (check `Owner` + §4 file map).
4. **Claim it**: set your row to `IN_PROGRESS (Cline#N)` → **`git add` this file only → commit → push
   the claim immediately**, so every other instance sees it taken.
5. Implement with tests. Rule: **`mvn -o -f AIPlayerEngine/pom.xml test` must stay fully green** (145 + yours).
6. **One task = one commit**, push right after. Set row to `DONE-PUSHED <commit-hash>` + a short
   `Done notes` line → commit → push.
7. Never leave `master` dirty between tasks. On conflict: `git pull --rebase`, resolve, and if it's
   another instance's file (`§4`) ask the owner before merging.

## 2. Status vocabulary
`TODO` · `IN_PROGRESS (Cline#N)` · `BLOCKED (reason)` · `DONE-PUSHED <hash>`

## 3. Parallel-safety rules (nobody damages anybody)
- **File ownership map (§4): only the owner edits a file.** If you must touch someone else's file
  (e.g. new telemetry field), flip it to `BLOCKED` in this board with a note FIRST and coordinate.
- **Claim before you code** (§1.4) — prevents two instances starting the same task.
- **One commit per task, pushed immediately** — worst case is a clean rebase, never lost work.
- **v1 API contract (§11) is FROZEN** — frontend (Cline#2) and protocol (Cline#3) build against it
  without touching Cline#1's code.
- `TASKS.md` itself: only Cline#1 (the orchestrator) rewrites structure; every instance edits **its
  own rows** (status/Done notes) only.

## 4. File ownership map
| Path (repo-relative) | Owner | Notes |
|---|---|---|
| `AIPlayerEngine/src/main/java/com/aiplayer/examples/**` | **Cline#1** | `FleetPlay.java`, `BotInfo.java` |
| `AIPlayerEngine/src/main/java/com/aiplayer/web/**` | **Cline#1** | `DashboardApi.java` + new web modules |
| `AIPlayerEngine/src/test/java/com/aiplayer/web/**` | **Cline#1** | API tests |
| `AIPlayerEngine/src/main/java/com/aiplayer/protocol/**` | **Cline#3** | `PacketLogger.java` + new parsers |
| `AIPlayerEngine/src/test/java/com/aiplayer/protocol/**` | **Cline#3** | packet tests |
| `AIPlayerEngine/src/main/resources/dashboard/**` | **Cline#2** | `index.html` + ALL new dashboard assets |
| `AIPlayerEngine/src/main/java/com/aiplayer/engine/**` | Cline#1 (shared) | others only with a board note |
| `Documentation/**` (except this file) | **Cline#4** | new docs / audits / how-tos |
| `scripts/**` | **Cline#4** | new bash tools |
| `README*` (root + engine) | **Cline#4** | doc edits |
| `TASKS.md`, `START_HERE.md` | **Cline#1** | structural rewrites |

---

## 6. ⚠️ TIM-001 — HIGH PRIORITY: bots look static — movement/quest/combat deep review
`Status: TODO` · `Owner: Cline#1 (lead)` · `Evidence tools: WPT-11 (Cline#2) + WPT-21/30 (Cline#3)`
`Detail spec: Documentation/PRIORITY_TASKS.md` · **Do not resolve without the evidence checklist below.**

- **Symptom (operator):** bots appear NOT to move on the map — coordinates look static; level 20/22
  exists yet no movement/quest/combat progression is visible.
- **Known facts:**
  1. Levels were **SEEDED** — chars inserted with `exp=1400000` → server auto-places them L20–22 at
     login. Levels alone prove nothing about gameplay.
  2. Live data DID show real short-range motion (`-82759,250149 → -82634,249894`, heading via
     `ValidateLocation`) + wolf `DELETE_OBJECT` kills → some combat happened.
  3. NO proactive gameplay: only 8s ±900-unit wander + server auto-follow; **quest-NPC navigation
     (workstream W5) is not implemented**; the brain issues attacks, not movement goals.
  4. Map view **auto-fits** bounds → small/local motion can look static on screen.
- **Evidence checklist (verify in order, paste output into `Done notes`):**
  - [x] H1: do `MoveToLocation(0x01)` frames actually move the char server-side? — **PROVEN live 2026-08-11 by Cline#4** (`MoveProbe` vs live server): CombatBot_03 DB `x` `-81804 → -81404` (exactly +400u, persisted); server confirms `CHAR_MOVE_TO_LOCATION=8`, `VALIDATE_LOCATION=1`. Opportunity: short wander + map auto-zoom makes motion look static; movement frames themselves WORK. Log: `Documentation/RuntimeLogs/2026-08-11-cline4-ops-jdk25-relaunch-tim001-baseline.md` §6.
  - [ ] H2: wander destinations degenerate (stale/zero coords) or masked by map auto-zoom?
  - [ ] H3: bots only ever chase nearby hostiles (auto-follow), never "travel"?
  - [ ] H4: DB spawn pos vs live pos mismatch (chars inserted at (-82759,250149))? — *partial baseline captured 2026-08-11 (all 5 bots currently at B4 zone, none at seed); live teleport test still open*
  - [ ] H5: organic XP/level-up confirm (XP gained over time, not just seeded 1.4M)? — *partial live data 2026-08-11 (Cline#4): 45s CombatLoop window = real server-confirmed damage (wolf HP 90→70) + 3 DELETE_OBJECTs, but exp stayed 1,400,000 — kill credit unproven yet; a longer fleet run / L1 char still needed. Log §8.*

---

## 7. Phase A — API & infra (owner **Cline#1**)
| ID | Task | Prio | Deps | Status |
|---|---|---|---|---|
| **WPT-01** | REST API redesign — `/api/v1/*` (bots/entities/landmarks/events/health/config) + `DashboardApi.java` extracted from `FleetPlay` | P0 | — | **IN_PROGRESS (Cline#1)** |
| **WPT-02** | SSE push deltas per bot (replaces index.html 2s poll; changed fields only) | P1 | WPT-01 | TODO |
| **WPT-03** | Event ring buffer + `/api/v1/events` typed events (kill, level-up, skill-cast, damage, move, connect/disconnect) | P0 | WPT-01 | TODO |
| **WPT-04** | State history ring (snapshot every few s; optional CSV export) → trails/playback/review | P0 | WPT-01 | TODO |
| **WPT-05** | `/api/v1/config` — tunable fleet size, wander radius, poll interval, per-bot overrides | P1 | WPT-01 | TODO |
| **WPT-06** | Dashboard API test suite (endpoint smoke, JSON schema, SSE framing) | P1 | WPT-01 | TODO |
| **WPT-07** | Access hardening — loopback default, `--bind` flag, token auth on mutating endpoints | P2 | WPT-01 | TODO |
| **WPT-08** | Health/metrics — uptime, reconnect counts, pktAge history, request latency | P1 | WPT-01 | TODO |

### Spec notes (acceptance)
- **WPT-01**: new `com.aiplayer.web.DashboardApi` owns serialize+routing; `FleetPlay.startDashboard`
  only registers "**/**" (SPA) + legacy `/json` `/report`; every v1 GET returns `application/json`,
  well-formed (test curl + JUnit); legacy `/json` payload unchanged for the SPA.
- **WPT-03**: in-memory ring (cap ~512), in-order timestamps, idempotent replay; `/api/v1/events?since=<seq>`.
- **WPT-04**: in-memory cap ~3600 snapshots (~1h @2s); `/api/v1/history?bot=&from=&to=`; CSV export flag.
- **WPT-05**: JSON in/out, applied live via volatile fields; UI in WPT-17.
- **WPT-06**: `com.aiplayer.web.DashboardApiTest` grows: every endpoint, JSON keys, SSE frames, 404s.
---

## 8. Phase B — Dashboard UX / core views (owner **Cline#2**, frontend only — index.html + assets)
| ID | Task | Prio | Deps | Status |
|---|---|---|---|---|
| **WPT-09** | World map data source — datapack/geo-derived region polygons + landmarks → `dashboard/data/*.json` | P0 | — | TODO |
| **WPT-10** | Map renderer v2 — pan/zoom + terrain layer over real coords | P0 | WPT-09 | TODO |
| **WPT-11** | **Movement trails** — per-bot polyline last-N positions (evidence for TIM-001) | P0 | WPT-04 | TODO |
| **WPT-12** | Playback / replay mode — scrub time window of recorded state | P1 | WPT-04 | TODO |
| **WPT-13** | Grid v2 — sortable columns, filters (state/online), CSV export | P1 | WPT-01 | TODO |
| **WPT-14** | Live event feed panel — kills, level-ups, damage, skill casts, chat (color-coded) | P0 | WPT-03 | TODO |
| **WPT-15** | Bot detail drawer — gear/items/stats/XP-HP timeline **+ per-metric sparklines** | P1 | WPT-01 | TODO |
| **WPT-17** | Fleet control panel — pause/resume/focus + "send to landmark" | P1 | WPT-05 | TODO |
| **WPT-18** | Alerts/notifications — death, disconnect, level-up, server-down badge/sound | P2 | WPT-08 | TODO |
| **WPT-19** | Filter/follow/search/highlight + pin camera | P2 | WPT-10 | TODO |
| **WPT-20** | Responsive + hotkeys (M/G/D) + theme toggle | P2 | — | TODO |
| **WPT-31** | Frontend modularization — split SPA, minify, cache-busting | P1 | — | TODO |

### Spec notes
- Frontend reads ONLY `§11` v1 contract (frozen) + legacy `/json`. Do not require server code changes.
- WPT-09: no official tiles — derive real-coordinate region polygons from the datapack geo/regions;
  acceptable fallback = schematic + pan/zoom + region labels.
- WPT-16 (sparkline charts) is **folded into WPT-15** — id kept only for traceability; the drawer shows
  HP/MP/EXP per-bot sparklines over the session.

---

## 9. Phase C — telemetry depth (owner **Cline#3**, protocol only)
| ID | Task | Prio | Deps | Status |
|---|---|---|---|---|
| **WPT-21** | Movement-ack telemetry → "STAGNANT" badge on non-moving bots (TIM-001 evidence) | P0 | WPT-03 | TODO |
| **WPT-22** | SystemMessage/Chat parser → real server messages & NPC dialogue | P0 | — | TODO |
| **WPT-23** | StatusUpdate full attr map — stream EXP/SP/level/HP changes in real time | P1 | — | TODO |
| **WPT-24** | Inventory v2 — full ItemList (equipped vs loose) + datapack names | P1 | — | TODO |
| **WPT-25** | Damage/combat KPIs — hits, damage, DPS, kills per bot | P1 | — | TODO |
| **WPT-26** | Skill-cast metering — MagicSkillUse ids→names, casts, cooldown | P1 | — | TODO |
| **WPT-27** | Quest telemetry — quest-list packets + quest log (aligns W5) | P1 | — | TODO |
| **WPT-28** | Chat manager — parse incoming + engine responds; `/api` view | P2 | WPT-22 | TODO |
| **WPT-29** | Entity name resolution — datapack npc names instead of `mob#id` | P1 | — | TODO |
| **WPT-30** | Position cross-check vs `gameserver.characters` every N min; drift alert (TIM-001) | P0 | WPT-03 | TODO |

### Spec notes
- New parsers live in `com.aiplayer.protocol` + emit into the **frozen v1 contract** (`§11`): telemetry
  that needs new fields → add them as OPTIONAL keys (must not break the SPA) or surface via `/api/v1/events`.
- WPT-21/30 are the TIM-001 evidence instruments — HIGH.

---

## 10. Phase D — ops, scale, polish (owner **Cline#4**, no Java-server edits)
| ID | Task | Prio | Status |
|---|---|---|---|
| **WPT-32** | Server-side observability view — own file `dashboard/ops.html` polling `/api/v1/health` + DB/port checks | P2 | **DONE-PUSHED (Cline#4)** — `AIPlayerEngine/src/main/resources/dashboard/ops.html` created: polls `/api/v1/health` + `/api/v1/bots` (TIM-001 stagnant detector: STAGNANT badge when no ≥10u movement across consecutive polls) + `/api/v1/events` + `/api/v1/config`; JS syntax-validated. DB/port truth stays in `scripts/server_health.sh` (browser cannot query the DB — page links it). |
| **WPT-33** | Final polish & docs — README "Web Panel" section, favicon asset, i18n stub `en.json`, `scripts/e2e_dashboard.sh` | P2 | **DONE-PUSHED (Cline#4)** — README "Web Panel" section (routes, ops, JDK25 note); `dashboard/favicon.png` (real 16×16 PNG); `dashboard/i18n/en.json` (valid stub); `scripts/e2e_dashboard.sh` (assets + live /api/v1 contract check + host health; E2E_EXIT=0 live). |
| **WPT-34** | Ops tooling — `scripts/server_health.sh` (ports 2106/7777/8080 + `gameserver.characters` ping + LN counts) | P2 | **DONE-PUSHED (Cline#4)** — live-verified 2026-08-11: server UP (2106/9014/7777), DB pings OK, 5 ai_% chars at B4 zone, EXIT=0. JDK25 discovered at `~/.jdk/jdk-25.0.4+7` (JARs are class 69; system JDK21 can't run them). |

### Spec notes
- Cline#4 touches ONLY `Documentation/**`, `scripts/**`, and NEW dashboard asset files (`ops.html`,
  `i18n/en.json`, `favicon.png`). Any `<link>`/route change to the shared SPA → coordinate with
  Cline#2 / Cline#1. WPT-32/33/34 are the "operations corner" — zero interaction with fleet code paths.

---

## 11. Frozen v1 API contract (Cline#1 implements EXACTLY this; Cline#2/#3 depend on it)
`GET /api/v1/bots` → `{"bots":[{"account","charId","name","level","exp","sp","hp","hpMax","mp","mpMax",
"cp","cpMax","x","y","z","heading","load","maxLoad","weapon","adena","invPct","itemCount",
"items":[[id,count]...],"mobs","npcs","ents":[[objId,kind,x,y,z]...],
"target":{"objId","kind","label","x","y","z","d"} | null,
"action","thought","state","online","uptimeSec","pktAgeMs","lastSeenMs"}]}`
`GET /api/v1/entities` → `{"entities":[{"objId","kind","label","x","y","z"}]}` (merged, deduped)
`GET /api/v1/landmarks` → `{"towns":[{"name","x","y","z"}]}` (real coords)
`GET /api/v1/events` → `{"events":[{"seq","t","type","bot","data":{...}}]}` (WPT-03 fills; empty now)
`GET /api/v1/health` → `{"status":"ok","uptimeSec","botCount","onlineCount","startedAtEpochMs","routes":[...]}`
`GET /api/v1/config` → `{"fleetSize","wanderRadius","wanderIntervalMs","pollMs","bind","tokenAuth":false}`

## 12. Board changelog (append-only)
- 2026-08-10 · Cline#1: created board; moved PRIORITY_TASKS detail → TIM-001 (detail kept in
  `Documentation/PRIORITY_TASKS.md`); registered the 33 WPT tasks (WPT-16 folded into WPT-15) + froze
  the v1 contract (§11).
- 2026-08-10 · Cline#1: WPT-01 → IN_PROGRESS (this session).
- 2026-08-11 · Cline#4: ops corner done — WPT-34 `scripts/server_health.sh` (live EXIT=0, `4ed840e1`),
  WPT-32 `dashboard/ops.html` + TIM-001 STAGNANT detector (`bf2ce3db`), WPT-33 README/favicon/i18n/e2e
  (`fd9581d2`). **Server relaunched on JDK25** (`~/.jdk/jdk-25.0.4+7`, PATH wrapper) — was DOWN at start;
  evidence baseline in `Documentation/RuntimeLogs/2026-08-11-cline4-ops-jdk25-relaunch-tim001-baseline.md`.
- ⚠️ Coord note: `dashboard/ops.html` exists as a resource but needs a server route (`/ops.html`) — Cline#1
  to add when wiring the v1 API (WPT-01/02). DB creds for ops tooling: `l2j`/`StrongPasswordHere`
  (`ServerBuild/*/config/Database.ini`) — `real_status.sh` still assumes sudo-root (Cline#4 flagged, didn't touch).
- **WPT-07**: `--bind 127.0.0.1` default; token via `--token`; 401 on unauth POST.
- **WPT-08**: counters in `DashboardApi`; `/api/v1/health` extended; feed alert (WPT-18) hooks here.
## 5. Right-now kickoff (start here, in order)
| Instance | Starts with | Then (deps satisfied → order flexible) |
|---|---|---|
| **Cline#1 (orchestrator)** | **WPT-01 — IN PROGRESS** | WPT-02, 03, 04, 05, 06, 07, 08 |
| **Cline#2 (frontend)** | WPT-09 → WPT-10 → WPT-13 → WPT-20 | 15, 16, 18, 19, 12, 14, 31 |
| **Cline#3 (protocol)** | WPT-22 → WPT-29 → WPT-23 → WPT-25 → WPT-26 | 27, 28, 24, 21, 30 |
| **Cline#4 (ops/docs)** | WPT-32 → WPT-33 → WPT-34 (`ops.html`, README/e2e, `server_health.sh`) | non-Java, zero conflicts |