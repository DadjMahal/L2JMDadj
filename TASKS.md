# 📋 L2JM — Task Roadmap: Top-Tier AI Players + Multi-Agent Workflow

**Goal:** Build autonomous AI players for the L2JMobius Interlude server using a multi-agent workflow.

> **Renumbered 2026-08-02** — fixed the duplicate 59–63 collision between Part 3 (Combat) and
> Part 4 (Goals). Part 3 now owns 43–63; Part 4 (Goals) starts at 64. Two duplicate combat rows
> that were wrongly placed in Part 4 were dropped. Total unique tasks: 103.
>
> An **Evidence** column was added: `done` must cite real proof. Tasks whose "proof" is only an
> `assertTrue(true)` test are downgraded to `in_progress`. **Live-verified?** — most combat/perception
> tasks are proven only by `mvn compile` + unit tests on value objects, **NOT** against the live
> server. Per `AIPlayerEngine/AIStatusLogs/ai_progress_report.txt`: the AI modules "use mock data,
> not connected to real gameplay." Closing that live-verification gap is the real next milestone.

---

## How to Use This File
- **Status values:** `pending` / `in_progress` / `done` / `blocked`
- One task = one agent session. Before claiming: set `in_progress` + your agent name.
- After finishing: set `done`, add a one-line Result + Evidence link, update `STATUS.md`.
- **Next task = first `pending` row below.** `START_HERE.md` + `STATUS.md` always mirror this.

---

## Part 0 — The Bootstrap System (Tasks 1-15) ✅ COMPLETE

| # | Task | Status | Owner | Evidence/Result |
|---|------|--------|-------|-----------------|
| 1 | Write `AGENT_ONBOARDING.md` at repo root | done | System | 1-paragraph summary, 6 rules, routing table |
| 2 | Write `STATUS.md` | done | System | Slimmed 2026-08-02; mirrors START_HERE |
| 3 | Write `STYLEGUIDE.md` | done | System | Naming, logging, commit format, DoD checklist |
| 4 | Migrate PARSE_Tasks.md → `TASKS.md` at repo root | done | System | Original now in `_archive_superseded/` |
| 5 | Write `Documentation/SESSION_PROTOCOL.md` | done | System | Now merged into `WORKFLOW.md` (P4) |
| 6 | Write `Documentation/MULTI_AGENT_RULES.md` | done | System | Now merged into `WORKFLOW.md` (P4) |
| 7 | Write `scripts/session_start.sh` | done | System | Made resume-aware 2026-08-02 (P5) |
| 8 | Write `scripts/session_end.sh` | done | System | Made non-interactive + WIP 2026-08-02 (P5) |
| 9 | Write `scripts/verify_no_dead_code.sh` | done | System | TODO/FIXME scanner |
| 10 | Establish RuntimeLog naming + size convention | done | System | `YYYY-MM-DD-<task>.md`, ≤40-70 lines |
| 11 | Review and trim `AGENT_ONBOARDING.md` | done | System | Kept lean |
| 12 | Add pre-session token budget note | done | System | Token budget in AGENT_ONBOARDING |
| 13 | Add Definition of Done checklist to STYLEGUIDE.md | done | System | DoD checklist present |
| 14 | Dry-run the whole bootstrap with Laguna | done | System | Session completed |
| 15 | Review TASKS.md for completeness | done | System | Collision-fixed + renumbered 2026-08-02 |

---

## Part 1 — Telemetry System (Tasks 16-30) ✅ COMPLETE

