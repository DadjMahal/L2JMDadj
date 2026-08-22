# RuntimeLog — 2026-08-22 GK-3 — items.json + skills.json + classes.json

## Task
GK-3 — Items + skills + class-tree extractors → items.json / skills.json / classes.json.

## What was done
Three extractors implemented end-to-end against the read-only datapack:

1. **`extract_items.py` → items.json (9,215 records, 1.7MB)** — stats/items/*.xml
   `<item id type name>` + nested `<set name=... val=...>`; fields {id, name, type, grade
   (crystal_type D/C/B/A/S80 or NONE), slot (bodypart), weaponType (weapon_type for weapons),
   price, crystal}. Grades seen: NONE,D,C,B,A,S.
2. **`extract_skills.py` → skills.json (13,777 rows, 89 classes, 1.6MB)** —
   stats/players/skillTrees/** per-class `<skillTree type=classSkillTree classId parentClassId>`
   → `<skill skillId skillLevel getLevel levelUpSp/>`; deduped by (id,class,level,skillLevel).
   (SkillLearn.xml is trainer→classId only; the real per-class ladders live in skillTrees/.)
3. **`extract_classes.py` → classes.json (9 base classes, 89 total)** —
   stats/players/classList.xml (authoritative `<class classId parentClassId>`); every chain
   derived by walking parents to a base; each class carries {classId, name, tier} (0 base,
   1 first-prof, 2 second, 3 third). `--summary` prints 5 real base→3rd chains.

## REAL 5 chains (--summary, audit acceptance pasted)
```
  Human Fighter -> Warrior -> Gladiator -> Duelist
  Human Mystic  -> Human Wizard -> Sorcerer -> Archmage
  Elven Fighter -> Elven Knight -> Temple Knight -> Eva's Templar
  Elven Mystic  -> Elven Wizard -> Spellsinger -> Mystic Muse
  Dark Fighter  -> Palus Knight -> Shillien Knight -> Bladedancer
```

## Supporting changes
- `SCHEMAS.md`: + classes.json contract (baseClassId/baseName/chain{classId,name,tier}).
- `validate.py`: EXPECTED + classes.json; id-key check now also accepts `baseClassId` (classes
  records use `baseClassId`, not `id`).
- `extract_all.py`: runner now 7/7 domains.

## Evidence / gate
- `extract_all.py` -> **7/7 domains ok**; `validate.py` -> files=7 issues=0.
- One commit set, pushed to master.