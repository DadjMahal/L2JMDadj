# 📋 TASK BOARD — THE single source of truth for all work

> Every task for every agent lives here. Read `START_HERE.md` (repo root) first, then come back
> here to pick work. Do **not** create your own task lists anywhere else.

---

## 1. How to use
1. `git pull --rebase origin master` — always start from latest.
2. Read `START_HERE.md` (fast orientation, §0 the ONE goal).
3. Open THIS file → pick a `TODO` row you don't collide with (check `Owner` + §4 file map).
4. **Claim it**: set your row to `IN_PROGRESS (owner)` → commit → push the claim so everyone sees it taken.
5. Implement with tests. Rule: `mvn -o -f AIPlayerEngine/pom.xml test` stays fully green.
6. **One task = one commit**, push right after; set the row to `DONE-PUSHED <hash>` → commit → push.
7. Never leave `master` dirty. On conflict: `git pull --rebase`, resolve; if it's another owner's
   file (§4) ask before merging.
8. When a row is done and verified, move it to §5 ARCHIVE (completed) and free the ID for reuse.

## 2. Status vocabulary
`TODO` · `IN_PROGRESS (owner)` · `BLOCKED (reason)` · `DONE-PUSHED <hash>`

## 3. TASK ROADMAP — 100 tasks, 10 sessions

> Grounded in (a) the codebase review (95 `MODE:PARTIAL` files, ~320 TODO/FIXME, 5 stubs, unwired
> modules), (b) live player logs from the 50-bot random-race run (solo relocation dead-end; race
> imbalance — Humans/Dwarfs ahead, Orcs slowest; USE_SKILL L1 stall; retreat/regen cycles; **0 crashes**),
> and (c) the GUIDE-MAP / RaceGuide work. Difficulty: **E**asy · **M**edium · **H**ard.
> Priority: **P0** (blocker/proof-first) · **P1** · **P2** (nice). Status defaults `TODO`.
> Completed pre-board work is archived in §5.

| Session | Theme | Effort | Priority | Count |
|---|---|---|---|---|
| S1 | Code hygiene & foundations | E/M | P0–P2 | 10 |
| S2 | Protocol & data hardening | M | P0–P2 | 10 |
| S3 | Quest pillar — live accept/complete/turn-in | H | P0/P1 | 10 |
| S4 | Guide map & race balance | M/H | P0/P1 | 10 |
| S5 | Movement & relocation (solo dead-end) | H | P0/P1 | 10 |
| S6 | Combat & survival polish | M | P0/P1 | 10 |
| S7 | Town & economy wiring | M | P1/P2 | 10 |
| S8 | Fleet coordination | M/H | P1/P2 | 10 |
| S9 | Monitoring & ops | E/M | P0/P1 | 10 |
| S10 | Legacy cleanup & archival | M/H | P1/P2 | 10 |

### Session 1 — Code hygiene & foundations
| ID | Task | Diff | Prio | Status |
|---|---|---|---|---|
| S1-T01 | Replace wildcard imports (`import java.util.*`) with explicit imports | E | P0 | TODO |
| S1-T02 | Convert remaining fully-qualified class refs to imports (BotPlayController pattern) | E | P0 | TODO |
| S1-T03 | Trailing-newline fixer pass over all main sources | E | P1 | TODO |
| S1-T04 | Implement or remove 5 stub methods (sendSay/sendClanChat/sendPartyChat/sendTradeChat/sendShout) | E | P1 | TODO |
| S1-T05 | javac -Xlint sweep: unused imports + warnings | E | P1 | TODO |
| S1-T06 | Audit + index all 95 `MODE:PARTIAL` files into one map | M | P1 | TODO |
| S1-T07 | Move hardcoded PASSWORD `ai123pass` into AIConfiguration | E | P1 | TODO |
| S1-T08 | Centralize magic numbers (RETREAT_HOP/CHASE_HOP/MAX_ACQUIRE_DIST/…) | E | P2 | TODO |
| S1-T09 | Add Spotless/Checkstyle config to keep style green going forward | M | P2 | TODO |
| S1-T10 | Create `scripts/style_sweep.sh` (repeatable sed hygiene) | E | P1 | TODO |