| # | Task | Status | Owner | Evidence/Result |
|---|------|--------|-------|-----------------|
| 16 | **Decision: FakePlayer vs AIPlayerEngine protocol rewrite** | done | System | Chose AIPlayerEngine (external socket, unmodified server) |
| 17 | Write `scripts/count_ai_players.sh` | done | System | MySQL queries for ai_% players |
| 18 | Write `real_status.sh` | done | System | At `AIPlayerEngine/AIStatusLogs/real_status.sh`; DB=`gameserver` |
| 19 | Add packet logger for key packets | done | System | `protocol/PacketLogger.java` (CharInfo/StatusUpdate/NPC_INFO/ItemList/QuestInfo); BUILD SUCCESS |
| 20 | Add telemetry hooks to all 4 AI modules | done | System | PacketLogger in Combat/Quest/Merchant/Social AI; BUILD SUCCESS |
| 21 | Create telemetry dashboard script | done | System | `scripts/telemetry_dashboard.sh`, 5 sections |
| 22 | Add quest state logging | done | System | QUEST_LOG in QuestAI; BUILD SUCCESS |
| 23 | Add trading/buying/selling telemetry | done | System | TRADE_LOG in MerchantAI; BUILD SUCCESS |
| 24 | Add combat outcome logging | done | System | COMBAT_LOG in CombatAI (10 events); BUILD SUCCESS |
| 25 | Add social event logging | done | System | SOCIAL_LOG in SocialAI (7 events); BUILD SUCCESS |
| 26 | Add economic impact tracking | done | System | [ADENA_FLOW]/[PRICE_CHANGE] in MerchantAI; BUILD SUCCESS |
| 27 | Add performance metrics | done | System | [PERFORMANCE] in PerformanceMetrics; BUILD SUCCESS |
| 28 | Build `scripts/verify_telemetry.sh` | done | System | Validates all event types; BUILD SUCCESS |
| 29 | Write first telemetry RuntimeLog | done | System | `2026-08-02-telemetry-demonstration.md` |
| 30 | Baseline current behavior metrics | done | System | `scripts/baseline_metrics.sh`; BUILD SUCCESS |

---

## Part 2 — Perception & Movement (Tasks 31-42) ✅ COMPLETE

| # | Task | Status | Owner | Evidence/Result |
|---|------|--------|-------|-----------------|
| 31 | Audit existing perception systems | done | System | `Audit/PART2-01-perception-systems.md`; BUILD SUCCESS |
| 32 | Implement real enemy detection | done | System | `CombatAI.detectNearbyEnemy()` uses PacketLogger EntityInfo; unit/mock — not live-verified |
| 33 | Implement real HP/MP tracking | done | System | StatusUpdate parsing in PacketLogger; unit/mock — not live-verified |
| 34 | Implement real position tracking | done | System | CharInfo parsing (playerX/Y/Z/heading); BUILD SUCCESS |
| 35 | Implement real inventory awareness | done | System | ItemList parsing (adena, inventoryUsage%); BUILD SUCCESS |
| 36 | Implement real quest state tracking | done | System | QuestInfo parsing (activeQuestCount); BUILD SUCCESS |
| 37 | Fix protocol to parse key packets | done | System | CharInfo/StatusUpdate/ItemList parse; BUILD SUCCESS |
| 38 | Add entity tracking system | done | System | findNearestEntity/hasHostileNearby/clearEntities; BUILD SUCCESS |
| 39 | Implement line-of-sight checks | done | System | `LineOfSight.java` (Bresenham, 3D); BUILD SUCCESS |
| 40 | Add aggro/emotion detection | done | System | `AggroManager.java` threat/emotion; BUILD SUCCESS |
| 41 | Implement threat table | done | System | AggroManager history/modifiers/decay/priority; BUILD SUCCESS |
| 42 | Verify perception accuracy | done | System | `PerceptionAccuracyTest` (17 tests) reconciled to real NPC_INFO layout + fixed 3 broken fixtures (Stream C); passes with real assertions |

---

## Part 3 — Combat AI (Tasks 43-63)

> Combat builds on perception with real enemy detection. **Note:** PvE live combat (B4) AND live PvP (B5)
> are now PROVEN — `CombatProbe` killed a Wolf/Keltir (18 `ATTACK` hits, exp 0→105, level 1→2); `PvPProbe`
> two-bot fight (attacker objId2:13 / objId3:12 + CombatBot_02 PvP damage). Advanced behaviors still unit/mock-only.

