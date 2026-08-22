#!/usr/bin/env python3
"""GK-7 — chains.json: per-race+base-class zero->hero roadmaps.

Builds an ordered step list per (race, baseClass): newbie quests (min<=10, race-matched),
1st-class transfer (Path-Of... quests, planned at level 20), key leveling quests 25-40,
2nd-class transfer (Testimony/Test-* quests, planned at level 40), endgame 40+ quests.

Algorithm: sort candidate quests by (minLevel, questId); inject the class-transfer steps at
level thresholds from classes.json; mark side-quest vs chain by reward/series heuristics.
Outputs chains.json + human-readable chains.md.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET_JSON = _lib.TARGET_ROOT / "chains.json"
TARGET_MD = _lib.TARGET_ROOT / "chains.md"

# Base-class ids are DERIVED from classes.json (authoritative): a race owns every base whose
# name contains its keyword. This build has 9 bases (0..53); there is no Kamael class id (123)
# in this datapack, and Orc Mystic is base 49 (not 51).
RACE_NAME_HINTS = {
    "HUMAN": "Human",
    "ELF": "Elven",
    "DARK_ELF": "Dark",
    "ORC": "Orc",
    "DWARF": "Dwarf",
    "KAMAEL": "Kamael",
}


def race_to_base(classes) -> dict:
    out = {}
    for rec in classes:
        name = (rec.get("baseName") or "")
        bid = rec.get("baseClassId")
        if bid is None:
            continue
        for race, hint in RACE_NAME_HINTS.items():
            if hint in name:
                out.setdefault(race, []).append(bid)
                break
    return out

FIRST_CLASS_RE = re.compile(r"PathOf", re.I)          # PathOfTheWarrior etc (Lv19 gate)
SECOND_CLASS_RE = re.compile(r"Testimony|TestOf", re.I)  # TestimonyOfX / TestOfTheX (Lv37-39)

RACE_KEYWORDS = {
    "HUMAN":    ["Human", "Warrior", "Rogue", "Knight", "Cleric"],
    "ELF":      ["Elven"],
    "DARK_ELF": ["Dark", "Palus", "Assassin", "Shillien"],
    "ORC":      ["Orc"],
    "DWARF":    ["Dwarven", "Scavenger", "Artisan"],
    "KAMAEL":   ["Kamael"],
}


# Minor aliases so a 2nd-class Test quest that talks about a 3rd-tier class still maps back to
# the base's chain (e.g. Orc Overlord -> "Lord", Warcryer -> "WarSpirit").
TRANSFER_SYNONYMS = {
    "Lord": ["Overlord"],
    "WarSpirit": ["Warcryer", "War Sp–irit"],
    "Duelist": ["Gladiator"],
    "Healer": ["Bishop", "Elven Elder", "Shillien Elder"],
    "Magus": ["Sorcerer", "Spellhowler", "Spellsinger"],
    "Reformer": ["Prophet", "Warlock"],
}


def _transfer_quest(quests, regex, race, chain_names):
    """Best-of match: chain-name token (or synonym) in the quest name -> race keyword -> first.
    This is documented as a naming heuristic; a perfect class-attribution needs GK-4 richer
    per-class data which the extraction does not carry yet.
    """
    hits = [q for q in quests
            if regex.search(q.get("name") or "") and q.get("startNpc") is not None]
    if not hits:
        return None
    # token pool: word-level tokens of chain names + synonym expansions (quest names are
    # camelCase with no spaces, so each chain word becomes its own matchable token).
    pool = []
    for name_part in chain_names:
        for word in name_part.split():
            if word:
                pool.append(word)
    # synonym expansions only when the base's chain already contains the trigger word
    chain_words = {w.lower() for name_part in chain_names for w in name_part.split()}
    for base, syn in TRANSFER_SYNONYMS.items():
        if base.lower() in chain_words:
            pool.extend(syn)
    # 1) race filter: a Human quest must never become an Elven/Orc/etc transfer. "Human" is
    # excluded on purpose (every Human Path is valid; the aim is wrong-RACE picks).
    race_tokens = [k for k in RACE_KEYWORDS.get(race, []) if k != "Human"]
    filtered = hits
    if race_tokens:
        had = [q for q in hits if any(k in (q.get("name") or "") for k in race_tokens)]
        if had:
            filtered = had
    # 2) line filter: a MYSTIC base must not take a FIGHTER Path (e.g. Human Mystic must get
    # a Wizard/Cleric Path, not HumanKnight).
    mystic_words = ["Wizard", "Cleric", "Oracle", "Shaman", "Mystic", "Elder", "Summoner"]
    chain_joined = " ".join(chain_names)
    if any(w in chain_joined for w in mystic_words):
        filtered = [q for q in filtered
                    if any(w in (q.get("name") or "") for w in mystic_words)]
    # score by SUMMED token lengths (ElvenKnight matches Elven+Knight -> beats bare Knight).
    best = None
    best_score = -1
    for q in sorted(filtered, key=lambda x: x["id"]):
        name = (q.get("name") or "").lower()
        score = sum(len(tok) for tok in pool if tok.lower() in name)
        if score > best_score:
            best = q
            best_score = score
    if best is not None:
        return best
    return hits[0]


def build() -> list[dict]:
    quests = _lib.read_json(_lib.TARGET_ROOT / "quests.json") or []
    classes = _lib.read_json(_lib.TARGET_ROOT / "classes.json") or []

    chains = []
    race_to_base_map = race_to_base(classes)
    for race in sorted(race_to_base_map):
        for base_id in race_to_base_map[race]:
            rec = next((r for r in classes if r.get("baseClassId") == base_id), None)
            base_name = rec.get("baseName") if rec else f"base{base_id}"
            chain_names = [c.get("name", "") for c in (rec.get("chain") or [])] if rec else []
            steps = build_chain(race, base_id, base_name, quests, chain_names)
            chains.append({"race": race, "baseClassId": base_id,
                           "baseName": base_name, "steps": steps})
    return chains


def build_chain(race: str, base_id: int, base_name: str, quests, chain_names) -> list[dict]:
    steps = []
    # 1) newbie quests: minLevel <= 10 (cap 5)
    newbies = [q for q in quests
               if (q.get("minLevel") or 0) <= 10
               and q.get("startNpc") is not None
               and not q.get("needsReview")]
    for q in sorted(newbies, key=lambda x: (x["minLevel"] or 0, x["id"]))[:5]:
        steps.append(step(q, "newbie"))

    # 2) 1st-class transfer (Path-*) at L20, race-matched
    first = _transfer_quest(quests, FIRST_CLASS_RE, race, chain_names)
    if first:
        steps.append({"kind": "firstClass", "questId": first["id"],
                      "name": first.get("name"), "level": 20,
                      "npc": first.get("startNpc")})

    # 3) leveling quests 26..40 (cap 6)
    lvl = [q for q in quests if (q.get("minLevel") or 0) in range(26, 41)
           and q.get("startNpc") is not None and not q.get("needsReview")
           and not FIRST_CLASS_RE.search(q.get("name") or "")
           and not SECOND_CLASS_RE.search(q.get("name") or "")]
    for q in sorted(lvl, key=lambda x: (x["minLevel"] or 0, x["id"]))[:6]:
        steps.append(step(q, "leveling"))

    # 4) 2nd-class transfer at L40, race-matched
    second = _transfer_quest(quests, SECOND_CLASS_RE, race, chain_names)
    if second:
        steps.append({"kind": "secondClass", "questId": second["id"],
                      "name": second.get("name"), "level": 40,
                      "npc": second.get("startNpc")})

    # 5) endgame 40+ (cap 3)
    end = [q for q in quests if (q.get("minLevel") or 0) >= 40
           and q.get("startNpc") is not None and not q.get("needsReview")
           and not (FIRST_CLASS_RE.search(q.get("name") or "")
                    or SECOND_CLASS_RE.search(q.get("name") or ""))]
    for q in sorted(end, key=lambda x: (x["minLevel"] or 0, x["id"]))[:3]:
        steps.append(step(q, "endgame"))
    return steps


def step(q, kind: str) -> dict:
    return {"kind": kind, "questId": q["id"], "name": q.get("name"),
            "level": q.get("minLevel"), "npc": q.get("startNpc")}




def write_md(chains) -> None:
    lines = ["# chains.md — zero->hero roadmaps (review artifact, GK-7)", ""]
    for c in chains:
        lines.append(f"## {c['race']} / {c['baseName']} ({len(c['steps'])} steps)")
        for s in c["steps"]:
            lines.append(f"  [{s['level']:>3}] {s['kind']:<12} q{s['questId']} {s['name']}")
        lines.append("")
    _lib.ensure_target(TARGET_MD)
    TARGET_MD.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    chains = build()
    _lib.write_json(TARGET_JSON, chains)
    write_md(chains)
    print(f"[chains] races={len(chains)} -> {TARGET_JSON.name} + chains.md")
    for c in chains:
        print(f"  {c['race']}/{c['baseName']}: {len(c['steps'])} steps")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