### Session 2 — Protocol & data hardening
| ID | Task | Diff | Prio | Status |
|---|---|---|---|---|
| S2-T01 | LoginCrypt parity/padding property tests | M | P1 | TODO |
| S2-T02 | GameCrypt key handling verified at 50 concurrent sessions | M | P1 | TODO |
| S2-T03 | PacketLogger NPC_INFO tests for multi-race entities | M | P2 | TODO |
| S2-T04 | Per-bot packet loss/drop metrics | M | P1 | TODO |
| S2-T05 | Decode CharSelectInfo (name/class/race) instead of slot-only select | M | P1 | TODO |
| S2-T06 | QUEST_LIST/SystemMessage decode coverage | M | P2 | TODO |
| S2-T07 | Reconnect backoff (avoid 50-bot thundering herd) | M | P0 | TODO |
| S2-T08 | Socket read-timeout/keepalive tuning for 50-bot load | M | P1 | TODO |
| S2-T09 | Structured JSON event log for the fleet watcher | M | P1 | TODO |
| S2-T10 | Protocol-version guard (server mismatch detection) | E | P2 | TODO |

### Session 3 — Quest pillar — live accept/complete/turn-in (the ONE-goal gap)
| ID | Task | Diff | Prio | Status |
|---|---|---|---|---|
| S3-T01 | Live-prove quest ACCEPT (real giver, validated bypass, journal shows active) | H | P0 | TODO |
| S3-T02 | Live-prove objective progress (kill/collect counters via QUEST_LIST) | H | P0 | TODO |
| S3-T03 | Live-prove TURN-IN + reward receipt (exp/adena/item) | H | P0 | TODO |
| S3-T04 | Enable `phase0.quest.npcId` flow by default for Human newbies | M | P1 | TODO |
| S3-T05 | Persist quest stepIndex across sessions (QuestProgressTracker) | M | P1 | TODO |
| S3-T06 | QuestDialogDriver: multi-quest journals + chain choice | M | P1 | TODO |
| S3-T07 | Wire per-bot `varietySeed` into FleetPlay acquire pick (STEP 7) | M | P1 | TODO |
| S3-T08 | AcquireCooldown tuning for L1 vs L20+ | E | P2 | TODO |
| S3-T09 | Newbie Q1–Q10 chain automation (per race) | H | P1 | TODO |
| S3-T10 | Class-change Path quests (Q401–418) live loop | H | P2 | TODO |