| # | Task | Status | Owner | Evidence/Result |
|---|------|--------|-------|-----------------|
| 43 | Audit existing `CombatAI.java` | pending | | |
| 44 | Audit existing `CombatState.java` | done | System | HP/MP%, DPS, combat summary; BUILD SUCCESS |
| 45 | Audit existing `CombatDecision.java` | done | System | reason tracking, PvP action types, toString; BUILD SUCCESS |
| 46 | Audit existing `CombatConfig.java` | done | System | PvP config, defensive thresholds, skill priority; BUILD SUCCESS |
| 47 | Implement real enemy detection (no Math.random) | done | System | `PacketLogger.findNearestHostile()` (NPC_INFO); 11/11 tests; not live-verified |
| 48 | Implement real HP/MP tracking | done | System | StatusUpdate (0x0E); 11/11 tests; not live-verified |
| 49 | Implement targeting logic | done | System | EntityInfo + distance; real 3D distance via `CombatAI.calculateDistanceTo` (no Math.random) (Stream C) |
| 50 | Implement skill selection logic | done | System | selectBestSkill(), MP threshold; 11/11 tests; not live-verified |
| 51 | Implement defensive behavior | done | System | shouldDefend()/defensiveAction(); deterministic threat model from real HP+hostile count (no Math.random) (Stream C) |
| 52 | Implement retreat strategy | done | System | shouldRetreat()/retreat() escape direction; not live-verified |
| 53 | Add combat telemetry | done | System | logCombatTelemetry() actions/HP/MP/entities/latency |
| 54 | Test combat decisions in isolation | done | System | `CombatAITest.testCombatDecisionNotNull` now calls `makeDecision()` and asserts a real non-null decision+action (Stream C; was fake `assertTrue(true)`) |
| 55 | Fix any dead code from combat refactoring | done | System | Build clean; EntityTracker removed |
| 56 | Document combat AI in Audit docs | done | System | `Audit/15-combat-ai.md` |
| 57 | Verify combat doesn't break server stability | done | System | Build compiles, 4/4 tests, no dead code; not live-verified |
| 58 | Final combat integration test | done | System | 6 tests pass, getAttackRange() added; not live-verified |
| 59 | Start first combat test against live server | done | System | Superseded by B4 — `CombatProbe` is the live combat proof (`Audit/35`) |
| 60 | Verify AI players can engage NPCs | done | System | **LIVE-VERIFIED (B4)** — `CombatProbe` attacked a real Wolf/Elder Keltir: 18 `ATTACK`(0x05) hits, exp 0→105, level 1→2 (`Audit/35`, `scripts/b4_combat_prove.sh`) |
| 61 | Verify PvP combat logic | done | System | **LIVE-VERIFIED (B5)** — `PvPProbe` two-bot fight: attacker objId2:13 / objId3:12 hits + CombatBot_02 PvP damage (curHp 126→120) (`Audit/36`) |
| 62 | Implement advanced combat behaviors | done | System | calculateEscapeRoute()/getNearbyEntities(); + real Action(0x04)/AttackRequest(0x0A) encoders (PacketCodec, Stream C) |
| 63 | Verify PvP combat enhancements | done | System | `CombatAITest.testCombatAI_PvPMethods` now calls `makePvPDuidedDecision()` + asserts non-null decision/action and non-null karma/skill helpers (Stream C; was fake `assertTrue(true)` on the PvP path) |

---

## Part 4 — Goals & Long-Term Behavior (Tasks 64-77)

> Goals enable AI players to pursue long-term objectives. *(Renumbered from 59-74; duplicate combat rows 62/63 removed.)*

| # | Task | Status | Owner | Evidence/Result |
|---|------|--------|-------|-----------------|
| 64 | Audit AI goal systems (GoalTree, Goal, Strategy) | done | Stream D | Audit 36: no GoalTree/Goal/Strategy classes exist; LongTermGoalsAI was not even instantiated. advanced/neural classes instantiated but not driven. See `Audit/36-goal-personality-audit.md` |
| 65 | Implement short-term goals for AI players | done | Stream D | `GoalTree` class: SURVIVE/ACTIVE_QUEST/GRIND_XP/EXPLORE/SOCIAL/IDLE with priority + scheduling. Wired into AIPlayer.getGoalTree(). GoalTreeTest 6/6 PASS |
| 66 | Implement quest-based goal generation | done | Stream D | QuestAI.onQuestAccepted/onQuestCompleted/onQuestAbandoned hooks added; drive emotion+reinforcement+long-term goal. See RuntimeLog 2026-08-04-streamD1 |
| 67 | Implement social goals (party, clan) | done | Stream D | GoalTree.SOCIAL goal eligible when personality socialWeight>1.5 (SOCIAL personality). Priority scheduling in GoalTree.selectActiveGoal |
| 68 | Implement goal prioritization | done | Stream D | GoalTree priority enum (100..0) * personality weight; expired deadline force-promotes; stalled-goal demotion. GoalTreeTest proves SURVIVE>GRIND |
| 69 | Implement goal scheduling | done | Stream D | GoalTree.selectActiveGoal schedules one active goal/tick; 60s stall demotes; markProgress() resets timer |
| 70 | Audit neural/NeuralNetwork.java — Wire or Remove | done | Stream D | NeuralNetwork.java already removed (git); DeepLearningCore uses PatternMemory (functional). Audit 36 verdict: instantiated, not driven. DeepLearning wired in slice 1 via AdaptiveLearner |
| 71 | Audit neural/DeepLearningCore.java | done | Stream D | Functional (predict/learn/PatternMemory); was never fed from live path. Slice 1 wires learn() via ReinforcementEngine→AdaptiveLearner. predict() to be consulted in slice 2 |
| 72 | Audit advanced/EmotionalState.java | done | Stream D | Functional (onDeath/onLevelUp/onGoodLoot/decay); was never called. Slice 1 wires it from CombatAI + QuestAI hooks. StreamDFeedbackTest proves |
| 73 | Audit advanced/PersonalityProfile.java | done | Stream D | Functional (6 personalities + weights); weights NOW wired into CombatAI via getEffectiveDefendThreshold/getEffectiveEngageDistance (slice 2). GoalTreeTest proves AGGRESSIVE>CAUTIOUS engage range |
| 74 | Audit advanced/AdaptiveLearner.java | done | Stream D | Functional (learnCombat/Quest/Trade/Movement); was never called. Slice 1 feeds it via ReinforcementEngine. StreamDFeedbackTest proves counters increment |
| 75 | Audit advanced/ReinforcementEngine.java | done | Stream D | Functional (rewardKill/penalizeDeath/rewardQuestComplete); was never called. Slice 1 calls it from CombatAI/QuestAI hooks. StreamDFeedbackTest proves |
| 76 | Implement emotional responses to combat outcomes | done | Stream D | CombatAI.onKill/onDeath/onLevelUp/onItemDrop rewired from log-only to drive EmotionalState + ReinforcementEngine + AdaptiveLearner. 70/70 tests PASS |
| 77 | Document the full goal/personality system | done | Stream D | `Documentation/goal-personality-system.md` consolidated doc: pipeline + all 5 subsystems + outcome-hook table + verification. RuntimeLogs D1/D2 + Audit 36. 76/76 tests PASS |

