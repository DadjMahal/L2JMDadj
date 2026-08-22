#!/usr/bin/env python3
"""GK-9 — map.json extractor: teleports, zones, routes, spawn regions (travel nav).

Source (READ-ONLY, from the audit goldmine):
  teleporters/**/*.xml  (145 files: town/dungeon/others/chamberlain/doorman/clanhall)
  zones/*.xml           (2586 zone elements: Town/Peace/NoLanding/pvp live zones)
  Routes.xml            (14 scripted NPC walk routes, 229 waypoints)
  npcs.json spawn rows  (10,754 spawns; aggregated per world region for hunting nav)

Schema (SCHEMAS.md §map.json): every record carries `id` + `kind`
(teleporter | zone | route | spawnRegion) so one file feeds WB-07-style travel nav.
Teleporter/zone/route coordinates are validated in-world by validate.py.
"""
from __future__ import annotations

import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "map.json"


def extract_teleports() -> list[dict]:
    """One record per (npc, teleportType); destinations are the <location> rows."""
    recs: list[dict] = []
    for f in _lib.xml_files(_lib.DATA_ROOT / "teleporters"):
        cat = f.parent.name  # town/dungeon/others/chamberlain/doorman/clanhall
        root = _lib.parse_xml(f)
        if root is None:
            continue
        for npc in root.iter("npc"):
            nid = _lib.norm_id(npc.get("id"))
            if nid is None:
                continue
            for tel in npc.iter("teleport"):
                ttype = tel.get("type", "OTHER")
                dests = []
                for loc in tel.iter("location"):
                    x, y, z = (_lib.round_coord(loc.get(k)) for k in ("x", "y", "z"))
                    if None in (x, y, z):
                        continue
                    dests.append({
                        "name": _lib.norm_name(loc.get("name")) or "",
                        "x": x,
                        "y": y,
                        "z": z,
                        "feeId": _lib.norm_id(loc.get("feeId")),
                        "feeCount": _lib.norm_id(loc.get("feeCount")) or 0,
                    })
                if not dests:
                    continue
                recs.append({
                    "id": f"{nid}-{ttype}",
                    "kind": "teleporter",
                    "npcId": nid,
                    "category": cat,
                    "type": ttype,
                    "destinations": dests,
                })
    return recs


def extract_zones() -> list[dict]:
    """A zone footprint record per <zone>: name+type+shape, z-band, node polygon and centroid."""
    recs: list[dict] = []
    for f in _lib.xml_files(_lib.DATA_ROOT / "zones"):
        root = _lib.parse_xml(f)
        if root is None:
            continue
        for zone in root.iter("zone"):
            name = zone.get("name")
            if not name:
                continue
            try:
                minz, maxz = int(zone.get("minZ")), int(zone.get("maxZ"))
            except (TypeError, ValueError):
                continue
            nodes = []
            for nd in zone.findall("node"):
                x, y = _lib.round_coord(nd.get("X")), _lib.round_coord(nd.get("Y"))
                if x is None or y is None:
                    continue
                nodes.append({"x": x, "y": y})
            if not nodes:
                continue
            cx = round(sum(n["x"] for n in nodes) / len(nodes))
            cy = round(sum(n["y"] for n in nodes) / len(nodes))
            # Honest flag (GK-5 pattern): boss/raid/event zones (Valakas lair, Seed of
            # Annihilation, movies…) sit outside the playable-world box — real datap locations
            # but not travel-nav areas. Flag, never drop or fake.
            outside = any(
                not (_lib.WORLD_X_MIN <= n["x"] <= _lib.WORLD_X_MAX
                     and _lib.WORLD_Y_MIN <= n["y"] <= _lib.WORLD_Y_MAX)
                for n in nodes)
            rec = {
                "id": name,
                "kind": "zone",
                "name": name,
                "type": zone.get("type", ""),
                "shape": zone.get("shape", ""),
                "minZ": minz,
                "maxZ": maxz,
                "x": cx,
                "y": cy,
                "z": minz,  # in-world point (footprint at lowest floor)
                "nodes": nodes,
            }
            if outside:
                rec["needsReview"] = True
            recs.append(rec)
    return recs


def extract_routes() -> list[dict]:
    """Routes.xml — scripted NPC walk routes (waypoint nav ground-truth)."""
    root = _lib.parse_xml(_lib.DATA_ROOT / "Routes.xml")
    if root is None:
        return []
    recs: list[dict] = []
    for route in root.iter("route"):
        name = route.get("name")
        if not name:
            continue
        points = []
        for p in route.iter("point"):
            x, y, z = (_lib.round_coord(p.get(k)) for k in ("X", "Y", "Z"))
            if None in (x, y, z):
                continue
            points.append({
                "x": x,
                "y": y,
                "z": z,
                "delay": _lib.norm_id(p.get("delay")) or 0,
                "run": (p.get("run", "false").lower() == "true"),
            })
        if not points:
            continue
        recs.append({
            "id": name,
            "kind": "route",
            "name": name,
            "repeat": (route.get("repeat", "false").lower() == "true"),
            "repeatStyle": route.get("repeatStyle", ""),
            "points": points,
        })
    return recs


def extract_spawn_regions() -> list[dict]:
    """Aggregate the 10k+ spawn rows (npcs.json) per world region (zoneHint prefix).

    A region is the natural 'where do I hunt' unit: centroid + level band + density.
    """
    npcs = _lib.read_json(_lib.TARGET_ROOT / "npcs.json") or []
    agg: dict[str, list] = defaultdict(list)
    for rec in npcs:
        lvl = rec.get("level")
        for s in rec.get("spawns", []):
            hint = s.get("zoneHint", "") or ""
            parts = hint.split("_")
            region = "_".join(parts[:2]) if len(parts) >= 2 else (hint or "(none)")
            agg[region].append((s.get("x"), s.get("y"), s.get("z"), lvl))
    recs: list[dict] = []
    for region in sorted(agg):
        xs, ys, lvls = [], [], []
        for x, y, _z, lvl in agg[region]:
            if x is None or y is None:
                continue
            xs.append(x)
            ys.append(y)
            if lvl is not None:
                lvls.append(lvl)
        if not xs:
            continue
        recs.append({
            "id": region,
            "kind": "spawnRegion",
            "name": region,
            "spawnCount": len(agg[region]),
            "x": round(sum(xs) / len(xs)),
            "y": round(sum(ys) / len(ys)),
            "minLevel": min(lvls) if lvls else 0,
            "maxLevel": max(lvls) if lvls else 0,
        })
    return recs


def main() -> int:
    teleports = extract_teleports()
    zones = extract_zones()
    routes = extract_routes()
    regions = extract_spawn_regions()
    data = teleports + zones + routes + regions
    _lib.write_json(TARGET, data)
    print(f"[map] teleports={len(teleports)} zones={len(zones)} "
          f"routes={len(routes)} spawnRegions={len(regions)} -> {TARGET.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())