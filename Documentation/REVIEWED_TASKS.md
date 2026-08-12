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
1. **Before starting anything**, check this file + `TASKS.md`: if a task is listed `✅ DONE` with a
   commit hash, **it is done — do not redo it**; instead pick an *open* item below or add a new board task.
2. All **33 WPT web-panel tasks are DONE** (Phase A/B/C/D). **TIM-001 is genuinely OPEN** (see §B) —
   do not mark it resolved without a real DB position/XP delta.
3. Every row keeps: `status · commit-hash · evidence/notes` so an auditor can re-verify instantly.

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

## §B — TIM-001 (HIGH) — movement/quest/combat deep review  ⚠️ **OPEN — do NOT resolve without evidence**
- **Overall status:** `IN PROGRESS` (fine-grained on live board §6). Evidence instruments shipped,
  live run executed, but **movement persistence + organic XP still UNPROVEN**.
- **Hypotheses evidence tick (live runs 2026-08-12 and 2026-08-13):**

| H | Question | Verdict | Evidence |
|---|---|---|---|
| H1 | `MoveToLocation(0x01)` frames move the char server-side (+persist) | ⚠️ **PARTIAL / NOT PERSISTED** | Short hop +400u **proven** (Audit/44). Far **single** HOP (~21k u) exceeds the server **9900u per-move cap** (`SourceCode/.../clientpackets/MoveToLocation.java:156-163`) → dropped. **2026-08-13 fresh run: `gameserver.characters` x/y/z IDENTICAL before/after**; `/telemetry` `serverMoved=0` for all 5 bots. |
| H2 | Destinations degenerate (stale/zero) | ✅ NO | 0 degenerate; far ~21k u dests generated. |
| H3 | Bots never proactively travel | 🟡 ATTEMPTED | Travel intent emitted; landed-position persistence is the open gap. |
| H4 | DB spawn vs live pos mismatch | 🟡 baseline | BEFORE==AFTER (no live delta to compare since static). |
| H5 | Organic XP / real leveling (not the seeded 1.4M) | ❌ NOT SHOWN | `expGained=0`; `exp` static at ~1.38M across the window. |

- **Root cause + fix direction (agreed):** the engine's `ZoneRouter` plans far routes, but a far goal
  must be walked as a **sequence of hops each ≤ `MAX_HOP_DIST=4800`u** (server cap 9900u). That
  short-multi-hop logic is **shipped + unit-tested** (`AIPlayerEngine/.../phase0/movement/ZoneRouter.java`
  — `buildHops()` split; `ZoneRouterTest` 7 tests). **What's left:** the *live* fleet must actually emit
  enough accepted hops to prove a nonzero DB position/XP delta (`tim001_move_probe.sh` evidence). In the
  latest 2-min run the fleet emitted only 2 moves total → not enough accepted hops yet. **Next concrete
  action:** drive the hop-gate in `FleetPlay` to send the ack-gated ≤4800u hop sequence as the bot's
  primary idle behavior, then re-run `scripts/tim001_move_probe.sh` and paste a nonzero DB delta.

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
| Audit/46 | Test-coverage gap | Remaining `MoveTelemetry`/hop-gate tests to add (P0). |
| Audit/47 | Stub-class inventory | Static inventory of stub classes. |
| Audit/48 | Quest-navigation plan | W5 quest-NPC nav not yet implemented. |
| Audit/49 | Organic-XP (H5) proof plan | Pairs movement with a verified kill path; caveat documented. |
| Audit/50 | Secret hygiene | Static review of credentials in repo. |

---

## §D — Session review ledger (append-only; newest last)
- **2026-08-10 … 2026-08-12** — WPT 33-task build completed (Phase A/B/C/D), 213 tests green; live
  fleet + dashboard verified on JDK25. See board changelog `TASKS.md §12`.
- **2026-08-12** — TIM-001 evidence run #1: far-travel attempted, single far HOP not persisted
  (9900u cap); `/telemetry` route added + verified live; probe defaults fixed.
- **2026-08-13** — TIM-001 evidence run #2 (fresh, 5-bot, movement FORCED ON):
  engine rebuilt with `ZoneRouter.buildHops()` short-multi-hop; full suite green **218/218**.
  Live outcome is **honest-negative** for persistence: `movesSent` barely emitted, `serverMoved=0`,
  `gameserver.characters` identical before/after, `expGained=0`. Doc-sync done in the 2026-08-13 commit.
  **TIM-001 remains OPEN** (next: wire hop-gate as primary idle behavior + re-probe).

> **Reminder for the next session:** the actively-open item is **§B TIM-001**. Everything in §A/§C is
> done — do not reopen WPT tasks.
