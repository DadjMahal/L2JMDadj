# AUDIT ORIENTATION — L2JM AI Player Engine

> **Purpose:** get an auditing/onboarding AI from zero to a confident mental model of the core in
> minutes. Read this top-to-bottom (~4 min), then pull the deeper docs via the routing table (§9).
> Everything here mirrors `STATUS.md` / `TASKS.md` / `START_HERE.md` (single source of truth = `STATUS.md`).

---

## 1. TL;DR
Autonomous AI **players for the L2JMobius Interlude server**. The engine is an **external socket
client** — it connects as a real game client, so the **server source is never modified**. It logs
in (login-server auth), enters the world, parses real server packets (`protocol/PacketLogger`),
makes a **deterministic, personality + emotion + goal-driven** decision (`engine/*AI`), and emits
real wire frames back to the server (`CombatFramePlanner` + `GameServerClient`). Built as 7 streams
(A–G); every stream A–G is **code-complete with passing tests** (117/117), live-proven where it
matters (B/C combat), and fully synced in the docs.

---

## 2. Repo map
```
L2JM/                         git root (branch master, origin = github DadjMahal/L2JMDadj)
├── TASKS.md                  roadmap 1-110 (Parts 0-7); 108 done, 2 open (109 env, 110 done now)
├── STATUS.md                 ★ single live snapshot — read first
├── START_HERE.md             fast orientation + routing table
├── Documentation/
│   ├── AUDIT_ORIENTATION.md  (this file)
│   ├── Streams.md            declarations + status of Streams D-G
│   ├── goal-personality-system.md   (Stream D)
│   ├── social-economy-system.md     (Stream E)
│   ├── StreamGDisposition.md        (Stream G: wire+test OR quarantine manifest)
│   ├── MultiAgentQA.md              (Stream F QA + Stream G scope)
│   ├── Audit/*.md            protocol/combat deep-dives (B-stream proofs 31-41)
│   └── RuntimeLogs/*.md      per-milestone logs
├── scripts/                  check_style.sh, verify_no_dead_code.sh, relocate_void_ai.sh,
│                             c5_live_combat_proof.sh, b*_prove.sh, cold_start_test.sh ...
└── AIPlayerEngine/           the Maven module (all AI code lives here)
    └── src/main/java/com/aiplayer/
        ├── engine/           ★ the AI core (see §4)
        ├── protocol/         real L2J wire: PacketLogger, PacketCodec, L2JProtocol, GameServerClient-ish
        ├── advanced/         PersonalityProfile, EmotionalState, AdaptiveLearner, ReinforcementEngine
        ├── neural/           DeepLearningCore, PatternMemory
        ├── social/           CollectiveKnowledge, SwarmCoordinator, DiplomacyEngine
        ├── economy/          MarketEngine, EconomicEngine, NetWorthOptimizer
        ├── monitor/ metrics/ PerformanceMetrics, AIMonitorDashboard
        └── examples/         LIVE drivers + proofs: CombatLoop, GoalDrivenLoop, CombatProbe, ...
    └── src/test/java/        JUnit5; per-stream tests (StreamD/E/F/G* tests)
```
**Build/test:** `cd AIPlayerEngine && mvn -o test` (117 tests). **Style:** `bash scripts/check_style.sh`
(passes, 0 violations). **Dead code:** `bash scripts/verify_no_dead_code.sh` (2 benign LEGIT_TODO).

---

## 3. Architecture (the mental model)
**External-socket, decision→send pipeline — one character per `AIPlayer`:**
```
[L2JM server] <--socket--> GameServerClient / L2JProtocol (login + enter-world handshake)
                 || background reader
                 \/ PacketLogger (parses CharInfo/NPC_INFO/StatusUpdate/ItemList/quest/etc.)
                 \/ CombatAI.makeDecision()  -- deterministic, biased by personality+emotion+goal
                 \/ CombatFramePlanner.plan(decision) -> ordered wire frames (Action 0x04, Attack 0x0A, Move 0x01)
                 \/ sendGameFrame(...)  -> back to server
```
**Feedback loop (Streams D/E + G-Live):** outcomes (kill/level/death/respawn/trade/party) mutate
`EmotionalState` + `ReinforcementEngine` + `AdaptiveLearner`/`PatternMemory`, promote `LongTermGoalsAI`
goals, and `GoalTree.selectActiveGoal()` + `ActivityScheduler.nextActivity()` pick what the bot does
next. `LiveFeedbackBridge` (Stream G) turns real packet deltas into those hooks.

**Determinism:** all decisions are deterministic (no `Math.random()` in the engine) — Streams D/E
removed every one; tests assert real behavior, never `assertTrue(true)`.

---

## 4. The core — read these first (in this order)
| Class | What it does | Stream |
|---|---|---|
| `engine/AIPlayer` | the central hub: owns personality/emotion/goals/scheduler + all 4 `*AI` modules + social/economy subsystems | D/E/G |
| `engine/CombatAI` | `makeDecision()` + `makePvPDuidedDecision()`; personality/emotion-biased engage distance + defend threshold; outcome hooks `onKill/onDeath/onLevelUp`; G-Combat helper consultations | C/D/G |
| `engine/GoalTree` | short-term goal selection (SURVIVE>ACTIVE_QUEST>GRIND_XP>EXPLORE>...) weighted by personality | D |

