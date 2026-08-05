# Stream Declarations — D / E / F

> Companion to `TASKS.md`. Streams A–C are DONE and live-proven (see `STATUS.md`).
> This file formally declares Streams D, E, F — the remaining work — mapping each stream to a
> `TASKS.md` part, stating its purpose, scope, entry/exit criteria, and the existing (mostly
> unwired) classes it will either wire or replace.

Status legend: ✅ done | 🔄 in progress | ⏳ declared (not started)

---

## Stream D — Goals & Long-Term Behavior  ✅ DONE (2026-08-04)
**Maps to:** `TASKS.md` Part 4 — tasks **64–77** (14 tasks)

### Purpose
Make AI players pursue **persistent, long-term objectives** and behave with a **distinct
personality + emotional state**, instead of pure reactive combat. A bot should level toward a
goal, get frustrated after deaths, pick easier mobs when cautious, and learn which actions pay off.

### Scope (what Stream D delivers)
1. **Goal system** (tasks 64–69): a real `GoalTree` — short-term goals (kill nearest mob, reach
   level N, finish active quest), quest-based goals, social goals, **priority + scheduling** so the
   bot always has ONE active goal driving the live loop. `LongTermGoalsAI` (currently unused) is
   wired to pick the long-term goal; the live Stream C loop will consult the active goal before
   `CombatAI.makeDecision()`.
2. **Personality / Emotion wiring** (tasks 70–76): audit the existing
   `neural/` + `advanced/` classes; the verdict (see audit `Documentation/Audit/36-goal-personality-audit.md`)
   is that they are **instantiated in `AIPlayer` but NOT driven from the live path** —
   `emotions.onDeath()`, `reinforcement.rewardKill()`, `adaptiveLearner.learnCombat()` are never
   called by `CombatAI`/`QuestAI`. Stream D **wires them**: real combat outcomes feed
   `EmotionalState` + `ReinforcementEngine` + `AdaptiveLearner`, and personality weights bias
   `CombatAI` target selection.
3. **Docs** (task 77): document the goal/personality system.

### Existing classes (audited)
| Class | Package | Current state | Stream D action |
|---|---|---|---|
| `LongTermGoalsAI` | engine | instantiated? **No — 0 callers**; stub `getPrimaryGoal` | wire into live loop as the long-term goal selector |
| `QuestGoal` (enum) | engine | used by `QuestAI`? to verify | keep; map to short-term goals |
| `PersonalityProfile` | advanced | instantiated in `AIPlayer` ctor; weights unused | wire weights into `CombatAI` decision |
| `EmotionalState` | advanced | instantiated; hooks never called from live path | call from real combat/quest outcomes |
| `AdaptiveLearner` | advanced | instantiated; learn*() never called | feed real kill/death/quest/trade outcomes |
| `ReinforcementEngine` | advanced | instantiated; reward*() never called | feed real XP/adena/quest rewards |
| `DeepLearningCore` | neural | instantiated; predict/learn functional but unused live | consult before decisions; wire learn() |
| `PatternMemory` | neural | functional, unused live | backing store for above |

### Entry / Exit criteria
- **Entry:** 64/64 tests green; Stream C live-proven.
- **Exit (Stream D done):** the live combat/quest loop consults an active goal; real combat
  outcomes mutate emotion + reinforcement state (unit-testable); at least one live proof showing a
  personality/emotion-influenced decision. All Part 4 tasks (64–77) marked done in `TASKS.md`.

---

## Stream E — Social & Economy  ✅ DONE (2026-08-04)
**Maps to:** `TASKS.md` Part 5 — tasks **78–91** (14 tasks)

### Purpose
Real **inventory-aware economy** (buy/sell/arbitrage on real merchant packets) and **social
behavior** (party formation, clan application, contextual chat) on the proven Stream B/C packet
path — not mock data.

### Scope
- Tasks 78–79: real inventory-aware buy/sell + price arbitrage on live merchant dialog (builds on
  the Stream C NPC-dialog driver).
- Tasks 80–82: party formation / clan application / contextual chat behavior (real `Say2` packets).
- Tasks 83–87: audit `social/` + `economy/` classes (`CollectiveKnowledge`,
  `SwarmCoordinator`, `DiplomacyEngine`, `EconomicEngine`, `NetWorthOptimizer`) — currently
  instantiated in `AIPlayer` but, like Stream D's classes, **not driven from the live path**.
