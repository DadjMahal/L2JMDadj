#!/usr/bin/env python3
"""GK-1 stub — quests.json extractor (from scripts/quests/*.java; real parsing lands in GK-4)."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "quests.json"
QUESTS_ROOT = _lib.DATA_ROOT / "scripts" / "quests"


def count_candidate_records() -> int:
    return len([d for d in QUESTS_ROOT.iterdir() if d.is_dir()]) if QUESTS_ROOT.is_dir() else 0


def extract() -> list[dict]:
    """GK-4 fills this: [{id, name, startNpc, talkNpcs[], items[], rewards[], minLevel, chain}]."""
    return []


def main() -> int:
    if not QUESTS_ROOT.is_dir():
        print(f"[quests] WARN quests dir missing: {QUESTS_ROOT}")
    _lib.write_json(TARGET, extract())
    print(f"[quests] source=scripts/quests dirs={count_candidate_records()} -> {TARGET.name} (empty)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())