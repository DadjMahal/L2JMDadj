#!/usr/bin/env python3
"""GK-3 — classes.json extractor (race -> base -> 1st -> 2nd -> 3rd profession chains).

Parses stats/players/classList.xml (authoritative: each <class> has parentClassId).
Base classes have NO parent. Each non-base class derives its chain by walking parents
up to the base; tier = position in the chain (0 base, 1 first, 2 second, 3 third).
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "classes.json"
CLASS_LIST = _lib.DATA_ROOT / "stats" / "players" / "classList.xml"


CLASSES: dict[int, dict] = {}


def _load() -> None:
    root = _lib.parse_xml(CLASS_LIST)
    if root is None:
        return
    for c in root.iter("class"):
        cid = _lib.norm_id(c.get("classId"))
        if cid is None:
            continue
        CLASSES[cid] = {
            "name": (c.get("name") or "").strip(),
            "parent": _lib.norm_id(c.get("parentClassId")),
            "classId": cid,
        }


def chain_of(cid: int) -> list[int]:
    """[self, parent, ...] up to the base class (base = no parent)."""
    chain, seen = [], set()
    cur = cid
    while cur is not None and cur not in seen and cur in CLASSES:
        chain.append(cur)
        seen.add(cur)
        cur = CLASSES[cur]["parent"]
    return chain


def extract() -> list[dict]:
    """One record per base class: {baseClassId, baseName, chain:[{classId,name,parent}]}."""
    records = []
    for cid, c in sorted(CLASSES.items()):
        if c["parent"] is not None:
            continue  # only bases start a record
        descendants = [cid2 for cid2 in CLASSES if chain_of(cid2)[-1] == cid]
        ordered = sorted(descendants, key=lambda x: len(chain_of(x)))
        records.append({
            "baseClassId": cid,
            "baseName": c["name"],
            "chain": [
                {"classId": c2, "name": CLASSES[c2]["name"],
                 "tier": len(chain_of(c2)) - 1}
                for c2 in ordered
            ],
        })
    return records


def summary(data: list[dict]) -> None:
    """--summary prints 5 real base -> 3rd-profession chains (audit acceptance)."""
    print("[classes] race chains (base -> 1st -> 2nd -> 3rd):")
    names = {c["classId"]: c["name"] for c in CLASSES.values()}
    shown = 0
    for rec in data:
        if shown >= 5:
            break
        t3 = [n["classId"] for n in rec["chain"] if n["tier"] == 3]
        if not t3:
            continue
        path = list(reversed(chain_of(t3[0])))
        print(f"  {rec['baseName']} -> " + " -> ".join(names[c] for c in path[1:]))
        shown += 1


def main() -> int:
    _load()
    data = extract()
    _lib.write_json(TARGET, data)
    print(f"[classes] baseClasses={len(data)} totalClasses={len(CLASSES)} -> {TARGET.name}")
    if "--summary" in sys.argv:
        summary(data)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
