# 📋 TASK BOARD — the single source of truth for OPEN work (100-task master plan)

> Restructured 2026-08-22 into a full 100-task road to the Living Server. The previous board
> (5 rows) is archived at `Documentation/_archive/TASKS-2026-08-22-pre-100board.md`.
> Completed work lives in the anti-redo registry `Documentation/REVIEWED_TASKS.md`
> (check it before starting anything). Program context: `Documentation/UpgradePlan/README.md`.
> Rules: `Documentation/WORKFLOW.md`. Core basis: `Architecture.md` (repo root).

## 1. How to use
1. `git pull --rebase origin master` — always start from latest; read `Architecture.md`
   (the core basis) before touching code.
2. Read `START_HERE.md` (orientation) + `STATUS.md` (current state).
3. Pick an open row below you don't collide with (check `Owner` + §3 file map) and its phase.
4. **Claim it**: set the row to `IN_PROGRESS (owner)` → commit → push the claim.
5. Implement with tests (`mvn -o -f AIPlayerEngine/pom.xml test` stays green).
6. **One task = one commit** `type(scope): brief`, push right after; set the row to
   `DONE-PUSHED <hash>`, move it to `REVIEWED_TASKS.md`, write a RuntimeLog
   (`Documentation/RuntimeLogs/<date>-<ID>-<slug>.md`, ≤70 lines).

## 2. Status vocabulary
`TODO` · `IN_PROGRESS (owner)` · `BLOCKED (reason)` · `DONE-PUSHED <hash>` → row moves to the registry.
`DONE (reviewed YYYY-MM-DD)` = verified already shipped elsewhere (e.g. EP-* or STATUS.md); kept on
the board for visibility, no work needed.

## 3. Phase overview (100 tasks)
| Phase | IDs | Count | Theme |
|---|---|---|---|
| **P0** Foundations & architecture | F-01…F-10 | 10 | Architecture.md, START_HERE basis, gates, hygiene |
| **P1** Quest pillar (live) | S3-T02, S3-T03 | 2 | quest objective progress + turn-in proof |
| **P2** Engine & behavior core | EB-01…EB-14 | 14 | session/decision, personality, chat, restock |
| **P3** Knowledge & datapack | GK-1…GK-12 | 12 | extractors → JSON → KnowledgeBase → chains |
| **P4** Intelligence & learning | IN-1…IN-10 | 10 | close the learning loop, memory, gossip |
| **P5** Observability & watchers | LW-1…LW-9 | 9 | events.jsonl, WATCHER_RULES, persistence |
| **P6** Integration & APIs | BR-1…BR-9, DA-1…DA-4 | 13 | ServerBridge, PlayerAPI, ControlPlane, security |
| **P7** Living Server & community | LI-1…LI-6, EN-1…EN-5 | 11 | souls, schedules, relationships, mentors, events |
| **P8** Content coverage (plays everything) | CO-1…CO-13 | 13 | party, shop, fishing, duels, crafting, clan |
| **P9** Scale & proof | RS-1…RS-6 | 6 | KnowledgeService, bootstrap, 100/500 soak, Golden Five |

**Rule of thumb:** P0 and P1 first (terrain + live blockers), P3/P5/P6 build the terrain most
other work stands on, P2/P4 interleave freely, P8 deepens coverage, P7 is where the laptop
product takes shape, P9 is the proof gate.

---
---

