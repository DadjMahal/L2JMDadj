# EP-8 RuntimeLog — Documentation unification

**Date**: 2026-08-20
**Task**: EP-8 (AUDIT_01) — one consistent doc set for 10-minute onboarding

## What changed
- **AIPlayerEngine/README.md**: full rewrite — ASCII architecture diagram (FleetPlay→BotSession→
  behavior/protocol/knowledge), package map table (old phase0/engine paths → current),
  correct build/test/run commands, secrets pointer to fleet_env.local, cross-links.
- **README.md** (root): architecture one-liner updated from `com.aiplayer.engine` to
  `com.aiplayer.behavior` + `core.BotSession` + virtual threads + EP-6 token note.
- **STATUS.md**: rewritten from stale "Phase: PLAY, 374 green, 36/100 tasks" to current
  program state (Wave 1 COMPLETE, all 8 EPs with hashes, 415/415 green, next = Waves 2-5).
- **TASKS.md §4 ownership map**: replaced 4 stale `phase0/*` path rows with 7 current
  package-group rows covering behavior/, core/, net/protocol/web/etc., scripts/, Documentation/.
- **MODE_PARTIAL_INDEX.md**: all 15 rows updated to post-EP-3/EP-5 paths (no phase0/ prefix
  remains except in the header history note); EP-5 addendum table removed (merged into the
  main table); ProtocolExt name corrected.
- **START_HERE.md**: attic path fixed (→ AIPlayerEngine/attic/).

## Verification
- `grep -rin 'phase0' README.md START_HERE.md STATUS.md Documentation/*.md` → only historical
  changelog mentions remain ("was Phase0Brain", "phase0 purge", "phase0 namespace" etc.);
  no operational references to the deleted namespace.
- 10 spot-check link targets: 9/10 resolve (the 10th was `attic/` which lives at
  `AIPlayerEngine/attic/` — now fixed in START_HERE and engine README).
- Tests: not affected by docs-only changes (415/415 green from EP-7).

## Notes
- The root README's poetry header (lines 1-8) is preserved as-is — repo personality, not technical.
- No test count change needed (EP-8 is docs-only).
