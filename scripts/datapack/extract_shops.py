#!/usr/bin/env python3
"""GK-1 stub — shops.json extractor (from buylists/ + multisell/; real parsing lands in GK-5)."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "shops.json"
BUYLISTS_ROOT = _lib.DATA_ROOT / "buylists"
MULTISELL_ROOT = _lib.DATA_ROOT / "multisell"


def count_candidate_records() -> int:
    buylist = len(_lib.xml_files(BUYLISTS_ROOT)) if BUYLISTS_ROOT.is_dir() else 0
    multisell = len(_lib.xml_files(MULTISELL_ROOT)) if MULTISELL_ROOT.is_dir() else 0
    return buylist + multisell


def extract() -> list[dict]:
    """GK-5 fills this: [{npcId, items:[{itemId, price, count}], multisell[]}]."""
    return []


def main() -> int:
    _lib.write_json(TARGET, extract())
    print(f"[shops] source=buylists+multisell files={count_candidate_records()} -> {TARGET.name} (empty)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())