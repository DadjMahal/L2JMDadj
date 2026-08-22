# ✅ REVIEWED_TASKS — Consolidated master registry (every task, every session)

> **Purpose:** a single, stable record of **everything that has been DONE or is in flight**, so no
> future session (or parallel Cline instance) re-does work. This is a **read-only registry** — status
> changes are made on the live board `Documentation/TASKS.md` and mirrored here at session end.
>
> **Live board (source of truth for OPEN work):** `Documentation/TASKS.md` (only TODO/IN_PROGRESS
> items live there). This file records final/known states.
>
> Rebuild/update me at the **end of every session** (leave cleaner than found).

---

## How to use (so we never redo work)
1. **Before starting anything**, check this file + `Documentation/TASKS.md` (open work): if a task
   is listed `✅ DONE` with a commit hash, **it is done — do not redo it**; pick an open board task.
2. All **33 WPT web-panel tasks are DONE** (§A). **TIM-001 is RESOLVED** (§B). Sessions S1–S10 and
   UpgradePlan Wave 1 are complete (§E). Only the rows on the live board `TASKS.md` are open.
3. Every row keeps: `status · commit-hash · evidence/notes` so an auditor can re-verify instantly.
4. When a board row completes, move it here (§E) — the live board holds OPEN work only.

---

## §A — Web Player Telemetry (WPT): 33/33 DONE-PUSHED  ✅
All WPT work is **complete and committed on `master`**. WPT-16 was folded into WPT-15 (traced).

### Phase A — API & infra (owner Cline#1)
| ID | Deliverable | Status | Commit(s) |
|---|---|---|---|
| WPT-01 | REST `/api/v1/*` redesign + `DashboardApi` extracted | ✅ DONE | `7b5d5a01` |
| WPT-02 | SSE push deltas per bot (`/api/v1/stream`) | ✅ DONE | `5f5715ac` |
| WPT-03 | Event ring + `/api/v1/events` | ✅ DONE | `00f92e24`+`87eb6510` |
| WPT-04 | State history ring (trails/playback/CSV) | ✅ DONE | `87eb6510` |
| WPT-05 | `/api/v1/config` + live POST | ✅ DONE | `5f5715ac` |
| WPT-06 | Dashboard API test suite | ✅ DONE | `87eb6510` |
| WPT-07 | Access hardening (loopback/`--bind`/token) | ✅ DONE | `5f5715ac` |
| WPT-08 | Health/metrics (uptime/reconnect/pktAge/latency) | ✅ DONE | `00f92e24`+`87eb6510` |

### Phase B — Dashboard UX (owner Cline#2)
| ID | Deliverable | Status | Commit(s) |
|---|---|---|---|
| WPT-09 | World map data (regions.json/landmarks.json) | ✅ DONE | `2b658022` |
| WPT-10 | Map renderer v2 (pan/zoom) | ✅ DONE | `2b658022` |
| WPT-11 | Movement trails (TIM-001 evidence) | ✅ DONE | `66c67487`+`32266bb9` |
| WPT-12 | Playback / replay mode | ✅ DONE | `9fc361b4` |
| WPT-13 | Grid v2 (sort/filter/CSV) | ✅ DONE | `2b658022` |
| WPT-14 | Live event feed panel | ✅ DONE | `2b658022`+`d811c512` |
| WPT-15 | Detail drawer + sparklines (**WPT-16 folded**) | ✅ DONE | `1018fc0e` |
| WPT-17 | Fleet control panel (config tuner) | ✅ DONE | `595a59b5` |
| WPT-18 | Alerts/notifications | ✅ DONE | `1018fc0e` |
| WPT-19 | Filter/follow/search/highlight + pin-camera | ✅ DONE | `595a59b5` |
| WPT-20 | Responsive + hotkeys + theme | ✅ DONE | `2b658022` |
| WPT-31 | Frontend modularization + build bundle | ✅ DONE | `2b658022` |

