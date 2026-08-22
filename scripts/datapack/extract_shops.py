#!/usr/bin/env python3
"""GK-5 — shops.json extractor (buylists + multisell).

Schema (SCHEMAS.md §shops.json):
  [ {id, kind: "buylist"|"multisell", npcId (multisell: vendors; buylist: null + needsReview),
     items:[{itemId, price, count?}],                      // buylists
     offers:[{production:{itemId,count}, ingredients:[{itemId,count}]}]  // multisell
  } ]

HONEST boundary: the read-only buylist file does NOT carry which merchant NPC opens it
(the linkage lives in the server NPC-template layer, not in the datapack shop files). So
buylist records have npcId=null and are flagged needsReview — never guessed.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "shops.json"
BUYLISTS = _lib.DATA_ROOT / "buylists"
MULTISELL = _lib.DATA_ROOT / "multisell"


def extract_buylists() -> list[dict]:
    records = []
    for f in _lib.xml_files(BUYLISTS):
        lid = _lib.norm_id(f.stem)  # buylist file id = the shop id
        if lid is None:
            continue
        root = _lib.parse_xml(f)
        if root is None:
            continue
        items = []
        for it in root.iter("item"):
            iid = _lib.norm_id(it.get("id"))
            if iid is None:
                continue
            items.append({"itemId": iid, "price": _lib.norm_id(it.get("price")) or 0})
        if not items:
            continue
        records.append({
            "id": lid,
            "kind": "buylist",
            "npcId": None,
            "items": items,
            "offers": [],
            "needsReview": True,  # honest: vendor linkage not in the buylist file
        })
    return records


def extract_multisells() -> list[dict]:
    records = []
    for f in _lib.xml_files(MULTISELL):
        lid = _lib.norm_id(f.stem)
        if lid is None:
            continue
        root = _lib.parse_xml(f)
        if root is None:
            continue
        npcs = [_lib.norm_id(n.text) for n in root.iter("npc") if n.text and n.text.strip().isdigit()]
        npcs = sorted({n for n in npcs if n is not None})
        offers = []
        for item in root.findall("item"):
            prod_id = count = None
            ingredients = []
            for ing in item.findall("ingredient"):
                iid = _lib.norm_id(ing.get("id"))
                cnt = _lib.norm_id(ing.get("count"))
                if iid is not None:
                    ingredients.append({"itemId": iid, "count": cnt or 0})
            for prod in item.findall("production"):
                prod_id = _lib.norm_id(prod.get("id"))
                count = _lib.norm_id(prod.get("count"))
            if prod_id is not None:
                offers.append({"id": prod_id, "count": count or 0, "ingredients": ingredients})
        records.append({
            "id": lid,
            "kind": "multisell",
            "npcId": npcs,
            "items": [],
            "offers": offers,
            "needsReview": False,
        })
    return records


def extract() -> list[dict]:
    recs = extract_buylists() + extract_multisells()
    return sorted(recs, key=lambda r: r["id"])


def main() -> int:
    data = extract()
    buys = [r for r in data if r["kind"] == "buylist"]
    multis = [r for r in data if r["kind"] == "multisell"]
    _lib.write_json(TARGET, data)
    n_items = sum(len(r["items"]) for r in buys)
    n_offers = sum(len(r["offers"]) for r in multis)
    n_vendors = sum(len(r["npcId"]) for r in multis if r["npcId"])
    print(f"[shops] buylists={len(buys)} (items={n_items}) multisells={len(multis)} (offers={n_offers} vendors={n_vendors}) -> {TARGET.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
