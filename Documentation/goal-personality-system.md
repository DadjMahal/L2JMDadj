# Goal & Personality System — Stream D (task 77)

> Consolidated doc for the AI player goal / personality / emotion / learning system.
> Implements `_archive_superseded/TASK_ROADMAP_110.md` Part 4 (tasks 64-77). Status: 14/14 tasks done.

## 1. Overview
The AI player behaves with **long-term objectives**, a **distinct personality**, an
**emotional state**, and **reinforcement learning** — not just reactive combat. The system is a
pipeline:

```
  live outcome (kill/death/level-up/quest)
        │
        ▼
  CombatAI / QuestAI outcome hooks        ← slice 1 wired these (were log-only stubs)
        │
        ▼
  EmotionalState + ReinforcementEngine   ← feelings + reward signal
        │
        ▼
  AdaptiveLearner → DeepLearningCore     ← record outcome in PatternMemory
        │
        ▼
  GoalTree.selectActiveGoal()             ← pick the short-term goal this tick
        │
        ▼
  CombatAI.makeDecision()                 ← personality/emotion bias target + range + defend
```

## 2. Components

### 2.1 Long-term aspiration — `engine/LongTermGoalsAI`
Picks the bot's overarching goal from level / castle count / noblesse status:
`MAX_LEVEL` → `GUILD_LEADERSHIP` (lv>60) → `ACHIEVEMENT_RAID` (lv>80) → `CASTLE_OWNERSHIP`
→ `NOBLESCE_TITLE`. Tracks progress per-goal (`advanceGoal`/`getGoalProgress`). Before Stream D
this class existed but was **never instantiated and never called**; now constructed in
`AIPlayer` and advanced on level-up / quest completion.

### 2.2 Short-term goals — `engine/GoalTree` (tasks 65/68/69)
Holds the goals relevant *right now* and schedules one active goal per tick:

| Goal | Priority | When eligible |
|---|---|---|
| SURVIVE | 100 | `CombatAI.shouldDefend()` (low HP or surrounded) |
| ACTIVE_QUEST | 80 | a quest is in progress |
| GRIND_XP | 60 | always (default leveling loop) |
| EXPLORE | 40 | emotion == BORED |
| SOCIAL | 20 | personality socialWeight > 1.5 (SOCIAL) |
| IDLE | 0 | nothing eligible |

**Selection:** `score = priority * personalityWeight`; an expired deadline force-promotes a goal
to the top; a goal active >60s with no progress is demoted (×0.1) so the bot doesn't get stuck.

### 2.3 Personality — `advanced/PersonalityProfile` (task 73)
Six personalities, each a set of decision weights (0.4–2.0, 1.0=neutral):
AGGRESSIVE (combat 1.8/safety 0.4), CAUTIOUS (0.6/1.9), SOCIAL (0.7 social 2.0),
MERCHANT (trade 2.0), EXPLORER (explore 2.0), COMPLETIONIST (quest 2.0).
Assigned by `accountId % 6`. These weights now **actually bias combat**:
`CombatAI.getEffectiveDefendThreshold()` and `getEffectiveEngageDistance()` use
safetyWeight/combatWeight so a CAUTIOUS bot defends sooner and engages closer, an AGGRESSIVE bot
reaches farther and defends later. (Before Stream D these weights were computed but never read.)

### 2.4 Emotion — `advanced/EmotionalState` (task 72)
Tracks frustration / excitement / boredom / confidence (each 0–1) and derives a discrete emotion
(NEUTRAL, FRUSTRATED, EXCITED, BORED, CONFIDENT, CAUTIOUS). Event hooks mutate the levels:
`onDeath` (frustration +0.3, confidence −0.2), `onLevelUp` (excitement +0.4, confidence +0.2),
`onGoodLoot` (excitement +0.2), `onIdle` (boredom +0.1), `onQuestComplete` (confidence +0.15,
frustration −0.2), `onProfitableTrade` (excitement +0.15). `decay()` returns levels toward
neutral. The emotion feeds back into combat thresholds (FRUSTRATED → defend sooner / reach less;
EXCITED → reach farther).

### 2.5 Reinforcement learning — `advanced/ReinforcementEngine` + `AdaptiveLearner` + `neural/DeepLearningCore`/`PatternMemory` (tasks 74/75/70/71)
- `ReinforcementEngine` maps outcomes to reward signals: `rewardKill` (XP×0.01),
  `penalizeDeath` (−2.0), `rewardTrade` (adena×0.001), `rewardQuestComplete` (+3.0),
  `penalizeQuestFail` (−1.0).
- `AdaptiveLearner` wraps these into domain calls (`learnCombat/Quest/Trade/Movement`) that
  record into `DeepLearningCore` under a context key (`"combat:Wolf"`, `"quest:Q00101"`).
- `DeepLearningCore.predict()` does epsilon-greedy (0.15 explore) over `PatternMemory`, which
  stores patterns with an exponential-moving-average reward and confidence that grows with
  occurrences. This is how the bot "gets better the more it plays."

Before Stream D all of these were **instantiated in `AIPlayer` but never invoked from the live
path** (0 callers — see Audit 36). Stream D slice 1 wired the outcome hooks to feed them.

## 3. Outcome hooks (the wiring)
| Event | Hook | Drives |
|---|---|---|
| Kill (with XP) | `CombatAI.onKill(target, xp)` | emotions.onGoodLoot, reinforcement.rewardKill, learner counter, PatternMemory |
| Death | `CombatAI.onDeath()` | emotions.onDeath, reinforcement.penalizeDeath |
| Level-up | `CombatAI.onLevelUp(level)` | setLevel, emotions.onLevelUp, longTermGoals.advanceGoal(MAX_LEVEL) |
| Loot | `CombatAI.onItemDrop(item)` | emotions.onGoodLoot |
| Respawn | `CombatAI.onRespawn(level)` | emotions.decay |
| Quest accepted | `QuestAI.onQuestAccepted(questId)` | emotions.onGoodLoot |
| Quest completed | `QuestAI.onQuestCompleted(questId)` | emotions.onQuestComplete, reinforcement.rewardQuestComplete, longTermGoals.advanceGoal |
| Quest abandoned | `QuestAI.onQuestAbandoned(questId)` | reinforcement.penalizeQuestFail |

## 4. Verification
- `StreamDFeedbackTest` (6 tests): kill→excitement+learned+PatternMemory; death→frustration+penalty;
  levelUp→confidence+goal; quest completion→emotion+reward; goal selection by level; personality assignment.
- `GoalTreeTest` (6 tests): default GRIND_XP; SURVIVE>GRIND when low HP; EXPLORE when bored;
  AGGRESSIVE threshold>CAUTIOUS; AGGRESSIVE range>CAUTIOUS; frustration shrinks range.
- Full suite **76/76 PASS, BUILD SUCCESS.**

## 5. Open / future work
- **Live driver integration:** call these hooks from the live `CombatLoop`/`QuestFlowLoop` on
  real `StatusUpdate`-derived XP/HP deltas (unit tests prove the chain; a live run proves real
  server events drive it). Have the live loop call `goalTree.selectActiveGoal()` before
  `makeDecision()`.
- **DeepLearningCore.predict() consultation:** learning is recorded but `predict()` is not yet
  consulted inside `makeDecision()` to pick the best skill/hunting spot — deferred.
- Persistence of `PatternMemory` across restarts (Stream E task 89).