### Phase C — Protocol telemetry (owner Cline#3)
| ID | Deliverable | Status | Commit(s) |
|---|---|---|---|
| WPT-21 | Movement-ack → `/telemetry` (STAGNANT badge) — **VERIFIED LIVE** | ✅ DONE | `e2eaa6a4` |
| WPT-22 | SystemMessage/Chat parser | ✅ DONE | `e09530b7`+`d811c512` |
| WPT-23 | StatusUpdate full attr map | ✅ DONE | `68945a94` |
| WPT-24 | Inventory v2 + datapack names | ✅ DONE | `e09530b7` |
| WPT-25 | Damage/combat KPIs | ✅ DONE | `68945a94` |
| WPT-26 | Skill-cast metering | ✅ DONE | `68945a94` |
| WPT-27 | Quest telemetry (QUEST_LIST 0x80) | ✅ DONE | `514f05c5` |
| WPT-28 | Chat manager view | ✅ DONE | `595a59b5` |
| WPT-29 | Entity name resolution | ✅ DONE | `e09530b7` |
| WPT-30 | Position crosscheck vs DB (`position_crosscheck.sh`) | ✅ DONE | `2a753645` |

### Phase D — Ops & docs (owner Cline#4)
| ID | Deliverable | Status | Commit(s) |
|---|---|---|---|
| WPT-32 | `ops.html` observability + STAGNANT badge | ✅ DONE | `bf2ce3db` |
| WPT-33 | README/favicon/i18n/e2e | ✅ DONE | `fd9581d2` |
| WPT-34 | `server_health.sh` | ✅ DONE | `4ed840e1` |

---

## §B — TIM-001 (HIGH) — movement/quest/combat deep review  ✅ **RESOLVED 2026-08-13 (all H1–H5 proven)**
- **Overall status:** `RESOLVED` — live `gameserver.characters` evidence now proves every hypothesis.
- **Hypotheses evidence (live runs 2026-08-13):**

| H | Question | Verdict | Evidence |
|---|---|---|---|
| H1 | `MoveToLocation(0x01)` frames move the char server-side (+persist) | ✅ **PROVEN** | All 5 bots moved and their DB positions persisted (~3.4–5k u deltas, e.g. CombatBot_02 `(-82500,250200) → (-82267,245934)`). Short-hop + far-hop + persistence all shown. |
| H2 | Destinations degenerate (stale/zero) | ✅ NO | `EVIDENCE-H2 degenerateDestinations=0 / 5`. |
| H3 | Bots never proactively travel | ✅ PROVEN | Zone-routed far hops issued + landed + persisted for all 5 during the 4-min run. |
| H4 | DB spawn vs live pos mismatch | ✅ baseline | BEFORE/AFTER diff proves DB tracks live movement (not an engine defect). |
| H5 | Organic XP / real leveling | ✅ **PROVEN** | Fixed 2 live combat-engagement bugs (stale `AIPlayer` position; ATTACK `targetObjId=0` → planner emitted no frames) → fleet lands real kills. Live farm run: all 5 bots gained real kill XP **+210 / +437 / +141 / +175 / +465** from the 2884 L5 baseline in `gameserver.characters.exp`. |

- **Resolved-by:** two engine fixes in `FleetPlay` (per-tick `player.setPosition(...)` so
  `detectNearbyEnemy` uses the real live position, and passing `getSelectedTargetObjId()` to
  `executeCombat` so the planner emits Action/AttackRequest) + regression test
  `CombatFramePlannerTest.testPlainAttackDecisionWithResolvedObjIdProducesFrames`. Suite **223/223 green**.
- **Evidence:** `Documentation/_archive/RuntimeLogs/2026-08-13-tim001-h1-h5-resolved.md`.

---

