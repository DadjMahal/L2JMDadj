# Runtime Log — Stream D slice 2: GoalTree + personality-weighted combat (2026-08-04)

## Goal
Slice 1 wired the emotion/learning *feedback*. Slice 2 makes the bot actually *pursue* a goal and
behave differently per personality/emotion — not just react to the nearest mob.

## What slice 2 changed
1. **`GoalTree` (new class, tasks 65/68/69):** holds short-term goals (SURVIVE, ACTIVE_QUEST,
   GRIND_XP, EXPLORE, SOCIAL, IDLE) with a priority enum (100..0) and a per-tick scheduler.
   `selectActiveGoal()` rebuilds the eligible set from live state (HP, emotion, personality),
   scales each by the matching personality weight, force-promotes overdue-deadline goals, and
   demotes goals stalled >60s. Wired into `AIPlayer.getGoalTree()`.
2. **Personality weights into `CombatAI` (task 73):** two new public helpers —
   `getEffectiveDefendThreshold()` (base 0.3 ± safetyWeight ± emotion) and
   `getEffectiveEngageDistance()` (base target distance * combatWeight * emotion). CAUTIOUS /
   frustrated bots defend sooner and reach less far; AGGRESSIVE / excited bots reach farther and
   defend later. `shouldDefend()` and `detectNearbyEnemy()` now use these instead of constants.
3. **`PacketLogger.setCurHp/setMaxHp`** test/telemetry hooks (alongside the existing
   `setSelfObjectId`).

## Proof — `GoalTreeTest` (6 tests, all PASS)
```
defaultGoalIsGrindXpForLowLevelPlayer   PASS (healthy -> GRIND_XP)
surviveBeatsGrindWhenDefending           PASS (HP=10 -> SURVIVE priority 100 > GRIND 60)
exploreEligibleWhenBored                 PASS (12x onIdle -> BORED -> EXPLORE eligible)
aggressivePersonalityLowersDefendThreshold PASS (AGGRESSIVE threshold > CAUTIOUS)
aggressivePersonalityReachesFarther      PASS (AGGRESSIVE range > CAUTIOUS)
frustrationShortensEngageRange          PASS (3 deaths -> FRUSTRATED -> range shrinks)
```
Full suite: **76/76 tests PASS (was 70/70), BUILD SUCCESS.** No regressions.

## Stream D status after slice 2
Tasks 64-76 are DONE in `TASKS.md`. Only **task 77 (document the goal/personality system)**
remains — this RuntimeLog + Audit 36 + the GoalTree/CombatAI javadoc cover it; a final
consolidated doc will close the stream.

## What's still open for a LIVE proof (deferred to a Stream-D live run)
- Call the hooks from the live `CombatLoop`/`QuestFlowLoop` on real StatusUpdate XP/HP deltas
  (the unit tests prove the chain; a live run proves real-server events drive it).
- Have the live loop consult `goalTree.selectActiveGoal()` before `makeDecision()`.
