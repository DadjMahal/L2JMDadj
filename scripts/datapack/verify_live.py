#!/usr/bin/env python3
"""GK-12 — Live-verification job: sample the generated knowledge JSON against LIVE server values.

What "live" means for this XML-datapack server: the game server loads NPC/item/skill/trainer
static data from `SourceCode/dist/game/data/**` (read-only ground truth) + its runtime
`gameserver` MySQL DB for character state. This job verifies in two honest parts:

  PART A (always runs, fully offline-safe): RE-SAMPLES a deterministic set of generated
  JSON values straight against the datapack files the RUNNING SERVER loads (stats/npcs,
  stats/items, SkillLearn.xml, teleporters, quest html). Any extractor drift or stale
  JSON breaks a sample -> the job FAILS (exit 1).

  PART B (best-effort, EP-6 secrets): when DB_USER/DB_PASS are configured in
  scripts/fleet_env.local (exactly like backup_db.sh), queries the real `gameserver`
  characters table for the live ai_% fleet and reports count/level range. When creds
  are absent or the DB is unreachable it prints an honest SKIP — never a fake pass.

Exit codes: 0 = samples green (DB may be SKIPped); 1 = a live-site sample MISMATCHED.
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

DATA = _lib.DATA_ROOT
KB = _lib.TARGET_ROOT


def first_xml(path: Path, tag: str, **attrs):
    """First element with `tag` matching the attrs; None when missing/parse-error."""
    root = _lib.parse_xml(path)
    if root is None:
        return None
    for el in root.iter(tag):
        if all(el.get(k) == str(v) for k, v in attrs.items()):
            return el
    return None


def first_scan(directory: Path, tag: str, **attrs):
    """First `tag` element matching attrs in any xml file under `directory`."""
    for f in _lib.xml_files(directory):
        root = _lib.parse_xml(f)
        if root is None:
            continue
        for el in root.iter(tag):
            if all(el.get(k) == str(v) for k, v in attrs.items()):
                return el
    return None


def attr(el, name: str) -> str | None:
    return el.get(name) if el is not None else None


def setval(el, name: str) -> str | None:
    """Value of a <set name=.. val=.. /> child (item XML)."""
    if el is None:
        return None
    for s in el.findall("set"):
        if s.get("name") == name:
            return s.get("val")
    return None


def check(no: int, label: str, ok: bool, detail: str) -> int:
    print(f"  [{no}] {label}: {'PASS' if ok else 'FAIL'} — {detail}")
    return 0 if ok else 1


def run_static() -> int:
    """Re-sample the generated JSON against the datapack the server loads."""
    fails = 0
    npcs = {r["id"]: r for r in (_lib.read_json(KB / "npcs.json") or [])}
    items = {r["id"]: r for r in (_lib.read_json(KB / "items.json") or [])}

    # sample 1: npc 20223 Mandragora Sprout (name + level).
    rec = npcs.get(20223)
    el = first_scan(DATA / "stats/npcs", "npc", id=20223)
    ok = rec is not None and el is not None \
        and rec["name"] == el.get("name") \
        and rec["level"] == int(el.get("level"))
    fails += check(1, "npc 20223 name+level", ok,
                   f"JSON({rec['name'] if rec else None}/{rec['level'] if rec else None}) vs "
                   f"XML({el.get('name') if el is not None else None}/"
                   f"{el.get('level') if el is not None else None})")

    # sample 2: item 1 Short Sword (name + weapon_type + price).
    rec = items.get(1)
    el = first_scan(DATA / "stats/items", "item", id=1)
    ok = rec is not None and el is not None \
        and rec["name"] == attr(el, "name") \
        and setval(el, "weapon_type") == "SWORD" \
        and setval(el, "price") == str(rec["price"])
    fails += check(2, "item 1 Short Sword", ok,
                   f"JSON(name={rec['name'] if rec else None}, price={rec['price'] if rec else None}) "
                   f"vs XML(weapon_type={setval(el, 'weapon_type')}, price={setval(el, 'price')})")

    # sample 3: trainer 30010 Auron teaches the Human Fighter line (SkillLearn.xml).
    trainers = {r["id"]: r for r in (_lib.read_json(KB / "trainers.json") or [])}
    t10 = trainers.get(30010)
    el = first_xml(DATA / "SkillLearn.xml", "npc", id=30010)
    n_cls = len(el.findall("classId")) if el is not None else -1
    ok = t10 is not None and el is not None \
        and len(t10["classIds"]) == n_cls \
        and sorted(t10["classIds"])[:3] == [0, 1, 2]
    fails += check(3, "trainers 30010 Auron", ok,
                   f"JSON classIds={len(t10['classIds']) if t10 else 0} vs XML={n_cls}")

    # sample 4: map Roxxy 30006 NORMAL teleport has Dwarven Village at real coords.
    roxxy = [r for r in (_lib.read_json(KB / "map.json") or [])
             if r["kind"] == "teleporter" and r["npcId"] == 30006 and r["type"] == "NORMAL"]
    dest = None
    if roxxy:
        for d in roxxy[0]["destinations"]:
            if d["name"] == "Dwarven Village":
                dest = d
                break
    el = first_xml(DATA / "teleporters/town/30006.xml", "location", name="Dwarven Village")
    ok = dest is not None and el is not None \
        and dest["x"] == int(el.get("x")) and dest["y"] == int(el.get("y"))
    fails += check(4, "Roxxy to Dwarven Village", ok,
                   f"JSON({dest['x'] if dest else '-'},{dest['y'] if dest else '-'}) vs "
                   f"XML({el.get('x') if el is not None else '-'},{el.get('y') if el is not None else '-'})")

    # sample 5: quest 6 giver + start dialog page exist in the live datapack.
    q6 = next((r for r in (_lib.read_json(KB / "quests.json") or []) if r.get("id") == 6), None)
    first_page = None
    for r in _lib.read_json(KB / "dialog.json") or []:
        if r.get("kind") == "questDialog" and r.get("id") == 6:
            first_page = (r.get("startPages") or [None])[0]
            break
    html6 = DATA / "scripts" / "quests" / "Q00006_StepIntoTheFuture"
    ok = q6 is not None and q6.get("startNpc") == 30006 and first_page \
        and (html6 / first_page).exists()
    fails += check(5, "quest 6 giver + start page exist", ok,
                   f"startNpc={q6.get('startNpc') if q6 else '-'} startPage={first_page} "
                   f"file={'yes' if (first_page and (html6 / first_page).exists()) else 'no'}")

    # sample 6: skills class-0 ladder matches the classSkillTree XML for classId=0.
    skills = _lib.read_json(KB / "skills.json") or []
    ladder = [s for s in skills if s.get("class") == 0]
    el = first_scan(DATA / "stats/players/skillTrees", "skillTree", classId=0)
    n_xml = len(el.findall("skill")) if el is not None else -1
    ok = bool(ladder) and n_xml > 0 and len(ladder) == n_xml
    fails += check(6, "skills class-0 ladder", ok,
                   f"JSON={len(ladder)} vs classSkillTree XML skills={n_xml}")
    return fails


def run_db() -> int:
    """Best-effort live fleet check; honest SKIP when creds/DB are unavailable."""
    env = {}
    loc = Path(__file__).resolve().parents[1] / "fleet_env.local"
    if loc.exists():
        with open(loc, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if line.startswith("export ") and "=" in line:
                    k, _, v = line[7:].partition("=")
                    if k in ("DB_USER", "DB_PASS"):
                        env[k] = v.strip('"').strip("'")
    user = env.get("DB_USER") or os.environ.get("DB_USER")
    pwd = env.get("DB_PASS") or os.environ.get("DB_PASS")
    if not user or not pwd:
        print("  [DB] SKIP — no DB_USER/DB_PASS set (scripts/fleet_env.local)")
        return 0
    sql = "SELECT COUNT(*), MIN(level), MAX(level) " \
          "FROM characters WHERE account_name LIKE 'ai\\_%' ESCAPE '\\\\'"
    try:
        out = subprocess.run(
            ["mysql", f"-u{user}", f"-p{pwd}", "-N", "-s", "gameserver", "-e", sql],
            capture_output=True, text=True, timeout=12)
    except Exception as exc:  # noqa: BLE001 — honest skip on any failure
        print(f"  [DB] SKIP — DB unreachable ({exc})")
        return 0
    if out.returncode != 0 or not out.stdout.strip():
        err = out.stderr.strip()[:120]
        print("  [DB] SKIP — DB unreachable" + (f" — {err}" if err else ""))
        return 0
    cols = out.stdout.split()
    print("  [DB] live ai_% fleet (gameserver.characters): "
          f"count={cols[0]} minLevel={cols[1]} maxLevel={cols[2]}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="GK-12 live-verification job")
    ap.add_argument("--no-db", action="store_true", help="skip the DB sample")
    args = ap.parse_args()

    print("[verify-live] PART A — generated JSON vs live-server datapack")
    fails = run_static()
    if fails:
        print("[verify-live] FAILED samples — generated JSON no longer matches the "
              "datapack the server loads; re-run the extractors and commit.")
        return 1
    if not args.no_db:
        print("[verify-live] PART B — live fleet DB sample")
        run_db()
    print("[verify-live] OK — all samples match the live server datapack")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())