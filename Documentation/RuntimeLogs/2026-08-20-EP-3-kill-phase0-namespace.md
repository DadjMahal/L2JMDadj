# EP-3 RuntimeLog — Kill the phase0 namespace

**Date**: 2026-08-20
**Commit**: c049e612
**Task**: EP-3 (AUDIT_01) — Rename phase0/* packages, classes, variables, config keys

## What changed
- 138 source files + 30 test files moved to package-by-feature layout
- `phase0.*` packages → `behavior.{combat,humanize,hunting,inventory,lifecycle,movement,party,quest,social,town}`
- `phase0.guide` → `knowledge`; `advanced/` + `neural/` → `learning`
- `Phase0Wiring` → `CoreWiring`, `Phase0Integration` → `EngineWiring`, `Phase0Driver` → `EngineDriver`
- Config keys `phase0.*` → `engine.*` in ai-player.properties
- All `phase0.` variable/log references replaced; 148+ cross-subpackage imports added
- Inner-class/enum imports (Outer.Inner pattern) fixed across ~40 test files
- 90 dead classes remain in `attic/` from EP-1

## Verification
- `mvn -o -f AIPlayerEngine/pom.xml test` → **409 tests, 0 failures, 0 errors**
- `grep -ri "phase0" src/ --include='*.java' --include='*.properties'` → 0 matches

## Notes
- Biggest challenge: inner-class visibility after package split (Java requires explicit Outer.Inner import)
- Enum types (VendorType, ItemFate, RollDecision, Positioning, Objective, Intent, MovementState, BotState, PartyRole) all needed Outer.Inner imports in cross-package consumers
- EngineDriver uses `wiring` (CoreWiring) and `integration` (EngineWiring) to disambiguate