## P0 — FOUNDATION & ARCHITECTURE
| ID | Task | Size | Prio | Dep | Status |
|---|---|---|---|---|---|
| F-01 | Root `Architecture.md` — the core basis doc (planes, engine internals, hard lines) | S | P0 | – | DONE (2026-08-22 restructure) |
| F-02 | `START_HERE.md` gains §0.5 "core basis" section + doc-map link to Architecture.md | S | P0 | F-01 | DONE (2026-08-22 restructure) |
| F-03 | AIPlayerEngine/README.md: sync package diagram + link root Architecture.md | S | P1 | F-01 | DONE-PUSHED 38aefc90 |
| F-04 | Root README.md: project header + pointer to START_HERE/Architecture (content stays candid) | S | P1 | F-01 | DONE-PUSHED f3982ad9 |
| F-05 | Documentation/README.md: index of every key doc + cross-link audit (no orphan docs) | S | P2 | F-01 | DONE (reviewed 2026-08-22 — index exists; Architecture.md row added) |
| F-06 | MODE_PARTIAL_INDEX refresh: every `MODE:PARTIAL` file listed; new files follow the header rule | S | P1 | – | DONE (reviewed 2026-08-22 — refreshed in EP-8, 95 files listed) |
| F-07 | Golden gate script `scripts/gate.sh`: test+style+secret-lint one command, offline | S | P1 | – | DONE-PUSHED d4e3407b |
| F-08 | Baseline suite report: re-pin current test count, note machine-dependence (docs/README runbook) | S | P1 | F-07 | DONE (reviewed 2026-08-22 — pinned in STATUS.md, 415 verified `mvn test`) |
| F-09 | Scripts hygiene: one-shot probes → `scripts/_probes/` or attic; live ops stay at scripts/ root | M | P2 | – | DONE-PUSHED 1b58ceb4 |
| F-10 | examples/ probe classes: decommission or move to attic (15 probe classes still compiled in `examples/`) | M | P2 | F-09 | DONE-PUSHED 9c2d11ea |

## P1 — QUEST PILLAR (live carry-over, do not collide)
| ID | Task | Size | Prio | Dep | Status |
|---|---|---|---|---|---|
---

## P2 — ENGINE & BEHAVIOR CORE
| ID | Task | Size | Prio | Dep | Status |
|---|---|---|---|---|---|
| EB-01 | Separate decision core from session I/O: `BotSession` handles socket/lifecycle, decisions via behavior engine (post-EP-4 refinement) | L | P1 | – | DONE-PUSHED 9a439438 |
| EB-02 | Deterministic seeds: every random source routed through per-bot seeded RNG (reproducible runs) | S | P1 | EB-01 | DONE-PUSHED 833eab58 |
| EB-03 | Goal-ladder tuning: `BotPlayController` goals/priorities configurable per profile (not hardcoded order) | M | P1 | EB-01 | DONE-PUSHED 3dd20c2e |
| EB-04 | Personality → behavior mapping: PersonalityProfile actually drives params (risk, pace, talkativeness) | M | P2 | EB-03 | DONE-PUSHED 38e607b3 |
| EB-05 | Humanization pack review: Humanization + imperfection params audited, wired to feedback (no dead knobs) | M | P2 | – | DONE-PUSHED fb11f4ce |
| EB-06 | Restock/Inventory planner as pure decision module (behavior/restock) feeding RestockPlanner | M | P1 | GK-3 | DONE-PUSHED d81ae488 |
| EB-07 | Movement & travel planner: route between zones/towns using map.json + waypoints | M | P2 | GK-9 | DONE-PUSHED 50eac026 |
| EB-08 | Chat reply engine v0: template + canned replies per context, LLM-ready seam | M | P2 | CO-4 | DONE-PUSHED de193e51 |
| EB-09 | Session lifecycle API: spawn/connect/play/sleep/disconnect as first-class states (SoulScheduler hook) | S | P1 | EB-01 | DONE-PUSHED b531ec56 |
| EB-10 | Graceful fleet shutdown + resume: drains bots on stop, safe restart (feeds keep_alive.sh) | M | P1 | EB-09 | DONE-PUSHED c24c59fc |
| EB-11 | Config single-source: all property reads funnel through EngineConfig with validation | M | P1 | – | DONE-PUSHED 74cbe68c |
| EB-12 | Logging hygiene: java.util.logging everywhere, no System.out in main | S | P2 | – | DONE-PUSHED 2d274bac |
| EB-13 | Per-bot resource guard: rate/backpressure limits (actions per tick, queue caps) | M | P1 | EB-01 | DONE-PUSHED 83021991 |
| EB-14 | BotSnapshot completeness: goal + sub-goal + cooldowns in telemetry for dashboard | M | P1 | EB-03 | DONE-PUSHED 52433ee8 |