## §C — Supporting tooling / audits shipped (not board tasks) 
| Item | Type | Note |
|---|---|---|
| `scripts/tim001_move_probe.sh` | Evidence harness | Snapshot DB → run fleet (movement FORCED ON) → curl `/telemetry`+`/json` → re-snapshot → H1..H5 verdict block. Now also prints per-hop `HOP-PROOF` + `DB-DELTA VERDICT` (2026-08-12/13). |
| `scripts/reset_fleet_xp.sh` | H5/XP tool | `status`/`baseline` read-only; `reset` **prints SQL only, never executes**. |
| `scripts/position_crosscheck.sh` (WPT-30) | Ops | Live-pos vs DB drift checker. |
| `scripts/server_health.sh` (WPT-34) | Ops | Ports/procs health gate. |
| `dashboard/ops.html` (WPT-32) | Ops UX | STAGNANT badge for non-moving bots. |
| Audit/44 | Movement review | Short-hop +400u proven. |
| Audit/45, 45a, 45b | Persistence findings+proof | 9900u cap; single far HOP dropped; short-hops are the fix path. |
| Audit/46 | Test-coverage gap | **P0 RESOLVED `2b010086`** — MoveTelemetry honesty test (5 sent/3 server-moved/2 degraded) + HopGate.nextAction pure helper (send/advance/resend) extracted from FleetPlay and unit-tested. P1 items 3-5 still open. |
| Audit/47 | Stub-class inventory | Static inventory of stub classes. |
| Audit/48 | Quest-navigation plan | **Stage A SHIPPED** (this session) — `QuestNpcNavigator` routes a bot to a quest-giver/turn-in NPC via ack-gated `ZoneRouter.buildHops()` (≤4800u) + `HopGate` send/advance/resend + stuck abandonment; target resolution from `QuestDatabase` TALK/RETURN steps and `QuestProgressTracker` quest state. 12 unit tests green. Stages B (talkToNpc 0xb0), C (bypass 0x21), D (quest-loop goal planning) remain future. |
| Audit/49 | Organic-XP (H5) proof plan | Pairs movement with a verified kill path; caveat documented. |
| Audit/50 | Secret hygiene | Static review of credentials in repo. |

---

## §D — Session review ledger (append-only; newest last)
- **2026-08-10 … 2026-08-12** — WPT 33-task build completed (Phase A/B/C/D), 213 tests green; live
  fleet + dashboard verified on JDK25. See board changelog history below (§E.4).
- **2026-08-12** — TIM-001 evidence run #1: far-travel attempted, single far HOP not persisted
  (9900u cap); `/telemetry` route added + verified live; probe defaults fixed.
- **2026-08-13** — TIM-001 evidence run #2 (fresh, 5-bot, movement FORCED ON):
  engine rebuilt with `ZoneRouter.buildHops()` short-multi-hop; full suite green **218/218**.
  Live outcome is **honest-negative** for persistence: `movesSent` barely emitted, `serverMoved=0`,
  `gameserver.characters` identical before/after, `expGained=0`. Doc-sync done in the 2026-08-13 commit.
  **TIM-001 remains OPEN** (next: wire hop-gate as primary idle behavior + re-probe).

> **Status:** everything in §A/§C/§E below is **done** — do not reopen. The only open work lives
> on the board `Documentation/TASKS.md`.

---

## §E — TASK BOARD HISTORY (moved off the live board 2026-08-22)
> The 100-task roadmap (sessions S1–S10, grounded in the code review of 95 `MODE:PARTIAL` files
> and the 50-bot live run) plus the pre-board STEP era. Statuses preserved verbatim.
> Still-open rows (S3-T02, S3-T03) live on the board `Documentation/TASKS.md`.

### E.1 Sessions 1–10 (2026-08-17 board restructure → 2026-08-19)
| Session | Scope | Outcome |
|---|---|---|
| S1 | Code hygiene & foundations | 10/10 DONE (wildcards→explicit 910a2812, stubs+lint b8879263, PARTIAL index+Spotless 945344cc, tunables e4211337/b8879263, style_sweep 87bf5a4b) |
| S2 | Protocol & data hardening | 10/10 DONE (LoginCrypt d653ab1e, 50-session keys 6ce4b56d, NPC_INFO/QUEST_LIST 5b56a768, packet metrics+events 408a3b4c, CharSelectInfo 66462e9c, reconnect backoff+keepalive 25f071df, version guard 2e04ba69) |
| S3 | Quest pillar | 8/10 DONE (LIVE quest accept 90863993 — quest 6 journal [[6,1]]; dialogs/turn-in primitives locked by QuestDialogTest/QuestChainPlannerTest; varietySeed a21ab79e; cooldown b72f182e; T02 objective progress + T03 turn-in remain OPEN on the board) |
| S4 | Guide map & race balance | 10/10 DONE (per-race newbie fields + huntZones + vendor anchors + BFS route c78d4841; anchor tests b72f182e; cache 7def0ed0; proximity gate a9834de6) |
| S5 | Movement & relocation | 10/10 DONE (walkable anchors + HopGate ack-gating 36645b2a — live 100% hop-success, 0/50 stalled; nudge/walkability/race-radius/drift-watch dbfa34df; telemetry+anti-oscillation 06ae36d5) |
| S6 | Combat & survival | 10/10 DONE (USE_SKILL fallback test 99dc335f; level-scaled budgets, retreat-heal, death handling, aggro cap, death-loop a3b3f04c; potions+starter gear 1c044c5d/39ca4cce) |
| S7 | Town & economy | 10/10 DONE (RestockPlanner orchestration; BuyManager/SellManager/Warehouse/Teleport d2c7dc55; vendor/adena/class-restock tests 68fac73e) |
| S8 | Fleet coordination | 10/10 DONE (FleetSpreadPlanner pickAnchor tested + wired; party formation/loot 5a44db8c; per-race telemetry) |
| S9 | Monitoring & ops | 10/10 DONE (watcher+provisioning+launcher 2aeae3de; dashboard race filter + KPIs fdf20296; log rotation/auto-restart/backup/health 25f071df) |
| S10 | Legacy cleanup | 10/10 DONE (wire-or-archive decision executed; engine inventory + dead imports + probes c4bff0ec; PARTIAL→COMPLETE a3aa3f06; no Redis/Postgres 3efd0c82; final docs sync) |

