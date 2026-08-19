# EP-2 — Relocate live engine classes to target packages; engine/ deleted

**Date:** 2026-08-20 · **Agent:** zcode (GLM 5.3) · **Status:** DONE

## Prompt (AUDIT_01 PROMPT EP-2)
Move the live engine classes into their target packages (net/, behavior/,
core/, cli/), update package/imports repo-wide, rename Phase0Config→EngineConfig
and Phase0Integration→EngineWiring; pure move+rename, no behavior change; engine/
directory deleted; `grep com.aiplayer.engine src/` → 0.

## What was done
1. Moved all **51** live engine classes (EP-1's compile-true set, not the audit's
   10 — see EP-1 RuntimeLog) + 18 engine-package test files:
   - `net/`: AIPlayer, AIPlayerConnection, GameServerClient
   - `core/`: AIConfiguration, EngineConfig (was Phase0Config), EngineWiring
     (was Phase0Integration), AIPlayerManager, AIPlayerState, PersistenceManager
   - `cli/`: AIPlayerEngine (old CLI entry, behavior kept)
   - `behavior/combat/` (11): CombatAI, CombatDecision, CombatFramePlanner,
     CombatState, CombatConfig, PKDecision, PvPSkillRotation, RangedKiteAI,
     AggroManager, AntiGriefing, SkillAllocator
   - `behavior/quest/` (6): QuestAI, QuestConfig, QuestDecision, QuestFramePlanner,
     QuestState, QuestGoal
   - `behavior/social/` (5): SocialAI, SocialConfig, SocialDecision, PartyState, ClanState
   - `behavior/town/` (4): MerchantAI, MerchantConfig, MerchantDecision, MerchantNPC
   - `behavior/` (15): AIBrain, AIAction, AIActionQueue, AIDecision, AIModuleLoader,
     BehaviorSeeder, HumanReactionSimulator, MovementPatternAI, GoalTree,
     LongTermGoalsAI, AchievementAI, EventCalendarAI, HeroTitleAI,
     ResourceHoardingAI, ActivityScheduler
   - tests moved to matching packages (CombatAITest→behavior/combat, …)
2. Rewrote every `import com.aiplayer.engine.X` repo-wide (incl. nested-type
   imports `engine.X.Y`); word-boundary renames Phase0Config→EngineConfig,
   Phase0Integration→EngineWiring across src/ (no collisions — Phase0IntegrationTest
   unaffected by \b).
3. Auto-added 62 files' worth of missing cross-package imports (the moved set
   referenced itself by simple name in the old single package).
4. Updated the one external reference (scripts/test_npc_engagement.sh CLI hint).

## Evidence
```
$ grep -rn "com\.aiplayer\.engine" src/ | wc -l
0
$ ls src/main/java/com/aiplayer/
advanced behavior cli core economy examples metrics monitor net neural protocol social web   (engine/ GONE)
$ mvn -o -f pom.xml clean test
[INFO] Tests run: 409, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Problems / notes
- First move-script run died mid-import-rewrite (grep no-match + pipefail);
  finished idempotently via Python pass. No file was double-moved (git mv failed
  loudly on the rerun before any sed ran).
- Auto-added imports are word-match based; a few may be technically unused
  (compile-clean but lintable). S1-T05 discipline: sweep them during EP-3's
  repo-wide rename pass rather than bloating this diff.
- `behavior/` now coexists with `phase0/` temporarily; EP-3 merges phase0's
  play/combat/quest/town/... trees INTO these behavior packages.

## Next
EP-3: phase0/** → behavior/knowledge/etc., purge the "phase0" token repo-wide
(Phase0Wiring→CoreWiring, Phase0Brain→BotBrain, …).
