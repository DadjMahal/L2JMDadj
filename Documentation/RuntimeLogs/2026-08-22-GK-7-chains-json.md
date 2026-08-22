# RuntimeLog — 2026-08-22 GK-7 — chains.json (zero→hero roadmaps)

## Task
GK-7 — Zero→hero chain builder per race/class → chains.json (quests + levels + gear stages).

## What was done
`scripts/datapack/build_chains.py` — builds a focused ordered roadmap per (race, baseClassId)
from the GK-4 quests + GK-3 classes:
- **newbie** (minLevel ≤ 10, cap 5) → **1st-class transfer** (Path-* quest) at L20 →
  **leveling** (L26..40, cap 6) → **2nd-class transfer** (Test/Testimony) at L40 →
  **endgame** (40+, cap 3). Outputs chains.json (24K) + chains.md (review artifact, 8K).
- Base-class ids are DERIVED from classes.json (authoritative: 9 bases 0..53; Orc Mystic =
  49, no Kamael 123 in this build) — honest fix of my initial hardcoded ORC=51/KAMAEL=123.
- **Transfer selection is a named heuristic** (documented in SCHEMAS.md + here): race
  pre-filter (Human quest never becomes an Elven transfer) → line filter (a Mystic base never
  takes a Fighter Path) → chain-token scoring. Not a perfect class attribution — that needs
  per-class quest data the extraction doesn't carry yet.

## Pasted (audit acceptance) — Human Fighter + Orc Mystic
```
HUMAN Human Fighter  16 steps | newbie q1-q153 | L20 PathOfTheHumanKnight | L40 TestOfSagittarius
ORC  Orc Mystic     16 steps | newbie q1-q153 | L20 PathOfTheOrcShaman   | L40 TestOfTheSummoner
```
All 9 (5 races × base classes) chains have 16 steps each (≥8 ✓); transfers at L20/L40 ✓;
validate.py green.

## Evidence / gate
- `extract_all.py` **8/8** (build_chains added last) · `validate.py` files=8 issues=0.
- One commit set, pushed to master.