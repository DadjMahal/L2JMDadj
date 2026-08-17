# 📋 TASK BOARD — THE single source of truth for all work

> Every task for every agent lives here. Read `START_HERE.md` (repo root) first, then come back
> here to pick work. Do **not** create your own task lists anywhere else.

---

## 1. How to use
1. `git pull --rebase origin master` — always start from latest.
2. Read `START_HERE.md` (fast orientation, §0 the ONE goal).
3. Open THIS file → pick a `TODO` row you don't collide with (check `Owner` + §4 file map).
4. **Claim it**: set your row to `IN_PROGRESS (owner)` → commit → push the claim so everyone sees it taken.
5. Implement with tests. Rule: `mvn -o -f AIPlayerEngine/pom.xml test` stays fully green (**242/242**).
6. **One task = one commit**, push right after; set the row to `DONE-PUSHED <hash>` → commit → push.
7. Never leave `master` dirty. On conflict: `git pull --rebase`, resolve; if it's another owner's
   file (§4) ask before merging.

## 2. Status vocabulary
`TODO` · `IN_PROGRESS (owner)` · `BLOCKED (reason)` · `DONE-PUSHED <hash>`

## 3. LIVE BOARD
| ID | Task | Status | Owner |
|---|---|---|---|
| **STEP 0** | Archive history pile + lean START_HERE/TASKS board for the PLAY goal | `DONE-PUSHED 7e820756` | doc-sweeper |
| **STEP 1** | **BotPlay controller** — bots pick goals and act (fight/level/travel), never idle | `DONE-PUSHED 9b0d34f6` | play-builder |
| **STEP 2** | Quest accept/turn-in live loop (accept → do → turn-in) | `DONE-PUSHED c4ee832a` | play-builder |
| **STEP 3** | 5-bot live run + play evidence (fleet actually plays) | `DONE-PUSHED 94993a25` | play-builder |
| **STEP 4** | Smartness polish: death/respawn, low-HP, restock | `DONE` | play-builder |
| **STEP 5** | Despawned-target (`DeleteObject`) lifecycle: bots stuck chasing a corpse after despawn — verify recovery on the P2 fixed build | `DONE (verified 3d97fe53; fleet-wide consistency split out → STEP 6)` | play-builder |
| **STEP 6** | Idle-relocation empty-zone dead-end: a bot with no hostiles in range freezes (`movedLast60=0`) because idle far-travel MoveToLocation doesn't persist server movement — route toward last-XP / nearest mate, progressive-abandon escape gate | `DONE-PUSHED 6cd9ffaf` | play-builder |
| **GUIDE-MAP-**INTEG | Guide map wired into bot behavior: `RelocationPlanner` idles to a real `RaceGuide.idleAnchor` landmark (never the void spot) and, when frozen, re-routes to last-XP / nearest fleet mate with a consecutive-abandon escape gate | `DONE-PUSHED 6cd9ffaf` | play-builder |
| **GUIDE-MAP** | Per-race/profession guide map for the bots from real Interlude sources — profession tree (`classList.xml`), newbie Q1→Q10 + tutorial, Path Q401–418, Trial/Q235 pool Q211–235, Saga Q70–100, teleport legs + hunt zones, `idleAnchor` returns real in-world coords (fixes the void-spot idle) | `DONE-PUSHED ce3e2426` | play-builder |

## 4. File ownership map
| Path (repo-relative) | Owner | Notes |
|---|---|---|
| `AIPlayerEngine/src/main/java/com/aiplayer/phase0/play/**` | **play-builder** | new BotPlay controller (STEP 1) |
| `AIPlayerEngine/src/test/java/com/aiplayer/phase0/play/**` | **play-builder** | controller tests |
| `AIPlayerEngine/src/main/java/com/aiplayer/examples/FleetPlay.java` | **play-builder** | fleet launcher |
| `AIPlayerEngine/src/main/java/com/aiplayer/phase0/**` (rest) | **play-builder** | shared phase0 engine |
| `Documentation/**` (incl. this board) | **doc-sweeper** | docs + board upkeep |
| `scripts/**` | **play-builder** | helper tools |

