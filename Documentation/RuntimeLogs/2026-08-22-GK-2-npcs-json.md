# RuntimeLog — 2026-08-22 GK-2 — npcs.json (NPCs + spawns + drop tables)

## Task
GK-2 — `npcs.json`: one record per NPC {id, name, level, hp, aggroRange, isAggressive, type,
drops, spawns} merged from stats/npcs/*.xml + spawns/**/*.xml.

## What was done
Implemented `scripts/datapack/extract_npcs.py` end-to-end:
- **stats/npcs/*.xml (95 files)** — `<npc id level type name aggroRange isAggressive>` +
  `<stats/vitals hp>`; drops from `<dropLists>` which contains BOTH `<drop>` (items nested in
  `<group>`) and `<spoil>` (items DIRECT children) — both parsed.
- **spawns/*.xml (186 files)** — `<spawn zone>` → polygon `<node>`s → centroid (x,y) + territory
  mid-z; one spawn row per `<npc id>` in that zone. `zoneHint` = the zone attribute.
- Drop chance: XML PERCENT ÷ 100, clamped to 1.0 (chance=200 = multi-roll guarantee).
- Build report: counts + top-50 mobs by drop value (chance×avg-count) + 5 spot-checks.

## REAL counts (honest — this Interlude datapack)
- records **6,541** (7,081 `<npc>` elements, 540 are stub duplicates with same id + no data —
  last-wins merge; nothing real lost)
- **drop rows 38,564** (the audit's "≥100k expected" was an estimate; the real datapack has
  ~38.5k drop+spoil rows) · **spawn rows 10,754** → npcs.json 6.3 MB
- Spot-checks: 12077 Wolf lvl15 dr0 spr0 · 13031 Huge Pig lvl70 dr1 spr0 · 20223 Mandragora
  Sprout lvl20 dr27 spr2 ✓ (20223 matches raw verify)
- Top: Valakas/Antharas (raid bosses) by drop value.

## Honest corrections (validator-driven)
1. World Y bound corrected → ±262,144 (real spawns reach y=253,542; the audit's ±204,800 was
   too tight for Y).
2. `<spoils` rows previously MISSED (they're direct-item, not grouped) — fixed.
3. `chance="200"` (boss multi-roll) clamped to 1.0.

## Evidence / gate
- `extract_all.py` 6/6 domains ok · `validate.py` files=6 issues=0 (npcs.json validated:
  ids non-null, coords in bounds, chances in (0,1]).
- One commit set, pushed to master.