---

## P3 — KNOWLEDGE & DATAPACK (ground truth = `SourceCode/dist/game/data/`, read-only)
| ID | Task | Size | Prio | Dep | Status |
|---|---|---|---|---|---|
| GK-1 | Extractor skeleton: `scripts/datapack/` (_lib.py, per-domain stubs, extract_all.py, SCHEMAS.md ≤150 lines, validate.py) | S | P1 | – | DONE-PUSHED 825928ca |
| GK-2 | NPC+spawn+drops extractor → `npcs.json` (6,541 records / 38,564 drops / 10,754 spawns, validate green) | M | P1 | GK-1 | DONE-PUSHED a64f168d |
| GK-3 | Items + skills + class-tree extractor → `items.json` (9,215) + `skills.json` (13,777) + `classes.json` (9 bases/89, 5 chains) | M | P1 | GK-1 | DONE-PUSHED c47ff42d |
| GK-4 | Quest extractor (344 Java scripts + html dialog graph) → `quests.json` (302 start+min, Q00006 verified, review 42) | L | P1 | GK-1 | DONE-PUSHED 4d8f758d |
| GK-5 | Shop extractor → `shops.json` (616 buylists/18,728 items, 95 multisell/8,609 offers/616 vendors; buylist vendor-linkage honestly flagged) | M | P1 | GK-1 | DONE-PUSHED 92906f96 |
| GK-6 | KnowledgeBase loader: JSON → id-indexed queries (npc/item/quest/skillLadder/droppersOf/questsFor) + RaceGuide anchor-vs-spawn test (553 tests) | M | P1 | GK-2..5 | DONE-PUSHED c85c1a19 |
| GK-7 | Zero→hero chain builder → `chains.json` + `chains.md` (9 race×base chains, 16 steps each, transfers at L20/L40) | M | P1 | GK-4 | DONE-PUSHED d674d7b2 |
| GK-8 | Gear/build recommender wired into RestockPlanner (uses items/shops/chains) | M | P2 | GK-3, GK-5 | IN_PROGRESS (Cline) |
| GK-9 | Map extractor → `map.json`: 245 teleporters (1,407 dests) + 2,533 zones + 14 routes + 203 spawnRegions; needsReview flags for out-of-world boss zones (validate 9/9) | M | P2 | GK-1 | DONE-PUSHED e1bdb9de |
| GK-10 | Dialog index: NPC talk graph from html — `dialog.json` (344 questDialog + 9,190 dialogPage, 4,667 links; firstPages/turnInCandidates; validate 10/10) | S | P2 | GK-1 | DONE-PUSHED 7df43337 |
| GK-11 | SkillLearn/class-tree loader: `trainers.json` — 217 trainer NPCs, 89 classes, REAL point-spawn coords (Auron 30010 → Human 0..9 @ Gludin; validate 11/11) | S | P2 | GK-3 | DONE-PUSHED ba83a0f5 |
| GK-12 | Live-verification job: sample generated JSON against live server values | S | P2 | GK-6 | DONE-PUSHED 3a0cc836 |
---

