# RuntimeLog — 2026-08-22 GK-1 — Datapack extractor skeleton

## Task
GK-1 — Extractor skeleton: `scripts/datapack/` (_lib.py, per-domain stubs, extract_all.py,
SCHEMAS.md ≤150 lines, validate.py).

## What was done
Built the full Python skeleton wired to the read-only datapack (`SourceCode/dist/game/data/`):

- **`_lib.py`** — shared helpers: XML walk (`xml_files`/`parse_xml`), `norm_name` (strip tags/
  entities/diacritics), `norm_id`, coordinate rounding + **world-bounds** check (±204800 x/y,
  ±16000 z), **drop-chance (0,1]** check, target-dir ensure, stable `write_json`/`read_json`.
- **6 domain stubs** — `extract_npcs/items/skills/spawns/quests/shops.py`: each scans the real
  source, COUNTS candidate records honestly (deterministic), writes an EMPTY-but-valid JSON
  skeleton (real parsing is GK-2..GK-5).
- **`extract_all.py`** — runs all 6 stubs; prints per-domain file/candidate counts + overall
  result; nonzero on any failure.
- **`SCHEMAS.md`** (~90 lines) — field-by-field contract for every generated JSON file.
- **`validate.py`** — checks: file presence + parse, top-level array, no null/absent id,
  coords in world bounds, drop chances in (0,1]. **Empty-but-valid PASSES** (the GK-1 gate).

## Honest candidate counts (this Interlude build)
npcs 7,081 · items 9,215 · skills 13,997 (stats/players/skillTrees — SkillLearn.xml here is
trainer→classId only, no <skill> rows) · spawns 3,751 · quests 344 dirs · shops 711 files.

## Evidence / gate
- `extract_all.py` → **6/6 domains ok**, per-domain counts printed.
- `validate.py` → **issues=0** (empty-but-valid passes).
- `mvn -o test` untouched (scripts-only; gate unaffected).
- One commit set, pushed to master.