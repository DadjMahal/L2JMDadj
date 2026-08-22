#!/usr/bin/env python3
"""GK-1 — run every datapack domain extractor stub.

Usage: python3 scripts/datapack/extract_all.py
Writes empty-but-valid skeleton JSON to AIPlayerEngine/src/main/resources/knowledge/
(GK-2..GK-5 replace the skeletons with real parsed entries.)
"""
from __future__ import annotations

import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

STUBS = ["extract_npcs", "extract_items", "extract_skills", "extract_spawns",
         "extract_quests", "extract_shops", "extract_classes", "build_chains",
         "extract_map", "extract_dialogs", "extract_trainers"]


def main() -> int:
    failed = 0
    for stub in STUBS:
        try:
            mod = importlib.import_module(stub)
            rc = mod.main() if hasattr(mod, "main") else 0
            if rc != 0:
                print(f"[extract_all] {stub} FAILED rc={rc}")
                failed += 1
        except Exception as exc:  # noqa: BLE001 — report + continue so one bad domain is visible
            print(f"[extract_all] {stub} ERROR: {exc}")
            failed += 1
    print(f"[extract_all] done: {len(STUBS) - failed}/{len(STUBS)} domains ok")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())