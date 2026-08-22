#!/usr/bin/env python3
"""GK-1 stub — npcs.json extractor (real parsing lands in GK-2).

Scans stats/npcs/*.xml, counts candidate <npc> records, and writes an EMPTY-but-valid
npcs.json skeleton so validate.py passes before GK-2 fills the entries.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "npcs.json"


def count_candidate_records() -> int:
    """Count every <npc> element across all stats/npcs XML files (deterministic order)."""
    total = 0
    for f in _lib.xml_files(_lib.DATA_ROOT / "stats" / "npcs"):
        root = _lib.parse_xml(f)
        if root is not None:
            total += sum(1 for _ in root.iter("npc"))
    return total


def extract() -> list[dict]:
    """GK-2 fills this: [{id, name, level, hp, aggroRange, isAggressive, type, drops, spawns}]."""
    return []


def main() -> int:
    files = _lib.xml_files(_lib.DATA_ROOT / "stats" / "npcs")
    entries = count_candidate_records()
    _lib.write_json(TARGET, extract())
    print(f"[npcs] source=stats/npcs files={len(files)} candidates={entries} -> {TARGET.name} (empty skeleton)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())