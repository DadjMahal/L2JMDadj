# RuntimeLog — 2026-08-22 EB-11 — Config single-source + validation

## Task
EB-11 — Config single-source: all property reads funnel through EngineConfig with validation.

## What was done
Two concrete problems fixed the way the audit framed them:

**1. SINGLE-SOURCE (kills the duplicate file parses).** `CombatConfig` (used by the live
`CombatAI`) and `QuestConfig` (used by `QuestAI`) each independently re-parsed
`config/ai-player.properties` into their OWN `Properties` map — a second/third reader of the
same file, with divergent default-handling and no shared validation. Converted both into pure
**delegating facades**: they now hold no `Properties`, run no `loadConfiguration()`, and route
every getter through `AIConfiguration.getInstance()` — the ONE store the engine loads once.
Public getters keep the exact same keys + defaults, so callers (`CombatAITest` etc.) are
byte-for-byte unaffected (verified: its 20 CombatConfig tests stay green).

**2. VALIDATION.** `EngineConfig.validate()` (new) cross-checks the `engine.*` knobs it owns:
`movement.idle_route_ms >= 0`, `movement.min_radius >= 0`, `movement.max_radius >= min_radius`,
`reaction_base_ms >= 0`, `reaction_sigma_ms >= 0`, `reaction_outlier_pct in [0,100]`,
`reaction_outlier_ms >= 0`. Returns `List<ConfigIssue>` (empty = sane); each issue carries
field/value/message. Callers (e.g. a future FleetPlay boot check) can surface them.

## Evidence / gate
- New `EngineConfigTest` (6): pristine defaults → no issues; negative idle-route caught; max <
  min caught (cross-field); outlier pct out of range caught; CombatConfig + QuestConfig both
  surface a value set through AIConfiguration (proves single-store).
- **GATE GREEN — 540/540 tests** (was 534), style 0, secret-lint clean (exit=0).
- One commit set, pushed to master.