### Session 4 — Guide map & race balance
| ID | Task | Diff | Prio | Status |
|---|---|---|---|---|
| S4-T01 | Add per-race L1–5 hunt-zone anchors to RaceGuide (fixes non-Human starts) | M | P0 | TODO |
| S4-T02 | Validate each race's idleAnchor is walkable/reachable live | M | P0 | TODO |
| S4-T03 | Orc-village throughput fix (slowest race in the 50-bot run) | M | P1 | TODO |
| S4-T04 | Elven Keltir target-selection tuning (tanky mobs) | M | P1 | TODO |
| S4-T05 | Per-race restock vendor landmarks verified live | M | P1 | TODO |
| S4-T06 | Verify all races' base classes resolve in the profession tree | M | P1 | TODO |
| S4-T07 | Teleport-leg BFS for cross-zone quest travel | H | P2 | TODO |
| S4-T08 | Extend RaceGuideTest: every race chain has no void coords | E | P1 | TODO |
| S4-T09 | Cache idleAnchor per race (avoid per-tick recompute) | E | P2 | TODO |
| S4-T10 | Landmark proximity gate (don't re-hop to the same landmark) | M | P1 | TODO |

### Session 5 — Movement & relocation (solo dead-end — top 50-bot log finding)
| ID | Task | Diff | Prio | Status |
|---|---|---|---|---|
| S5-T01 | Fix solo-bot relocation dead-end (far-hop doesn't persist server-side) | H | P0 | TODO |
| S5-T02 | Frozen bot routes toward nearest hostile entity (not just last-XP/mate) | M | P0 | TODO |
| S5-T03 | Hop persistence: require server-ack before re-issuing (kill 2-timeout churn) | M | P0 | TODO |
| S5-T04 | Escape-gate: after 3 abandons, short walk to nearest entity instead of 60s hold | M | P1 | TODO |
| S5-T05 | ZoneRouter walkability heuristic (avoid ocean/void hops) | H | P1 | TODO |
| S5-T06 | MoveTelemetry: per-bot move success rate on the dashboard | E | P1 | TODO |
| S5-T07 | Per-race movement min/max radius (small near village) | E | P2 | TODO |
| S5-T08 | Anti-oscillation (no bouncing between two landmarks) | M | P1 | TODO |
| S5-T09 | Verify STEP 6 fix for SOLO bots (no-mates case) | M | P0 | TODO |
| S5-T10 | Position-sync drift watchdog (local vs server-ack) | M | P1 | TODO |

### Session 6 — Combat & survival polish
| ID | Task | Diff | Prio | Status |
|---|---|---|---|---|
| S6-T01 | Regression test for USE_SKILL→melee fallback (the L1-farming unblock) | E | P0 | TODO |
| S6-T02 | Scale stale-target budget by level (L1 needs >15s) | E | P1 | TODO |
| S6-T03 | Retreat→camp-heal timing (bots over-retreat after low HP) | M | P1 | TODO |
| S6-T04 | HP-potion use when stocked (ConsumableManager wiring) | M | P1 | TODO |
| S6-T05 | Starter gear/soulshot provisioning for fresh chars | M | P0 | TODO |
| S6-T06 | Death-penalty handling + respawn position choice | M | P1 | TODO |
| S6-T07 | Aggro cap: avoid pulling 33 mobs at once (leash/kite) | H | P1 | TODO |
| S6-T08 | Engage range per level/race tuning | M | P2 | TODO |
| S6-T09 | HEAL mana budget (63 casts observed — verify economy) | M | P2 | TODO |
| S6-T10 | Death-loop guard (relocate after N deaths in a window) | M | P1 | TODO |

### Session 7 — Town & economy wiring
| ID | Task | Diff | Prio | Status |
|---|---|---|---|---|
| S7-T01 | Wire RestockPlanner BUY into FleetPlay restock branch (live vendor walk) | M | P1 | TODO |
| S7-T02 | BuyManager executes RestockPlan orders (soulshots/pots/gear) | H | P1 | TODO |
| S7-T03 | Per-race vendor landmark verification | M | P1 | TODO |
| S7-T04 | Adena income tracking (kills/quests/merchant) | E | P1 | TODO |
| S7-T05 | Restock threshold per class (fighter vs mystic) | E | P2 | TODO |
| S7-T06 | SellManager auto-sell overflow when invPct high | M | P1 | TODO |
| S7-T07 | Warehouse overflow storage | M | P2 | TODO |
| S7-T08 | Soulshot restock live proof | M | P1 | TODO |
| S7-T09 | Teleport to far vendors (TeleportManager) | H | P2 | TODO |
| S7-T10 | Item values calibrated from real drop rates | H | P2 | TODO |

### Session 8 — Fleet coordination
| ID | Task | Diff | Prio | Status |
|---|---|---|---|---|
| S8-T01 | Wire FleetSpreadPlanner into FleetPlay idle (live anti-clustering) | M | P1 | TODO |
| S8-T02 | Validate zone distribution across 50 bots | M | P1 | TODO |
| S8-T03 | Same-race party formation | H | P2 | TODO |
| S8-T04 | Party loot distribution basics | H | P2 | TODO |
| S8-T05 | Shared hunt-zone coordination messaging | H | P2 | TODO |
| S8-T06 | Mate-rescue routing (verify) | M | P1 | TODO |
| S8-T07 | varietySeed live: no same-quest stampede | M | P1 | TODO |
| S8-T08 | Dashboard per-race stats | E | P1 | TODO |
| S8-T09 | 50-bot server load profile/tuning | M | P1 | TODO |
| S8-T10 | Cross-zone respect (no griefing other zones) | E | P2 | TODO |

### Session 9 — Monitoring & ops
| ID | Task | Diff | Prio | Status |
|---|---|---|---|---|
| S9-T01 | Promote the 2h fleet watcher to `scripts/watch_fleet.sh` | M | P1 | TODO |
| S9-T02 | Fix watcher xp/min metric (currently reads 0) | E | P0 | TODO |
| S9-T03 | Reusable random-race provisioning script (`scripts/provision_fleet.sh`) | M | P1 | TODO |
| S9-T04 | Reusable 50-bot launcher with race rotation | E | P1 | TODO |
| S9-T05 | Dashboard per-race filter + race badge | E | P1 | TODO |
| S9-T06 | Dashboard kills/min + XP/min per bot | M | P1 | TODO |
| S9-T07 | Log rotation for fleet50.log (50 bots = huge logs) | M | P0 | TODO |
| S9-T08 | Auto-restart fleet on crash (systemd/supervisord) | M | P1 | TODO |
| S9-T09 | DB backup before mass provisioning | E | P1 | TODO |
| S9-T10 | Health endpoint: online-vs-expected + alerts | M | P1 | TODO |

### Session 10 — Legacy cleanup & archival
| ID | Task | Diff | Prio | Status |
|---|---|---|---|---|
| S10-T01 | Wire-or-archive unwired `MODE:PARTIAL` modules (brain/chat/social/town/party/farm/death/director) | H | P1 | TODO |
| S10-T02 | Inventory `engine/*` legacy pile; archive unreferenced classes | H | P1 | TODO |
| S10-T03 | Dedupe frame emission (Phase0Wiring vs AIPlayerConnection) | M | P1 | TODO |
| S10-T04 | Kill dead imports across the 95 PARTIAL files | M | P2 | TODO |
| S10-T05 | Consolidate config loading (AIConfiguration central) | M | P2 | TODO |
| S10-T06 | Remove obsolete probes (superseded by FleetPlay) | M | P2 | TODO |
| S10-T07 | Single logging convention (java.util.logging vs slf4j) | M | P2 | TODO |
| S10-T08 | PARTIAL→COMPLETE conversion with tests (top-10 files) | H | P2 | TODO |
| S10-T09 | Verify no Redis/Postgres deps (in-memory replacements) | M | P1 | TODO |
| S10-T10 | Final docs sync (TASKS/START_HERE/STATUS) | E | P1 | TODO |

## 4. File ownership map
| Path (repo-relative) | Owner | Notes |
|---|---|---|
| `AIPlayerEngine/src/main/java/com/aiplayer/phase0/play/**` | **play-builder** | decision ladder + planners |
| `AIPlayerEngine/src/test/java/com/aiplayer/phase0/play/**` | **play-builder** | controller tests |
| `AIPlayerEngine/src/main/java/com/aiplayer/examples/FleetPlay.java` | **play-builder** | fleet launcher |
| `AIPlayerEngine/src/main/java/com/aiplayer/phase0/**` (rest) | **play-builder** | shared phase0 engine |
| `Documentation/**` (incl. this board) | **doc-sweeper** | docs + board upkeep |
| `scripts/**` | **play-builder** | helper tools |

## 5. ARCHIVE — completed (moved off the live board)
| ID | Task | Status | Owner |
|---|---|---|---|
| **STEP 0** | Archive history pile + lean START_HERE/TASKS board for the PLAY goal | `DONE-PUSHED 7e820756` | doc-sweeper |
| **STEP 1** | BotPlay controller — bots pick goals and act, never idle | `DONE-PUSHED 9b0d34f6` | play-builder |
| **STEP 2** | Quest accept/turn-in live loop (accept → do → turn-in) — pure dialog driver, gated OFF by default | `DONE-PUSHED c4ee832a` | play-builder |
| **STEP 3** | 5-bot live run + play evidence (fleet actually plays) | `DONE-PUSHED 94993a25` | play-builder |
| **STEP 4** | Smartness polish: death/respawn, low-HP retreat, restock intent | `DONE` | play-builder |
| **STEP 5** | Despawned-target (`DeleteObject`) lifecycle verified on the P2 fixed build | `DONE (verified 3d97fe53)` | play-builder |
| **STEP 6** | Idle-relocation empty-zone dead-end fixed (last-XP/nearest-mate routing + escape gate) | `DONE-PUSHED 4d8acad5` | play-builder |
| **GUIDE-MAP-INTEG** | Guide map wired into `RelocationPlanner` (race landmark idle, frozen re-route, escape gate) | `DONE-PUSHED 4d8acad5` | play-builder |
| **GUIDE-MAP** | Per-race/profession guide map (`RaceGuide`) from real Interlude sources | `DONE-PUSHED ce3e2426` | play-builder |
| **STEP 7** | Ultra-smart vol.1 — RestockPlanner BUY, FleetSpreadPlanner, reward-aware + seed-diverse quest pick | `DONE-PUSHED d2c75b4b` | play-builder |
| **STEP 8** | Live-path cleanup vol.1 (BotPlayController FQN→import); review + roadmap | `DONE-PUSHED b08e204f` | play-builder |
| **LIVE-RUN** | 50 random-race players created + played 2h (provisioning, launcher race rotation, USE_SKILL→melee fix) | `DONE-PUSHED 0fd3fef4/e53ca85a` | play-builder |

## 6. Changelog (newest last)
- **2026-08-17 · doc-sweeper/play-builder:** Board restructure — 100-task roadmap in 10 sessions
  (difficulty E/M/H × priority P0–P2, all `TODO`), grounded in the code review (95 `MODE:PARTIAL`,
  stubs, unwired modules), the 50-bot live logs (solo relocation dead-end, race imbalance, L1
  USE_SKILL stall, 0 crashes), and the RaceGuide/GUIDE-MAP work. All prior completed STEPs/GUIDE-MAP
  moved to §5 ARCHIVE. `mvn test` **345 green**; 50-bot fleet + 2h watcher running live.