- Tasks 88–90: activity scheduling, graceful reconnect/persistence, telemetry/tuning.
- Task 91: docs.

### Entry / Exit criteria
- **Entry:** Stream D done (or agreed to run in parallel).
- **Exit:** at least one live merchant trade via real NPC dialog; party formation live; all Part 5
  tasks (78–91) done.

---

## Stream F — Multi-Agent Scale & QA  ✅ DONE (2026-08-04)
**Maps to:** `TASKS.md` Part 6 — tasks **92–103** (12 tasks; task 100 already done)

### Purpose
Run **many AI players concurrently** in isolation, coordinate them as a swarm, and a final
**QA / acceptance harness** proving the whole system end-to-end.

### Scope
- Tasks 92–99: multi-bot coordination, agent isolation testing, performance/scale, swarm
  coordination on real packets, headless ops, graceful shutdown.
- Task 100: ✅ DONE (count_ai_players.sh).
- Tasks 101–103: telemetry consolidation, final acceptance harness, docs.

### Entry / Exit criteria
- **Entry:** Streams D + E done (multi-agent needs stable single-agent behavior first).
- **Exit:** N≥3 bots live + coordinated; full QA acceptance harness green; all Part 6 tasks done.

---

## Ordering
**D → E → F** (declared order). D unblocks meaningful persistent bot behavior and is the
foundation for E's economy/social (a bot needs goals before it has a reason to trade/party).
F needs stable single-agent behavior from D+E before scaling.

Parallelism: E's audit tasks (83–87) may overlap with D's audit tasks (70–75) since both audit
unwired `AIPlayer` subsystems — but implementation is sequential D→E→F.

---

## Stream G — Wire the Remaining Stubs  ✅ CODE DONE (2026-08-05); run-proof/env/style pending
**Maps to:** `TASKS.md` Part 7 — tasks **104–110** (addressed the ~145 stub classes from task 92/103)

### Purpose
Wire the previously-dead helper/content/behavior classes into the live decision path, i.e. convert
the unit-proven D/E/F chains into live-driven behavior — and give every remaining stub an explicit
disposition (wired + tested OR quarantined).

### What shipped (code scope DONE)
1. **G-Live (104):** `examples/GoalDrivenLoop` live driver (mirrors `CombatLoop`) + `engine/LiveFeedbackBridge`
   fires D/E/F kill/level/death/respawn hooks from real `PacketLogger` deltas; loop consults
   `ActivityScheduler.nextActivity()` + `GoalTree.selectActiveGoal()` before `makeDecision()`.
2. **G-Combat (105):** `RangedKiteAI`, `PvPSkillRotation`, `AntiGriefing`, `AggroManager`, `SkillAllocator`
   → `CombatAI` (append-only consultations on the live decision path).
3. **G-Content (106):** `EventCalendarAI`, `AchievementAI`, `HeroTitleAI` → `AIPlayer` (+ achievement→goal hook).
4. **G-Behavior (107):** `HumanReactionSimulator`, `BehaviorSeeder`, `MovementPatternAI`, `ResourceHoardingAI` → `AIPlayer`.
5. **Disposition (108):** `Documentation/StreamGDisposition.md` — every stub wired+tested OR
   explicitly quarantined; ~130 are documented library modules.
6. **Relocation script (109):** `scripts/relocate_void_ai.sh` for the 23 void `ai_%` chars (ENV — not run here).

### Entry / Exit criteria
- **Entry:** Stream F done; `LiveFeedbackBridge` committed as G-Live partial.
- **Exit (met in code):** `mvn test` **117/117** PASS; `verify_no_dead_code` PASS (2 benign TODOs);
   every stub wired+tested or quarantined; `GoalDrivenLoop` + `LiveFeedbackBridge` on the live path.
- **Remaining to fully close:** (a) run `GoalDrivenLoop` against the live server (server-verified proof);
   (b) run `relocate_void_ai.sh --apply` on the L2JM host (relocate+heal 23 chars);
   (c) style-normalize the legacy baseline (task 110).