## P4 — INTELLIGENCE & LEARNING (close the open loop)
| ID | Task | Size | Prio | Dep | Status |
|---|---|---|---|---|---|
| IN-1 | Close the loop v0: decisions consult learned stats behind one `DecisionPolicy` (OFF/SHADOW/ENFORCED) — contextual bandit | M | P1 | LW-3 | TODO |
| IN-2 | Utility-based combat gates: replace fixed thresholds (CombatAI) with tunable utilities | M | P1 | IN-1 | TODO |
| IN-3 | Per-bot HuntingMemory persisted (zone/mob picks) consulted for target choice | M | P1 | LW-5, GK-2 | TODO |
| IN-4 | Exploration: ε-greedy with decay + risk personalities (dwarfs hoard, orcs charge) | S | P2 | IN-1 | TODO |
| IN-5 | Fleet-level policy sharing: gossip best arms between bots (bounded, privacy-aware) | M | P2 | IN-1 | TODO |
| IN-6 | Quest-arc driver consuming `chains.json`: quest goals override generic HUNT | M | P1 | GK-7 | TODO |
| IN-7 | Wire Emotion/EmotionalState + PersonalityProfile into real reads (no decorative fields) | M | P2 | IN-1 | TODO |
| IN-8 | Decision explainability: per-decision log (why: context→choice→outcome) in events.jsonl | S | P2 | IN-1 | TODO |
| IN-9 | Per-bot growth telemetry: what each bot learned vs baseline, exposed to dashboard | M | P2 | IN-1 | TODO |
| IN-10 | Learning quality self-assessment: drift/regret metrics for the bandit (no black-box) | M | P2 | IN-1 | TODO |

---

## P5 — OBSERVABILITY & WATCHERS
| ID | Task | Size | Prio | Dep | Status |
|---|---|---|---|---|---|
| LW-1 | Event file sink: EventRing → `logs/fleet/events.jsonl` (async, rotation midnight/50MB, 7 gzip keeps, drop-oldest counter) | M | P1 | – | TODO |
| LW-2 | `Documentation/WATCHER_RULES.md` (≤120 lines) + `scripts/watchers/_template.py`; migrate watch_fleet.py | S | P1 | – | TODO |
| LW-3 | Canonical watcher suite: health / fleet / behavior / progress (rules-doc-conformant) | M | P1 | LW-1, LW-2 | TODO |
| LW-4 | Kill /tmp: all persistent outputs under `logs/` + `data/`, rotation everywhere | S | P2 | LW-3 | TODO |
| LW-5 | Session + quest-progress persistence: `data/sessions/`, tracker file store (survives restart) | M | P1 | EB-01 | TODO |
| LW-6 | events.jsonl schema versioning + field dictionary (frozen header comment, migration note) | S | P2 | LW-1 | TODO |
| LW-7 | Engine stdout → rotated files under `logs/engine/` (no console-only trace) | S | P2 | LW-4 | TODO |
| LW-8 | Behavior dataset pipeline: watcher output → `data/observations/` as learning input | M | P2 | LW-3 | TODO |
| LW-9 | Observations quality report: row counts, schema drift, gaps per bot | S | P2 | LW-8 | TODO |
| S3-T02 | Live-prove quest objective progress (kill/collect counters via QUEST_LIST; quest persists [[6,1]], dialog re-click works — objective parser + live-html feed added 2026-08-22; remaining: raw counter capture + QUEST_LIST state-flip proof) | H | P0 | – | IN_PROGRESS (play-builder) |
| S3-T03 | Live-prove quest TURN-IN + reward receipt (exp/adena/item) — turn-in/reward detection added 2026-08-22 (QuestTurnRewardParser + BotInfo.questReward); remaining: live run w/ DB to observe receipt + journal-clear + exp delta | H | P0 | S3-T02 | BLOCKED (on S3-T02 live proof; detection shipped) |
---