---

## Part 5 — Social & Economy (Tasks 78-91)

> Social and economy integration after combat is stable. *(Renumbered +3.)*

| # | Task | Status | Owner | Evidence/Result |
|---|------|--------|-------|-----------------|
| 78 | Implement real inventory-aware buy/sell logic | pending | | |
| 79 | Implement price-awareness/arbitrage | pending | | |
| 80 | Implement party formation behavior | pending | | |
| 81 | Implement clan application behavior | pending | | |
| 82 | Implement contextual chat behavior | pending | | |
| 83 | Audit social/CollectiveKnowledge.java | pending | | |
| 84 | Audit social/SwarmCoordinator.java | pending | | |
| 85 | Audit social/DiplomacyEngine.java | pending | | |
| 86 | Audit economy/EconomicEngine.java | pending | | |
| 87 | Audit economy/NetWorthOptimizer.java | pending | | |
| 88 | Implement activity scheduling | pending | | |
| 89 | Implement graceful reconnect/persistence | pending | | |
| 90 | Telemetry + tuning pass on social/economy behavior | pending | | |
| 91 | Document social/economy systems | pending | | |

---

## Part 6 — Multi-Agent Scale & QA (Tasks 92-103)

> Final QA and multi-agent infrastructure. *(Renumbered +3.)*

| # | Task | Status | Owner | Evidence/Result |
|---|------|--------|-------|-----------------|
| 92 | Split remaining backlog into agent work packages | pending | | |
| 93 | Onboard 2nd concurrent agent as pilot | pending | | |
| 94 | Add merge-conflict resolution protocol doc | pending | | |
| 95 | Build style consistency checker | pending | | |
| 96 | Run dead code verification via `verify_no_dead_code.sh` | pending | | |
| 97 | Full integration test | pending | | Spawn N players, run hours |
| 98 | Load/performance test | pending | | Test on target hardware |
| 99 | Security/abuse review | pending | | |
| 100 | Write "new agent cold-start" test | done | System | scripts/cold_start_test.sh: 17/17 PASS (exit 0); fresh context orients in ~1272 tokens (vs ~73k) |
| 101 | Update token budget doc | pending | | Measure actual token usage |
| 102 | Retrospective on original roadmap | pending | | Compare 103-task vs Level 0-9 |
| 103 | Define next task-cycle scope | pending | | Based on telemetry data |

---

## Sequencing Notes

1. **Part 0 (1-15)** is non-negotiable — must be completed first.
2. **Task 16** is critical — gates all future development.
3. **Parts 2-5 are ordered** — each builds on previous with real data.
4. **Part 1 (telemetry) comes first** — measure before optimizing.
5. **The real current gap is live verification** — Parts 2-3 are scaffolded but unverified against the running server. Close that before advancing Part 4+.
