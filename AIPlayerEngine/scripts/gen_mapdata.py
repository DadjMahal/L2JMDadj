#!/usr/bin/env python3
"""
WPT-09 — World map data source generator.

Derives real-coordinate map data for the dashboard from the L2 datapack:
  * regions.json    — region polygons from SourceCode/dist/game/data/zones/custom_town.xml
                      (TownZone zones, shape=NPoly real-coordinate polygons;
                       Cuboid zones converted to 4-corner rectangles).
  * landmarks.json  — town landmarks from DashboardApi.java's authoritative
                      TOWN_NAMES / TOWNS arrays (real world coords, meters).

Run:  python3 scripts/gen_mapdata.py
Outputs are written under AIPlayerEngine/src/main/resources/dashboard/data/.
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ZONES_XML = os.path.join(REPO, "SourceCode", "dist", "game", "data", "zones", "custom_town.xml")
API_JAVA = os.path.join(REPO, "AIPlayerEngine", "src", "main", "java", "com", "aiplayer", "web", "DashboardApi.java")
OUT_DIR = os.path.join(REPO, "AIPlayerEngine", "src", "main", "resources", "dashboard", "data")


def polygon_from_zone(zone):
    """Return a closed list of [x, y] pairs for a zone's shape, or None."""
    shape = zone.get("shape", "NPoly")
    nodes = zone.findall("node")
    if shape == "NPoly":
        pts = [[int(n.get("X")), int(n.get("Y"))] for n in nodes]
        if len(pts) < 3:
            return None
        return pts
    if shape == "Cuboid":
        if len(nodes) < 2:
            return None
        (x0, y0) = (int(nodes[0].get("X")), int(nodes[0].get("Y")))
        (x1, y1) = (int(nodes[1].get("X")), int(nodes[1].get("Y")))
        xs = sorted((x0, x1))
        ys = sorted((y0, y1))
        return [[xs[0], ys[0]], [xs[1], ys[0]], [xs[1], ys[1]], [xs[0], ys[1]]]
    return None


def centroid(poly):
    n = len(poly)
    cx = sum(p[0] for p in poly) / n
    cy = sum(p[1] for p in poly) / n
    return {"x": int(cx), "y": int(cy)}


def main():
    tree = ET.parse(ZONES_XML)
    regions = []
    for zone in tree.getroot().findall("zone"):
        if zone.get("type") != "TownZone":
            continue
        poly = polygon_from_zone(zone)
        if poly is None:
            print(f"  ! skip region with unusable shape: {zone.get('name')}")
            continue
        regions.append({
            "name": zone.get("name"),
            "id": zone.get("id"),
            "category": "town",
            "shape": zone.get("shape"),
            "minZ": int(zone.get("minZ", "0")),
            "maxZ": int(zone.get("maxZ", "0")),
            "center": centroid(poly),
            "polygon": poly,
        })

    # Landmarks from DashboardApi.java TOWN_NAMES / TOWNS arrays.
    src = open(API_JAVA, encoding="utf-8").read()
    names = re.findall(r'TOWN_NAMES\s*=\s*\{(.*?)\}', src, re.S)[0]
    town_names = [m.strip().strip('"') for m in names.split(",") if m.strip()]
    towns_block = re.findall(r'TOWNS\s*=\s*\n?\s*\{(.*?)\};', src, re.S)[0]
    town_coords = []
    for row in towns_block.split("{"):
        nums = re.findall(r'-?\d+', row)
        if len(nums) >= 3:
            town_coords.append([int(n) for n in nums[:3]])
    landmarks = []
    for i, name in enumerate(town_names):
        if i >= len(town_coords):
            break
        x, y, z = town_coords[i]
        landmarks.append({
            "name": name,
            "kind": "town",
            "x": x,
            "y": y,
            "z": z,
        })

    os.makedirs(OUT_DIR, exist_ok=True)
    with open(os.path.join(OUT_DIR, "regions.json"), "w", encoding="utf-8") as f:
        json.dump({"version": 1, "units": "meters", "source": "zones/custom_town.xml (TownZone NPoly/Cuboid)", "regions": regions}, f, indent=2)
    with open(os.path.join(OUT_DIR, "landmarks.json"), "w", encoding="utf-8") as f:
        json.dump({"version": 1, "units": "meters", "source": "DashboardApi.java TOWN_NAMES/TOWNS", "landmarks": landmarks}, f, indent=2)

    print(f"regions.json    : {len(regions)} regions -> {os.path.join(OUT_DIR, 'regions.json')}")
    print(f"landmarks.json  : {len(landmarks)} landmarks -> {os.path.join(OUT_DIR, 'landmarks.json')}")
    for r in regions:
        print(f"  - {r['name']:24s} pts={len(r['polygon']):3d} center=({r['center']['x']:7d},{r['center']['y']:7d})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
