#!/usr/bin/env python3
"""GK-3 — skills.json extractor (per-class skill ladders from skillTrees/**)."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "skills.json"
SKILL_TREES = _lib.DATA_ROOT / "stats" / "players" / "skillTrees"
# SkillLearn.xml = trainer-NPC -> classId mapping only (no <skill> rows in this build).


def extract() -> list[dict]:
    """{id, class, level, skillLevel, cost} — one row per <skill levelUpSp> in a classSkillTree."""
    records = []
    for f in _lib.xml_files(SKILL_TREES):
        root = _lib.parse_xml(f)
        if root is None:
            continue
        for tree in root.iter("skillTree"):
            if tree.get("type") != "classSkillTree":
                continue
            class_id = _lib.norm_id(tree.get("classId"))
            parent_id = _lib.norm_id(tree.get("parentClassId"))
            for skill in tree.findall("skill"):
                skill_id = _lib.norm_id(skill.get("skillId"))
                if skill_id is None or class_id is None:
                    continue
                records.append({
                    "id": skill_id,
                    "class": class_id,
                    "parentClass": parent_id,
                    "level": _lib.norm_id(skill.get("getLevel")),
                    "skillLevel": _lib.norm_id(skill.get("skillLevel")),
                    "cost": _lib.norm_id(skill.get("levelUpSp")) or 0,
                })
    # dedupe (same id/class/level/skillLevel could appear more than once across trees)
    uniq = {}
    for r in records:
        key = (r["id"], r["class"], r["level"], r["skillLevel"])
        uniq.setdefault(key, r)
    return sorted(uniq.values(), key=lambda r: (r["class"], r["level"], r["id"]))


def main() -> int:
    data = extract()
    _lib.write_json(TARGET, data)
    classes = len({r["class"] for r in data})
    print(f"[skills] records={len(data)} classes={classes} -> {TARGET.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())