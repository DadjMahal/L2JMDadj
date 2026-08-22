#!/usr/bin/env python3
"""GK-1 stub — skills.json extractor (from SkillLearn.xml; real parsing lands in GK-3)."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "skills.json"
SKILL_TREES = _lib.DATA_ROOT / "stats" / "players" / "skillTrees"
# SkillLearn.xml = trainer-NPC -> classId mapping only (no <skill> rows in this build).


def count_candidate_records() -> int:
    """Count every <skill> row across stats/players/skillTrees/** (recursive)."""
    total = 0
    if not SKILL_TREES.is_dir():
        return 0
    for f in _lib.xml_files(SKILL_TREES):
        root = _lib.parse_xml(f)
        if root is not None:
            total += sum(1 for _ in root.iter("skill"))
    return total


def extract() -> list[dict]:
    """GK-3 fills this: [{id, class, level, skillId, cost}]."""
    return []


def main() -> int:
    _lib.write_json(TARGET, extract())
    print(f"[skills] source=stats/players/skillTrees candidates={count_candidate_records()} -> {TARGET.name} (empty)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())