- **2026-08-16 · play-builder:** STEP 5 (4/5 despawned-target stall) verified on the P2 fixed build via a 1h50m
  `FleetPlay 5 … movement` soak (`Documentation/RuntimeLogs/2026-08-16-step5-fleet-despawned-target.md`). The
  `DeleteObject` corpse-chase lifecycle is FIXED — **1606 RE_TARGET** despawn-hop transitions, 1468 organic kill-XP
  receipts, 0 server exceptions, no bot stuck on a corpse; all 5 chars accumulated large persisted XP (01
  `14941→17880`, 02 `4438→27647`, 03 `3844→25191`, 04 `3665→27918`, 05 `4170→23343`; DB flush confirms persistence;
  02/03/04/05 → L8). **New residual root (→ STEP 6):** 02/04 ended as a persistent idle-relocation dead-end —
  `AUTO_PLAY`, `target=null`, `movedLast60=0` (server never moved the char) frozen at `(-95544,246398)` /
  `(-97264,244673)` while the client kept issuing time-stamped far-point `HOP → … → hop unreachable → abandoning
  route` churn (196 fleet-wide abandons). Distinct from the despawned-target lifecycle (which is fixed): an
  out-of-hostile bot's idle far-travel does not persist server movement, so it freezes in place. STEP 5 marked
  `DONE (verified)`; fleet-wide consistency logged as **STEP 6** (route toward last-XP / nearest mate, escape gate).
  No engine change this session — build unchanged (`06906195` / `b558d4f6`).
## 5. Changelog (newest last)
- **2026-08-16 · play-builder:** **STEP 6 + GUIDE-MAP-INTEG** (`6cd9ffaf`) — new `phase0.movement.RelocationPlanner`
  fixes the empty-zone idle dead-end. When a bot's far-travel relocation is frozen (server never walks it
  toward the hop, `movedLast60=0`), it now routes **back toward the last XP-earning position** or the nearest
  **in-world fleet mate** instead of a random far point; a **consecutive-abandon escape gate**
  (`MAX_CONSECUTIVE_ABANDONS=3`) holds the bot still for 60s to break the frozen re-plan churn. Non-frozen
  idle now prefers a **real guide-map landmark** (`RelocationPlanner` → `RaceGuide.idleAnchor`, Human fleet),
  so a displaced bot always targets a real gatekeeper/town landmark — never the void `(16600,17000,434)`.
  Wired into `FleetPlay` (XP-gain remembers the spot; hop ADVANCE = progress; abandon = freeze). New
  `RelocationPlannerTest` (10 tests). Full suite **325 green, BUILD SUCCESS**. Fleet left running; fix takes
  effect on the next fleet restart (needs a rebuilt+relaunched `FleetPlay 5` soak to re-verify live).
- **2026-08-16 · play-builder:** **GUIDE-MAP** landed (`ce3e2426`) — `com.aiplayer.phase0.guide.RaceGuide`
  is the per-race/profession path map with real sourced coordinates (newbie/Path/trial/Saga chain,
  teleport legs + BFS, hunt-zone bands, `idleAnchor` for real-world idle). Every quest NPC spawn
  verified against `spawns/*.xml`; town centroids from `custom_town.xml`; saga quests registered as
  Aden-hub events. New `RaceGuideTest` (14 tests, all 315 green) validates each race's full chain
  (e.g. Human Fighter→Warrior→Gladiator→Duelist Q401/Q211/Q73) and that no registered node uses the
  void `(16600,17000,434)`. Steps 5/6 in the play lane unchanged; guide-map now offers STEP 6 a real
  idle displacement coordinate source.
- **2026-08-16 · play-builder:** STEP 5 claimed `IN_PROGRESS` — 4/5 fleet stall after `DeleteObject`
  (the STEP 3 closure follow-up). P0/P1/P2 farming fix set live-verified single-bot (sustained XP/min +
  reconnect, `06906195` + `b558d4f6`); STEP 5 re-probes the same lifecycle with the full 5-bot fleet on
  the fixed build and recovers any remaining despawned-target stall for fleet-wide consistency.