## P6 — INTEGRATION & APIs (three-plane architecture; server core stays vanilla)
| ID | Task | Size | Prio | Dep | Status |
|---|---|---|---|---|---|
| BR-1 | ServerBridge v1 (datapack script): loopback HTTP :9300, token, `/v1/status\|players\|raidbosses\|auction\|castles` read-only truth | M | P1 | – | TODO |
| BR-2 | ServerBridge admin relay: whitelisted commands (kick/announce/spawn) via AdminCommandHandler, audit log, fail-closed | M | P1 | BR-1 | TODO |
| BR-3 | PlayerAPI façade (Java): move/attack/talk/trade/chat/party/store/inventory/skill/duel as stable javadoc'd contract + API.md | M | P1 | EB-01 | TODO |
| BR-4 | ControlPlane: merge EngineAPI + ServerBridge, auth + audit + kill-switch (any bot, any admin) | L | P1 | BR-2, DA-1 | TODO |
| BR-5 | External exposure: TLS reverse proxy (Caddy) + `SECURITY.md` + firewall matrix; EngineAPI/Bridge stay loopback | S | P1 | BR-4 | TODO |
| BR-6 | LLM advisory integration: free tier, off hot path, drives canned dialogue/tips (not core decisions) | M | P2 | RS-4 | TODO |
| BR-7 | Engine-side bridge client: loopback token client + status polling + capability handshake | M | P2 | BR-1 | TODO |
| BR-8 | ServerBridge hot-reload + test harness: reload script, contract tests, version bump policy | M | P2 | BR-1 | TODO |
| BR-9 | Bridge rate/credential guard: token rotation, per-command rate limit, audit retention policy | S | P2 | BR-2 | TODO |
| DA-1 | Engine admin API: spawn/connect/disconnect/setPolicy/stopFleet + audit trail (auth-gated) | M | P1 | – | TODO |
| DA-2 | Dashboard pages: Souls, Control, Community, audit viewer (extends existing SPA) | M | P2 | DA-1, BR-4 | TODO |
| DA-3 | Per-bot inspector: session, goals, decisions, learning state, recent events | S | P2 | DA-1 | TODO |
| DA-4 | Engagement metrics page: souls activity, human-bot interactions, party/duel/fishing stats | M | P2 | DA-2 | TODO |

---

## P7 — LIVING SERVER & COMMUNITY (the product layer)
| ID | Task | Size | Prio | Dep | Status |
|---|---|---|---|---|---|
| LI-1 | `ai_souls` schema + soul identity generator (birth certificate: name, race, class, traits, birthday) | M | P1 | – | TODO |
| LI-2 | SoulScheduler: circadian wake/sleep + population policy + human-presence coupling | M | P1 | LI-1, BR-1 | TODO |
| LI-3 | Relationships: souls remember humans (greeting, favors, friendship perks: better prices) | M | P2 | LI-1, CO-4 | TODO |
| LI-4 | Soul CLI tooling: create/inspect/list/destroy souls (ops-facing) | S | P2 | LI-1 | TODO |
| LI-5 | Souls dashboard "life" page: who is awake, current goal, mood, relationships | S | P2 | LI-1 | TODO |
| LI-6 | Soul archive & lifecycle history: birth→life events→retirement JSONL per soul | S | P2 | LI-1 | TODO |
| EN-1 | Mentor behavior pack: greet new players, gift starter bag, escort to hunt, answer "where…?" | M | P2 | LI-3 | TODO |
| EN-2 | AI-hosted events: trivia hour + duel night + fishing rivalry with mailed prizes | L | P2 | BR-2, CO-8 | TODO |
| EN-3 | Golden Five integration: cast of 5 souls, week-long soak on laptop, verdict report | L | P1 | P7 complete | TODO |
| EN-4 | Community transparency policy: labeling decision (AI badge vs incognito) + rules doc | S | P2 | – | TODO |
| EN-5 | Leaderboard rivals: souls climb raid-points/fishing boards legitimately | M | P2 | CO-7 | TODO |
---

