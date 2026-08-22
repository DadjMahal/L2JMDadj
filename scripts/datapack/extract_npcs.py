#!/usr/bin/env python3
"""GK-2 — npcs.json extractor (NPCs + spawns + drop tables).

Merges:
  stats/npcs/*.xml      -> id/name/level/hp/aggroRange/isAggressive/type + <dropLists> drops
  spawns/**/*.xml       -> per-npc spawn points (polygon centroid x/y + zone mid-z + zoneHint)

Drop chances are store in PERCENT (0..100) in the XML; the schema contract is fraction (0,1].
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "npcs.json"


def extract_npc_records() -> dict[int, dict]:
    """id -> record from stats/npcs xml (drops included)."""
    records: dict[int, dict] = {}
    for f in _lib.xml_files(_lib.DATA_ROOT / "stats" / "npcs"):
        root = _lib.parse_xml(f)
        if root is None:
            continue
        for npc in root.iter("npc"):
            npc_id = _lib.norm_id(npc.get("id"))
            if npc_id is None:
                continue
            vitals = npc.find("stats/vitals")
            hp = _lib.round_coord(vitals.get("hp")) if vitals is not None else None
            aggro = _lib.norm_id(npc.get("aggroRange"))
            records[npc_id] = {
                "id": npc_id,
                "name": _lib.norm_name(npc.get("name")),
                "type": (npc.get("type") or "").strip(),
                "level": _lib.norm_id(npc.get("level")),
                "hp": hp,
                "aggroRange": aggro if aggro is not None else 0,
                "isAggressive": (npc.get("isAggressive") or "false").lower() == "true",
                "drops": extract_drops(npc),
            }
    return records


def extract_drops(npc) -> list[dict]:
    """dropLists: <drop> nests items in <group>; <spoil> has <item> DIRECT children."""
    drops = []
    for dl in npc.findall("dropLists"):
        for drop in dl.findall("drop") + dl.findall("spoil"):
            for group in drop.findall("group"):
                for item in group.findall("item"):
                    parsed = parse_drop_item(item)
                    if parsed is not None:
                        drops.append(parsed)
            # spoil rows sit directly under <spoil> (no <group> wrapper).
            for item in drop.findall("item"):
                parsed = parse_drop_item(item)
                if parsed is not None:
                    drops.append(parsed)
    return drops


def parse_drop_item(item) -> dict | None:
    """One <item id min max chance/> (chance percent -> fraction clamped to 1.0)."""
    iid = _lib.norm_id(item.get("id"))
    if iid is None:
        return None
    pct = item.get("chance")
    chance = None
    if pct is not None:
        try:
            # Percent -> fraction; cap at 1.0 (chance=200 = multi-roll guarantee,
            # but a single-roll probability can't exceed 1 — honest clamp).
            chance = min(1.0, float(pct) / 100.0)
        except (TypeError, ValueError):
            chance = None
    return {
        "itemId": iid,
        "chance": chance,
        "min": _lib.norm_id(item.get("min")),
        "max": _lib.norm_id(item.get("max")),
    }


def extract_spawns() -> dict[int, list[dict]]:
    """npcId -> spawn points {x, y, z, zoneHint} (polygon centroid + territory mid-z)."""
    by_id: dict[int, list[dict]] = {}
    for path in _lib.xml_files(_lib.DATA_ROOT / "spawns"):
        root = _lib.parse_xml(path)
        if root is None:
            continue
        for spawn in root.iter("spawn"):
            xs, ys = [], []
            for node in spawn.iter("node"):
                x = _lib.round_coord(node.get("x"))
                y = _lib.round_coord(node.get("y"))
                if x is not None:
                    xs.append(x)
                if y is not None:
                    ys.append(y)
            zs = []
            terr = spawn.find("territory")
            if terr is not None:
                mn = _lib.round_coord(terr.get("minZ"))
                mx = _lib.round_coord(terr.get("maxZ"))
                if mn is not None and mx is not None:
                    zs = [mn, mx]
            if not xs or not ys:
                continue
            cx = sum(xs) // len(xs)
            cy = sum(ys) // len(ys)
            cz = (zs[0] + zs[1]) // 2 if len(zs) == 2 else None
            zone_hint = (spawn.get("zone") or "").strip()
            for npc in spawn.findall("npc"):
                nid = _lib.norm_id(npc.get("id"))
                if nid is None:
                    continue
                by_id.setdefault(nid, []).append(
                    {"x": cx, "y": cy, "z": cz, "zoneHint": zone_hint})
    return by_id


def extract() -> list[dict]:
    records = extract_npc_records()
    spawns = extract_spawns()
    for nid, rec in records.items():
        rec["spawns"] = spawns.get(nid, [])
    return sorted(records.values(), key=lambda r: r["id"])


def main() -> int:
    data = extract()
    npc_count = len(data)
    drop_rows = sum(len(r["drops"]) for r in data)
    spawn_rows = sum(len(r["spawns"]) for r in data)
    _lib.write_json(TARGET, data)
    print(f"[npcs] records={npc_count} drops={drop_rows} spawns={spawn_rows} -> {TARGET.name}")
    top50(data)
    spot_checks(data)
    return 0


def top50(data: list[dict]) -> None:
    """Top-50 mobs by summed chance×count (drop-value proxy until items.json join, GK-3)."""
    rows = []
    for r in data:
        value = 0.0
        drops = len(r["drops"])
        for d in r["drops"]:
            lo = d.get("min") or 0
            hi = d.get("max") or lo
            value += (d.get("chance") or 0) * (lo + hi) / 2.0
        rows.append((value, r["id"], r["name"], drops))
    rows.sort(reverse=True)
    print("[npcs] top-50 by drop value (id name drops value):")
    for value, nid, name, nd in rows[:50]:
        print(f"  {nid} {name} drops={nd} value={value:.1f}")


def spot_checks(data: list[dict]) -> None:
    """5 sanity rows vs the source XML: id, name, level, dropRows, spawnRows."""
    by_id = {r["id"]: r for r in data}
    print("[npcs] spot-checks (id, name, level, dropRows, spawnRows):")
    for target in (12077, 13031, 20223, 13032, 50004):
        r = by_id.get(target)
        if r:
            print(f"  {target} {r['name']} lvl={r['level']} dropRows={len(r['drops'])} spawnRows={len(r['spawns'])}")


if __name__ == "__main__":
    raise SystemExit(main())