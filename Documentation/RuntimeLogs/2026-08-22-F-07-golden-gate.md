# RuntimeLog — 2026-08-22 F-07 golden gate

**Task:** F-07 — `scripts/gate.sh`: golden gate = tests + style + secret-lint, one offline command.

## What changed
1. **`scripts/gate.sh`** (new, executable): stages `tests` (mvn -o test with a summary line),
   `style` (calls `scripts/check_style.sh`), `secret` (greps tracked code/config for likely
   secret literals; allows `.example`/`_archive/`/`attic/`); optional stage arg; exit 0 = green.
2. **`scripts/check_style.sh`** — fixed stale hardcoded `CODEBASE=/home/volodro/...` → repo-root
   relative (this gate now runs on any checkout).
3. Fixed **pre-existing style debt** the gate surfaced: trailing whitespace in 8 engine files
   (BotPlayController, DeathHandler, ItemDatabase, InventoryTracker, ConsumableManager,
   WeightMonitor, SoulshotRestocker, AutoLootHandler — each 1 line, whitespace only) and 2
   bare `TODO` comments → `LEGIT_TODO` (RecoveryFlow self-buff, GeoPathfinder GeoEngine —
   both tracked follow-ups).
4. `Documentation/WORKFLOW.md` §9 adds `scripts/gate.sh` as the golden verification command.

## Verification
```bash
scripts/gate.sh   # exit 0
# [PASS] mvn test green  | Tests run: 415 | Failures: 0 | Errors: 0 |
# STYLE CHECK PASSED (0 violations) · [PASS] no hardcoded secrets in tracked code/config
# ✔ GATE GREEN — merge-ready
```
Gate ran full before/after the debt fix — before: 2 FAIL groups; after: 0. 415 tests still
green on the engine after the Whitespace/TODO comment edits (whitespace + comment-only).