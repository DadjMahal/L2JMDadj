# Audit 36 — Goal / Personality / Emotion Systems (Stream D, tasks 64, 70-75)

> Audit performed 2026-08-04 before Stream D implementation. Method: grep callers + read each class.

## Scope
Audited the Part 4 classes that the task board references:
`engine/LongTermGoalsAI`, `engine/QuestGoal`, `advanced/PersonalityProfile`,
`advanced/EmotionalState`, `advanced/AdaptiveLearner`, `advanced/ReinforcementEngine`,
`neural/DeepLearningCore`, `neural/PatternMemory`.

## Verdict: instantiated-but-not-driven (the "dead wiring" pattern)
Every one of these classes was **constructed inside `AIPlayer`'s constructor** (so they compiled
and existed as fields) but their **behavioural methods were never invoked from the live path**:

| Class | Where constructed | Live-path callers of its methods | Verdict |
|---|---|---|---|
| `LongTermGoalsAI` | **NOWHERE** (not even in AIPlayer ctor before this audit) | 0 callers of `getPrimaryGoal`/`advanceGoal` | fully dead; wired by Stream D |
| `PersonalityProfile` | `AIPlayer` ctor | 0 callers of weights | dead weights; to be wired to CombatAI |
| `EmotionalState` | `AIPlayer` ctor | 0 callers of `onDeath`/`onLevelUp`/... | dead; wired by Stream D |
| `AdaptiveLearner` | `AIPlayer` ctor | 0 callers of `learn*` | dead; wired by Stream D |
| `ReinforcementEngine` | `AIPlayer` ctor | 0 callers of `reward*`/`penalize*` | dead; wired by Stream D |
| `DeepLearningCore` | `AIPlayer` ctor | 0 callers of `predict`/`learn` | dead; to be consulted by decisions |
| `PatternMemory` | via `DeepLearningCore` | (functional, backing store) | becomes live once DeepLearning is fed |
| `CombatAI.onKill/onDeath/onLevelUp/onItemDrop` | `CombatAI` (log-only bodies) | 0 external callers | log-only stubs; wired by Stream D |

## Root finding
The combat/quest **decision** hooks (`onKill`, `onDeath`, `onLevelUp`, `onItemDrop`) existed on
`CombatAI` but (a) had no callers in the live `CombatLoop`/`AIBrain` and (b) only logged. So even
the emotion/learning classes that *were* constructed could never receive events. Stream D's first
job was therefore to **make these hooks actually mutate state** and then to **call them from the
live driver on real outcomes**.

## Stream D slice 1 fix (this commit)
- Added `AIPlayer` getters for all advanced subsystems (`getPersonality`, `getEmotions`,
  `getAdaptiveLearner`, `getReinforcement`, `getDeepLearning`, `getLongTermGoals`) — they had
  **no getters** before, so nothing could reach them even if it wanted to.
- Added the `longTermGoals` field + construction to `AIPlayer` (it was the only Part-4 class not
  even instantiated).
- Rewired `CombatAI.onKill/onDeath/onLevelUp/onItemDrop` from log-only to drive
  `EmotionalState` + `ReinforcementEngine` + `AdaptiveLearner` (and `LongTermGoalsAI` on level-up).
- Added `onKill(target, xpGained)` overload so XP-scaled rewards are recorded.
- Added `QuestAI.onQuestAccepted/onQuestCompleted/onQuestAbandoned` hooks that drive the same
  feedback chain (emotions + reinforcement + long-term goal progress).

## Proof
`StreamDFeedbackTest` (6 tests) asserts the chain end-to-end at the unit level:
- kill → excitement>0, combat-learned counter +1, PatternMemory non-empty, frustration unchanged
- death → frustration up, a (negative) learning action recorded
- levelUp → confidence up, player level set, MAX_LEVEL goal advanced
- quest completion (after a death) → confidence up, frustration down, quest-learned counter +1
- long-term goal selection by level/castle
- personality assigned from accountId with trade>combat weight for MERCHANT

**70/70 tests PASS (was 64/64), BUILD SUCCESS.** No regressions.

## Remaining (Stream D slice 2 / tasks 65-69, 76, 77)
- Call these hooks from the **live** `CombatLoop`/`QuestFlowLoop` on real StatusUpdate-derived
  outcomes (XP/HP deltas), not just unit-testable.
- Wire `PersonalityProfile` weights into `CombatAI` target selection (aggressive picks riskier mobs).
- Build the short-term `GoalTree` (task 65) + priority + scheduling (tasks 68-69) and have the live
  loop consult the active goal before `makeDecision()`.
- Document the system (task 77).