- **2026-08-13 · doc-sweeper:** archived the full historical/audit/evidence pile to `Documentation/_archive/`,
  deleted the stray team-runtime json, rewrote `START_HERE.md` + this board to be lean around the ONE goal
  (3–5 AI players that actually PLAY), `STATUS.md` = Phase: PLAY. **242/242 green.**
- **2026-08-13 · play-builder:** STEP 1 claimed `IN_PROGRESS` — BotPlay controller build starts.
- **2026-08-14 · play-builder:** STEP 1 sub-landed and pushed — 1A QuestGoalPlanner+GoalDecision value objects
  (`1dbf68e6`, 252/252), 1B BotPlayController decision ladder survive/combat/hunt/quest/rest + 11 tests
  (`df03840a`), 1C wired controller into FleetPlay idle loop via new pure ZoneRouter.routeTo (`9b0d34f6`). **265/265 green.**
- **2026-08-14 · play-builder:** STEP 2 landed — pure quest accept→do→turn-in dialog driver
  (`QuestDialogDriver`: `Script → quest-name → objective token → safe .htm/Quest` fallback; never
  re-sends, never fabricates) + `GoalDecision.questMove`/`questTargetId`/`bypass` surfaced at giver,
  `BotPlayController` emits `BYPASS` when a QUEST/ACQUIRE MOVE_TO lands within `talkRange`, and
  `FleetPlay`'s idle loop drives the live NPC dialog (click giver → read NpcHtmlMessage → send the
  single validated bypass) gated behind `phase0.quest.npcId` (off by default). 8 new tests.
  *273/273 green.* `c4ee832a`.
- **2026-08-14 · play-builder:** STEP 4 landed — death/respawn, low-HP retreat, restock. The SURVIVE
  ladder now flees the nearest hostile (`RETREAT` action + `GoalDecision.retreat()` with a Y-aware
  hop, clamped to `RETREAT_HOP=4800`, `FleetPlay` drives a single `moveTo` and logs a move event)
  instead of WAIT. Low-HP restock: `PlayContext.inventoryPct` (0-100) + configurable
  `restockThreshold` (default 100 = disabled) gates COMBAT/HUNT — returns `REST`-reason
  `"restock"` above the threshold, and FleetPlay sets the `restock` state. Death/respawn hooks
  added: `CombatAI.onDeath()`/`onRespawn(level)` called from the live loop, new
  `EventRing.TYPE_DEATH`/`TYPE_RESPAWN` feed events. 5 new tests (retreat math, hop clamp,
  restock above/below). *280/280 green.* Live: dashboard+5-bot fleet brought up on
  localhost:8080 and rendered in-browser (Login :2106, Game :7777, dash HTTP 200);
  evidence captured under `Documentation/_archive/Evidence/2026-08-14_step4-browser-live/`.
  STEP 4 runtime triggers (retreat/restock/death) did NOT organically fire this window
  (bots stayed full-HP, invPct 0, no deaths) and the fleet hit the KNOWN pre-existing
  STEP 3 stale-target stall (no kills/XP) — see evidence verification_notes.txt.
  Follow-up persisted: live-verify/force STEP 4 triggers + recover fleet from
  despawned-target stall.
- **2026-08-14 · play-builder:** STEP 3 live-run + evidence. Live 5-bot run (movement FORCED ON) on
  master exposed a stall: whole fleet 0 MoveTo / 0 XP (CombatAI engages within `target_distance=1500`
  and never lowers a far mob → never reaches the controller's movement branch; endless out-of-range
  skill spam). Fix: pure `BotPlayController.chaseStep` (clamped single-hop, capped under the ~9900u
  server move cap) + `FleetPlay` combat branch advances one hop toward an out-of-melee target when
  `phase0.movement` is ON — proves real movement (**serverMoved 5698 u**) + organic XP (**+245**) on
  the fixed build. Evidence: `Documentation/_archive/Evidence/2026-08-14_203704-step3-fleet-play/`.
  *276/276 green.* `94993a25`. Follow-up: 4 bots still stall on despawned-target lifecycle (target
  entity null after DeleteObject) → logged for fleet-wide farming consistency.
