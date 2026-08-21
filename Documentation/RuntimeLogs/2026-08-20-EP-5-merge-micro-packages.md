# EP-5 RuntimeLog — Merge micro-packages

**Date**: 2026-08-20
**Commit**: 5e61be6b
**Task**: EP-5 (AUDIT_01) — file-count reduction: presets, director, humanize lows, BotProfile

## What changed
- **Presets 7 → 1**: `behavior/ClassPreset.java` now nests Archer/Fighter/Mage/Healer/Buffer as
  static classes + `forProfile()` (was PresetFactory logic, same dispatch/fallbacks). Deleted:
  PresetFactory, ArcherPreset, FighterPreset, MagePreset, HealerPreset, BufferPreset
- **Director 2 → 1**: NEW `behavior/Director.java` = DirectorAI singleton (getSpawnQueue,
  suggestNextRole) + nested `NameGenerator`. Deleted: DirectorAI.java, NameGenerator.java
- **humanize 9 → 4**: NEW `behavior/humanize/Humanization.java` (~810 lines) nests
  HumanizedRandom, BehavioralFingerprint, SessionVariance, ImperfectionInjector (with
  ReactionDelay/AFKModule private-nested inside it). Kept as own files: AntiDetectionEngine
  (12 importers), TimingJitter (6), InputRandomizer (ADE's dependency)
- **BotProfile moved** `behavior/` → `core/BotProfile.java` (git rename 98%)
- Import updates: BotBrain, StateMachine, ProfileStore, RotationFactory, ChatEngine,
  EngineWiring, LevelingPlanner, AntiDetectionEngine, TimingJitter, InputRandomizer,
  HumanizedRandomTest (nested-class imports keep call sites unchanged)
- MODE_PARTIAL_INDEX.md: EP-5-affected rows refreshed to post-merge paths

## Verification
- `mvn -o -f AIPlayerEngine/pom.xml test` → **414 tests, 0 failures, 0 errors**
- Main files: **230 → 218** (net −12, acceptance ≥12 ✓)
- `grep` confirms zero references to deleted class names outside their new nested homes

## Notes
- First compile pass failed: same-package users of the merged classes had NO import lines (my
  caller survey grepped for `humanize.ClassName` which misses package-local references), and an
  earlier `| head` truncated the BotProfile importer list. Fixed by re-grepping raw names.
- The PROMPT's target layout (cabinet/, director/, imperfection/ as dirs) no longer existed —
  EP-3 had already flattened those to behavior/ root single files. Merge targets adapted to
  post-EP-3 reality; net effect matches the audit's intent.
- Director/NameGenerator currently have zero external callers (kept, not deleted — they are the
  EP-7/living-server casting seam; a follow-up task should wire Director into the launcher).
