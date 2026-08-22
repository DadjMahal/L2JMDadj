#!/usr/bin/env python3
"""GK-11 — trainers.json extractor: skill-trainer NPC → class mapping + world position.

Source (READ-ONLY):
  SkillLearn.xml    trainer-NPC -> <classId> rows (the class-change/skill-learn gates)
  spawns/**/*.xml   the <npc id x y z/> POINT spawns (village NPCs) — the polygon
                    spawns already in npcs.json only cover mob regions, so this is
                    the honest world location of each trainer.
  npcs.json         trainer display name.

Output: one record per trainer {id (npc id), kind: "trainer", name, classIds[],
spawn {x,y,z}|null}. classIds ARE the authoritative SkillLearn classes; the spawn is
the first point-spawn found (deterministic across sorted files).
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "trainers.json"


def point_spawns() -> dict[int, dict]:
    """npcId -> first point-spawn {x,y,z} found in spawns/** (deterministic sort)."""
    out: dict[int, dict] = {}
    for f in _lib.xml_files(_lib.DATA_ROOT / "spawns"):
        root = _lib.parse_xml(f)
        if root is None:
            continue
        for spawn in root.iter("spawn"):
            for npc in spawn.findall("npc"):
                nid = _lib.norm_id(npc.get("id"))
                x = _lib.round_coord(npc.get("x"))
                y = _lib.round_coord(npc.get("y"))
                z = _lib.round_coord(npc.get("z"))
                if nid is None or x is None or y is None or z is None:
                    continue
                if nid in out:
                    continue
                out[nid] = {"x": x, "y": y, "z": z}
    return out


def extract() -> list[dict]:
    root = _lib.parse_xml(_lib.DATA_ROOT / "SkillLearn.xml")
    if root is None:
        return []
    npcs = {n["id"]: n for n in (_lib.read_json(_lib.TARGET_ROOT / "npcs.json") or [])}
    spawns = point_spawns()
    recs = []
    for npc in root.iter("npc"):
        nid = _lib.norm_id(npc.get("id"))
        if nid is None:
            continue
        class_ids = sorted({int(c.text) for c in npc.findall("classId") if c.text})
        if not class_ids:
            continue
        rec = {
            "id": nid,
            "kind": "trainer",
            "name": npcs.get(nid, {}).get("name", ""),
            "classIds": class_ids,
            "spawn": spawns.get(nid),
        }
        recs.append(rec)
    return sorted(recs, key=lambda r: r["id"])


def main() -> int:
    data = extract()
    _lib.write_json(TARGET, data)
    with_spawn = sum(1 for r in data if r.get("spawn"))
    n_classes = len({c for r in data for c in r["classIds"]})
    print(f"[trainers] trainers={len(data)} (with-spawn {with_spawn}) "
          f"classes={n_classes} -> {TARGET.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())