### E.2 UpgradePlan Wave 1 (2026-08-20/22; program: `Documentation/UpgradePlan/README.md`)
| ID | Task | Status |
|---|---|---|
| UP-EP-1 | Archive dead engine classes to attic/ | DONE-PUSHED 4827ac0f |
| UP-EP-2 | Relocate live engine classes; remove engine/ | DONE-PUSHED 9601dd77 |
| UP-EP-3 | Rename phase0/* → behavior/* domain packages | DONE c049e612 |
| UP-EP-4 | Split FleetPlay god class (FleetConfig/BotSession/DashboardBoot) | DONE 2b4cda1b |
| UP-EP-5 | Merge micro-packages | DONE 5e61be6b |
| UP-EP-6 | Security pass (creds → fleet_env.local, DASH_TOKEN) | DONE 8bacb021 |
| UP-EP-7 | Virtual threads (BotSession fleet + gs-readers + dashboard) | DONE 933204d2 |
| UP-EP-8 | Docs unification round 1 | DONE ac742a27 |
| DOCS-2 | Docs consolidation round 2 — one fact one place: 28 stale files archived, WORKFLOW = canonical rules, TASKS = open work only (history → §E), AGENTS.md entry added, volodro-path scripts fixed (2026-08-22; evidence `RuntimeLogs/2026-08-22-docs-consolidation.md`) | DONE |

### E.3 Pre-board history (STEP era)
| ID | Task | Status | Owner |
|---|---|---|---|
| STEP 0 | Archive history pile + lean START_HERE/TASKS for the PLAY goal | DONE-PUSHED 7e820756 | doc-sweeper |
| STEP 1 | BotPlay controller — bots pick goals and act, never idle | DONE-PUSHED 9b0d34f6 | play-builder |
| STEP 2 | Quest accept/turn-in live loop (pure dialog driver, gated OFF by default) | DONE-PUSHED c4ee832a | play-builder |
| STEP 3 | 5-bot live run + play evidence | DONE-PUSHED 94993a25 | play-builder |
| STEP 4 | Smartness polish: death/respawn, low-HP retreat, restock intent | DONE | play-builder |
| STEP 5 | Despawned-target lifecycle verified on the P2 fixed build | DONE (verified 3d97fe53) | play-builder |
| STEP 6 | Idle-relocation empty-zone dead-end fixed | DONE-PUSHED 4d8acad5 | play-builder |
| GUIDE-MAP / GUIDE-MAP-INTEG | Per-race guide map (RaceGuide) wired into RelocationPlanner | DONE-PUSHED ce3e2426 / 4d8acad5 | play-builder |
| STEP 7 | Ultra-smart vol.1 — RestockPlanner BUY, FleetSpreadPlanner, seed-diverse quest pick | DONE-PUSHED d2c75b4b | play-builder |
| STEP 8 | Live-path cleanup vol.1 + review/roadmap | DONE-PUSHED b08e204f | play-builder |
| LIVE-RUN | 50 random-race players created + played 2h | DONE-PUSHED 0fd3fef4/e53ca85a | play-builder |

### E.4 Board changelog (narrative, newest last; 2026-08-17 → 08-19)
- **2026-08-19 · play-builder:** **🎯 S3-T01 DONE — LIVE QUEST ACCEPT PROVEN (the ONE-goal pillar).**
  A real bot walked to ROXXY (30006), navigated the real server dialog (menu → Script → quest-accept
  bypass `Script Q00006_StepIntoTheFuture 30006-03.htm`), and the server recorded it:
  **QUEST_LIST total=0 → total=1, active=1, list=[[6,1]]** (quest 6 "Step Into The Future").
  New behaviors: configured-giver routing (ACQUIRE routes to the configured quest NPC), dialog fires
  within talkRange. Suite **383 green**.
- **2026-08-19 · play-builder:** **Quest pipeline LIVE-PROVEN through the giver dialog.** Fixed quest-data
  bug (40001's giver wrong zone — real giver "Jackson" 30002, Talking Island); quest-priority behavior
  (within 5k of quest NPC: route to it, no combat stealing, hold while dialog open). Live probe: clicked
  ROXXY, extracted 5 bypass links, attempted the validated bypass. Remaining blocker: multi-step quest UI
  (menu → quest list → accept) — solved by S3-T06 QuestDialogDriver. **383/383**.
- **2026-08-19 · play-builder:** **S5-T01 LIVE-PROVEN FIX — the keystone.** Root cause: random far-point
  relocations landed on unwalkable terrain → server ActionFailed → 0% hop-success → freeze. Fix:
  `RelocationPlanner` prefers REAL hunt-zone anchors. Live: **hop-success 100% on all 50 bots, 0/50
  stalled**, 29 ATTACK/16 chase, **422 learning-kills/min**. **383 green**.
- **2026-08-19 · play-builder:** **TOP-NOTCH AI — live learning wired.** Every real kill feeds
  `onKill(xp) → ReinforcementEngine.rewardKill → AdaptiveLearner → DeepLearning` (+emotions; 200+
  learning events/min, EXCITED observed). Hop-success telemetry live on the dashboard.
- **2026-08-19 · play-builder:** **S5 root-cause** (from `MoveToLocation.java`): server rejects moves
  with `ActionFailed` when player `isOutOfControl()` (mob CC); 9900-distance check is server-side.
  Engine fix: on route-abandon with hostiles near, hold for CC/regen instead of churning re-plans. **382 green**.
- **2026-08-17 · play-builder:** **Sessions 1 & 9 complete.** S1 hygiene (wildcards over 99 files, stubs,
  imports, PARTIAL index, tunables, .editorconfig); S9 ops (watcher+xp/min fix, provisioning/launcher,
  log rotation, keep-alive, DB backup, health check). Live: `health_check.sh` → **OK: 50/50**.
- **2026-08-17 · play-builder:** S2-T04/05/07/08/09 — packet health, CharSelectInfo decode (+3 tests),
  reconnect backoff, keepalive/TCP_NODELAY, watcher JSON. 50 chars persisted, avg L3.9/max L6,
  ~119.5k total XP, 0 crashes. Post-run: `/tmp` watcher notes lost with the machine; server reverted
  DB race/class to Human (missing `character_subclasses` rows) — true random race needs subclass provisioning.
- **2026-08-17 · doc-sweeper/play-builder:** Board restructure — 100-task roadmap grounded in the code
### E.5 — 100-task board era (2026-08-22+, fewer rows: DONE only; open rows live on `TASKS.md`)
| ID | Task | Status |
|---|---|---|
| F-01 | Root `Architecture.md` — the core basis doc | DONE (2026-08-22 restructure, `010f7e9d`) |
| F-02 | `START_HERE.md` §0.5 core basis + doc-map link | DONE (2026-08-22 restructure, `010f7e9d`) |
| F-03 | AIPlayerEngine/README.md package diagram sync + Architecture links | DONE-PUSHED 38aefc90 |
| F-04 | Root `README.md` project header + START_HERE/Architecture pointers (owner's note kept) | DONE-PUSHED f3982ad9 |
| F-07 | Golden gate `scripts/gate.sh` (tests+style+secret-lint, offline) + style-path fix, ws trim | DONE-PUSHED d4e3407b |
| F-05 | Documentation/README live-docs index (+Architecture.md row) | DONE (reviewed 2026-08-22, `0c835e9b`) |
| F-06 | MODE_PARTIAL_INDEX refresh (EP-8, 95 files) | DONE (reviewed 2026-08-22) |
| F-08 | Baseline suite report (STATUS.md pinned, 415 verified) | DONE (reviewed 2026-08-22) |
  review + 50-bot live logs. `mvn test` **345 green**; 50-bot fleet + 2h watcher live.
