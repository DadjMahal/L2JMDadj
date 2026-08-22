#!/usr/bin/env python3
"""GK-1 — validate generated knowledge JSON.

Usage: python3 scripts/datapack/validate.py [--files npcs.json ...]
Checks (SCHEMAS.md §Invariants):
  1. every expected file exists + parses as JSON;
  2. no null/absent 'id' on any record;
  3. coordinates within world bounds (spawns + npc.spawns);
  4. drop chances in open (0, 1].
Empty-but-valid files PASS (GK-1 acceptance).
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

EXPECTED = ["npcs.json", "items.json", "skills.json", "spawns.json", "quests.json", "shops.json"]


def named(value):
    """Human name of the record (best effort) for error messages."""
    return ", ".join(f"{k}={value.get(k)}" for k in ("id", "npcId") if value.get(k) is not None)


def check_file(name: str, out) -> int:
    path = _lib.TARGET_ROOT / name
    if not path.exists():
        print(f"[validate] MISSING {name}", file=out)
        return 1

    data = _lib.read_json(path)
    if data is None:
        print(f"[validate] UNPARSEABLE {name}", file=out)
        return 1
    if not isinstance(data, list):
        print(f"[validate] NOT-ARRAY {name} (expected top-level JSON array)", file=out)
        return 1

    issues = 0
    for rec in data:
        if not isinstance(rec, dict):
            print(f"[validate] {name}: non-object record {rec!r}", file=out)
            issues += 1
            continue

        # 2. no null/absent id.
        rid = rec.get("id") if "id" in rec else rec.get("npcId")
        if rid is None:
            print(f"[validate] {name}: record missing id ({named(rec)})", file=out)
            issues += 1

        # 3. coordinates in bounds.
        for spawn in rec.get("spawns", []) if isinstance(rec.get("spawns"), list) else []:
            x, y, z = spawn.get("x"), spawn.get("y"), spawn.get("z")
            if not _lib.in_world_bounds(x, y, z):
                print(f"[validate] {name}: {named(rec)} spawn coords out of bounds ({x},{y},{z})", file=out)
                issues += 1

        # 4. drop chances in (0, 1].
        for drop in rec.get("drops", []) if isinstance(rec.get("drops"), list) else []:
            ch = drop.get("chance") if isinstance(drop, dict) else None
            if not _lib.in_open_unit_interval(ch):
                print(f"[validate] {name}: {named(rec)} drop chance out of (0,1] ({ch})", file=out)
                issues += 1
    return issues


def main() -> int:
    ap = argparse.ArgumentParser(description="Validate generated knowledge JSON")
    ap.add_argument("--files", nargs="*", default=EXPECTED, help="which files to check")
    args = ap.parse_args()

    total = 0
    for name in sorted(set(args.files)):
        total += check_file(name, sys.stdout)
    print(f"[validate] files={len(args.files)} issues={total} (empty-but-valid passes)")
    return 1 if total else 0


if __name__ == "__main__":
    raise SystemExit(main())