## P8 — CONTENT COVERAGE (AI plays ALL of SourceCode — coverage contract, Audit 06)
| ID | Task | Size | Prio | Dep | Status |
|---|---|---|---|---|---|
| CO-1 | Coverage matrix as living artifact: docs + dashboard page (row per server system) | S | P1 | – | TODO |
| CO-2 | Private sell/buy store: bot shopfront in town (prices from economy, human-buyable) | M | P1 | GK-5 | TODO |
| CO-3 | Party-with-humans full loop: accept/invite, follow, assist, loot rules (never LFG alone) | L | P1 | – | TODO |
| CO-4 | Chat receive→reply pipeline: channels + whisper, canned + LLM-assisted (off hot path) | M | P1 | BR-6 | TODO |
| CO-5 | Warehouse + crystals + safe enchant (souls bank their valuables) | M | P2 | – | TODO |
| CO-6 | Crafting + manor participation (dwarf career: craft, harvest crops, sell) | L | P2 | CO-2 | TODO |
| CO-7 | Fishing: idle-friendly minigame + fishing championship participation | M | P2 | – | TODO |
| CO-8 | Duels + PvP etiquette: accept/fair play, no karma grief, honor rules | M | P2 | – | TODO |
| CO-9 | "Plays-everything" regression suite: coverage matrix rows → smoke probes (CI-able) | M | P1 | all | TODO |
| CO-10 | Clan/alliance basics: join/create clan, ally with humans, clan chat | M | P2 | CO-4 | TODO |
| CO-11 | Subclass + noblesse + Olympiad path: souls pursue prestige legitimately | L | P2 | GK-7 | TODO |
| CO-12 | Siege/mercenary participation (laptop phase off; design doc + probes only) | L | P2 | CO-10 | TODO |
| CO-13 | Town-life behaviors: tavern/temple idle hangouts, emote/cosmetic routines (charm) | M | P2 | EB-05 | TODO |

---

## P9 — SCALE & PROOF (laptop → fleet; Golden Five is the gate)
| ID | Task | Size | Prio | Dep | Status |
|---|---|---|---|---|---|
| RS-1 | KnowledgeService query facade (in-process middleware: "next quest? nearest NPC? where drops?") | M | P1 | GK-6 | TODO |
| RS-2 | Self-contained autonomous bootstrap (`--self-contained`): connects, picks race/class/name, starts playing | L | P2 | RS-1, GK-6 | TODO |
| RS-3 | Scaling profiles + supervisor design doc (laptop 1-5, small 50, big 500; resource budgets) | M | P1 | EB-01 | TODO |
| RS-4 | LLM advisory hook (offline dataset analysis, optional feature flag) | S | P2 | LW-3 | TODO |
| RS-5 | 100-bot soak measurement (stability + learning stats over 48 h) | M | P2 | RS-3 | TODO |
| RS-6 | 500-bot profile ladder + ceiling report (RAM/CPU/packets, per-bot cost) | L | P2 | RS-5 | TODO |

---

## 4. File ownership map
| Path (repo-relative) | Owner | Notes |
|---|---|---|
| `AIPlayerEngine/src/main/java/com/aiplayer/behavior/**` (+tests) | **play-builder** | decision ladder, combat/quest/social/... domains |
| `AIPlayerEngine/src/main/java/com/aiplayer/core/**` (+tests) | **play-builder** | FleetConfig, BotSession, BotInfo, wiring, snapshots |
| `AIPlayerEngine/src/main/java/com/aiplayer/{net,protocol,web,monitor,metrics,cli,knowledge,learning,examples}/**` | **play-builder** | engine plumbing + fleet launcher (FleetPlay) |
| `AIPlayerEngine/src/main/resources/**` (config, dashboard SPA, knowledge JSON) | **play-builder** | ai-player.properties (`engine.*`), generated knowledge/ |
| `scripts/**` (+ `scripts/datapack`, `scripts/watchers`) | **play-builder** | helper tools; secrets via `fleet_env.local` (gitignored) |
| `Documentation/**` (incl. this board) | **doc-sweeper** | docs + board upkeep |
| `AIPlayerEngine/attic/**` | — | dead code, do not edit (see `AIPlayerEngine/attic/README.md`) |

## 5. Registry & history
- Done/in-flight registry (never redo work): `Documentation/REVIEWED_TASKS.md`.
- Pre-100-board board snapshot: `Documentation/_archive/TASKS-2026-08-22-pre-100board.md`.
- Per-task evidence: `Documentation/RuntimeLogs/`.
- Pre-board history (STEP 0–8, GUIDE-MAP, LIVE-RUN): `REVIEWED_TASKS.md` §E.3.