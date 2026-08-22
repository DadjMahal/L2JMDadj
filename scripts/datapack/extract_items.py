#!/usr/bin/env python3
"""GK-3 — items.json extractor.

Parses stats/items/*.xml: <item id type name> + nested <set name=... val=...> plus <stats>.
Schema: {id, name, grade, type, slot, price, weaponType, crystal}.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "items.json"


def _sets(item) -> dict:
    """Map of set-name -> val, from all <set name=... val=...> under the item."""
    out = {}
    for s in item.findall("set"):
        name = s.get("name")
        val = s.get("val")
        if name:
            out[name] = (val or "").strip()
    return out


def extract_record(item) -> dict:
    item_id = _lib.norm_id(item.get("id"))
    if item_id is None:
        return None
    sets = _sets(item)
    weapon_type = ""
    if item.get("type") == "Weapon":
        wt = (sets.get("weapon_type") or "").upper()
        weapon_type = wt  # e.g. "SWORD", "BOW", "BIGSWORD"
    # Grade -> crystal grade ("NONE" when absent, else D/C/B/A/S80...).
    grade = sets.get("crystal_type", "none").upper()
    return {
        "id": item_id,
        "name": _lib.norm_name(item.get("name")),
        "type": (item.get("type") or "").strip(),
        "grade": grade,
        "slot": sets.get("bodypart", ""),
        "weaponType": weapon_type,
        "price": _lib.norm_id(sets.get("price")) or 0,
        "crystal": _lib.norm_id(sets.get("crystal_count")) or 0,
    }


def extract() -> list[dict]:
    records = []
    for f in _lib.xml_files(_lib.DATA_ROOT / "stats" / "items"):
        root = _lib.parse_xml(f)
        if root is None:
            continue
        for item in root.iter("item"):
            r = extract_record(item)
            if r is not None:
                records.append(r)
    return sorted(records, key=lambda r: r["id"])


def main() -> int:
    data = extract()
    _lib.write_json(TARGET, data)
    print(f"[items] records={len(data)} -> {TARGET.name}")
    print(f"[items] grades={sorted({r['grade'] for r in data})}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())