---

## 5. Streams A–G — what they did + proof state
| Stream | Scope | Result | Proof |
|---|---|---|---|
| **A** | cold-start orientation, real_status/count scripts | ✅ | `cold_start_test.sh` 17/17 |
| **B** | live login + enter-world + combat/PvP/quest/move/chat/party/trade | ✅ live | probes B1–B10; server-verified (`Audit/31-41`) |
| **C** | decision→send + live combat loop | ✅ live | `c5_live_combat_proof.sh`: server-confirmed damage; `CombatLoop` |
| **D** | goals/personality/emotion/reinforcement wiring | ✅ 76 tests | `GoalTreeTest`, `StreamDFeedbackTest` |
| **E** | inventory-aware trade + deterministic social + scheduler/reconnect/persistence | ✅ 92 tests | `StreamETrade/Social/SchedulerTest` |
| **F** | multi-agent isolation/shutdown/load + QA docs + style checker | ✅ 100 tests | `MultiAgentIntegrationTest`, `AgentLoadTest` |
| **G** | G-Live driver + wire 12 dead classes + disposition manifest | ✅ 117 tests | `StreamG*Test` (14), `LiveFeedbackBridgeTest`, `GoalDrivenLoop` |

## 6. How to verify (run these)
```bash
cd AIPlayerEngine && mvn -o test                       # 117 tests, BUILD SUCCESS
bash ../scripts/check_style.sh                          # STYLE CHECK PASSED (0 violations)
bash ../scripts/verify_no_dead_code.sh                  # build SUCCESS, 2 benign LEGIT_TODO
# live (needs L2JM server up: LoginServer :2106, GameServer :7777):
#   java -cp target/classes com.aiplayer.examples.CombatLoop ai_combat_01 ai123pass ...
#   java -cp target/classes com.aiplayer.examples.GoalDrivenLoop ai_combat_01 ai123pass ...
# proof scripts: bash scripts/c5_live_combat_proof.sh, b4/b5/b6/b7/b8/b9/b10_prove.sh
```

## 7. Honest state — the gaps (do NOT claim these done)
1. **Live run-proof of `GoalDrivenLoop`** (Stream G) — compiled + bridge unit-tested, but not run
   against the live server this session. This is the one remaining "server-verified proof."
2. **Relocate + heal 23 void `ai_%` chars** (TASKS 109) — ENV/DB op on the L2JM host; script written
   (`scripts/relocate_void_ai.sh --apply`), not executed here.
3. **`CombatAI.isTargetDead()`** needs real target-HP StatusUpdate attribution (uses DeleteObject now).
4. **`AIPlayerEngine` launcher** is a stub (LEGIT_TODO).
5. **E-Extra:** PatternMemory on-disk persistence; `DeepLearningCore.predict()` fed but not yet
   consulted inside `makeDecision()`.

## 8. Conventions (what an auditor should expect)
- **Logging, not `System.out`** — in the engine. `examples/` drivers print grep-able `[X]` markers by design.
- **Deterministic decisions** — no `Math.random()` in engine code.
- **LEGIT_TODO marker** = an accepted, tracked TODO (must be in `StreamGDisposition.md` §4).
- **External-socket only** — never modify the L2JM server source.
- **Verify-before-claim** is a hard rule (no fake logs, no `assertTrue(true)`).
- **Style:** 4-space indent, no tabs, no trailing whitespace (enforced by `check_style.sh`).

## 9. Routing table (pull deeper docs on demand)
| Need | Read |
|---|---|
| Any Stream D/E/F/G question | `Streams.md`, `STATUS.md`, `StreamGDisposition.md` |
| Protocol / real packets | `Audit/31-41` (B-stream proofs), `protocol/PacketLogger.java` |
| Goals/personality | `goal-personality-system.md`, `engine/GoalTree.java`, `advanced/` |
| Social/economy | `social-economy-system.md`, `engine/MerchantAI/SocialAI/ActivityScheduler.java` |
| Multi-agent/QA | `MultiAgentQA.md`, `engine/AIPlayerManager.java` |
| Workflow/agent rules | `AGENT_ONBOARDING.md`, `Documentation/WORKFLOW.md` |

| `engine/LongTermGoalsAI` | long-term goals (MAX_LEVEL, ACHIEVEMENT_RAID, ...) | D |
| `engine/ActivityScheduler` | rotates GRIND/MERCHANT/QUEST/SOCIAL/REST | E |
| `engine/QuestAI` / `MerchantAI` / `SocialAI` | the other three live decision modules (quest, trade, social/party) | C/E |
| `engine/AIPlayerManager` | multi-agent: spawn/despawn/shutdown/thinkAll + load & graceful shutdown | F |
| `protocol/PacketLogger` | the single source of parsed server state (HP, level, entities, adena, hostiles) | B/C/E |
| `examples/GoalDrivenLoop` | the live driver — wires bridge→scheduler→goal→combat→frames end-to-end | G |
