#!/usr/bin/env python3
"""GK-1 stub — spawns.json extractor (real parsing lands in GK-2)."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "spawns.json"
SPAWNS_ROOT = _lib.DATA_ROOT / "spawns"


def count_candidate_records() -> int:
    total = 0
    for f in _lib.xml_files(SPAWNS_ROOT):
        root = _lib.parse_xml(f)
        if root is not None:
            total += sum(1 for _ in root.iter("spawn"))
    return total


def extract() -> list[dict]:
    """GK-2 fills this: [{npcId, x, y, z, zone}]."""
    return []


def main() -> int:
    files = _lib.xml_files(SPAWNS_ROOT)
    _lib.write_json(TARGET, extract())
    print(f"[spawns] source=spawns files={len(files)} candidates={count_candidate_records()} -> {TARGET.name} (empty)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())