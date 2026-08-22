#!/usr/bin/env python3
"""GK-1 stub — items.json extractor (real parsing lands in GK-3)."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "items.json"


def count_candidate_records() -> int:
    total = 0
    for f in _lib.xml_files(_lib.DATA_ROOT / "stats" / "items"):
        root = _lib.parse_xml(f)
        if root is not None:
            total += sum(1 for _ in root.iter("item"))
    return total


def extract() -> list[dict]:
    """GK-3 fills this: [{id, name, grade, slot/type, price, weaponType, crystal}]."""
    return []


def main() -> int:
    files = _lib.xml_files(_lib.DATA_ROOT / "stats" / "items")
    _lib.write_json(TARGET, extract())
    print(f"[items] source=stats/items files={len(files)} candidates={count_candidate_records()} -> {TARGET.name} (empty)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())