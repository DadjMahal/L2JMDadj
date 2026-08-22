#!/usr/bin/env python3
"""GK-4 — quests.json extractor (regex over the formulaic Java quest scripts + htm graph).

Per quest dir QNNNNN_Name: {id, name, minLevel, maxLevel?, races[], startNpc, talkNpcs[],
killNpcs[], items[], rewards[], htmGraph{npcId:[files]}, codeHints:{kindLines[]}}.
Ambiguous fields record null + needsReview flag — never guessed.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "quests.json"
QUESTS_ROOT = _lib.DATA_ROOT / "scripts" / "quests"

RACE_BITS = {"HUMAN": 1, "ELF": 2, "DARK_ELF": 4, "ORC": 8, "DWARF": 16, "KAMAEL": 32}

RE_SYMBOL_INT = re.compile(r"(?:private\s+|static\s+|final\s+)*int\s+(\w+)\s*=\s*(\d+)")
RE_START_NPC = re.compile(r"addStartNpc\s*\(\s*([^)]*)\)")
RE_TALK_ID = re.compile(r"addTalkId\s*\(\s*([^)]*)\)")
RE_KILL_ID = re.compile(r"addKillId\s*\(\s*([^)]*)\)")
RE_ITEM_REG = re.compile(r"registerQuestItems\s*\(\s*([^)]*)\)")
RE_REWARD = re.compile(r"rewardItems\s*\(\s*[^,]+,\s*(\w+)\s*(?:,\s*(\d+))?\s*\)")
RE_MIN = re.compile(r"getLevel\(\)\s*<\s*(\d+)")
RE_MIN_GE = re.compile(r"getLevel\(\)\s*>=\s*(\d+)")
RE_MIN_GT = re.compile(r"getLevel\(\)\s*>\s*(\d+)")
RE_MIN_CONST = re.compile(r"MIN_LEVEL\s*=\s*(\d+)")
RE_MAX = re.compile(r"getLevel\(\)\s*>\s*(\d+)")
RE_RACE = re.compile(r"getRace\(\)\s*(!=|==)\s*Race\.([A-Z_]+)")
RE_ADD_EXP = re.compile(r"addExpAndSp\s*\([^)]*\)")


def load_symbols(java: str) -> dict:
    """name -> int for every `int NAME = <num>` in the source."""
    return {m.group(1): int(m.group(2)) for m in RE_SYMBOL_INT.finditer(java)}


def resolve_ids(args_text: str, symbols: dict) -> list[int]:
    """Resolve comma/space int-constant/num args -> sorted unique ids; [] when ambiguous."""
    ids = []
    for tok in re.split(r"[,\s]+", args_text.strip()):
        if not tok:
            continue
        if tok.isdigit():
            ids.append(int(tok))
        elif tok in symbols:
            ids.append(symbols[tok])
        else:
            return []  # ambiguous symbol -> review, never guess
    return sorted(set(ids))


def parse_quest_file(java: str) -> dict:
    symbols = load_symbols(java)
    out = {"startNpc": None, "talkNpcs": [], "killNpcs": [], "items": [], "rewards": [],
           "minLevel": None, "maxLevel": None, "races": [], "kindLines": []}

    m = RE_START_NPC.search(java)
    if m:
        ids = resolve_ids(m.group(1), symbols)
        if ids:
            out["startNpc"] = ids[0]
    for call in RE_TALK_ID.finditer(java):
        out["talkNpcs"] = sorted(set(out["talkNpcs"]) | set(resolve_ids(call.group(1), symbols)))
    for call in RE_KILL_ID.finditer(java):
        out["killNpcs"] = sorted(set(out["killNpcs"]) | set(resolve_ids(call.group(1), symbols)))
    for call in RE_ITEM_REG.finditer(java):
        out["items"] = sorted(set(out["items"]) | set(resolve_ids(call.group(1), symbols)))
    for call in RE_REWARD.finditer(java):
        sym = call.group(1)
        if sym.isdigit():
            out["rewards"].append(int(sym))
        elif sym in symbols:
            out["rewards"].append(symbols[sym])
    out["rewards"] = sorted(set(out["rewards"]))

    # level gates: the STRICTEST gate (smallest lower bound / smallest upper bound) wins.
    mins = [int(g) for g in RE_MIN.findall(java)]
    mins += [int(g) for g in RE_MIN_GE.findall(java)]
    mins += [int(g) + 1 for g in RE_MIN_GT.findall(java)]
    mc = [int(g) for g in RE_MIN_CONST.findall(java)]
    if mins:
        out["minLevel"] = min(mins)
    elif mc:
        out["minLevel"] = min(mc)
    maxes = [int(g) for g in RE_MAX.findall(java)]
    if maxes:
        out["maxLevel"] = min(maxes)
    elif "MAX_LEVEL" in java:
        mm = re.search(r"MAX_LEVEL\s*=\s*(\d+)", java)
        if mm:
            out["maxLevel"] = int(mm.group(1))

    # race gates: `!=` restricts (exclude); `==` requires (include).
    required, restricted = [], []
    for gate in RE_RACE.finditer(java):
        op, race = gate.group(1), gate.group(2)
        (required if op == "==" else restricted).append(race)
    if required:
        out["races"] = sorted(set(required))
    out["classExclusions"] = sorted(set(restricted))
    out["kindLines"] = [ln.strip() for ln in java.splitlines()
                         if "addExpAndSp(" in ln or "rewardExp" in ln][:10]
    return out


# ---- htm graph ----
def htm_graph(quest_dir: Path) -> dict[str, list[str]]:
    """{npcId: [file names]} grouped by the leading <npcId> segment of each dialog file."""
    graph = {}
    for f in sorted(quest_dir.glob("*.htm")):
        first = f.name.split("-")[0]
        if first.isdigit():
            graph.setdefault(first, []).append(f.name)
        else:
            graph.setdefault("?", []).append(f.name)
    return graph


def extract() -> list[dict]:
    records, review = [], []
    for quest_dir in sorted(QUESTS_ROOT.iterdir()):
        if not quest_dir.is_dir():
            continue
        m = re.fullmatch(r"Q(\d+)_(.+)", quest_dir.name)
        if not m:
            continue
        try:
            qid = int(m.group(1))
        except ValueError:
            qid = None
        name = m.group(2)
        java_path = quest_dir / (quest_dir.name + ".java")
        if not java_path.exists():
            review.append({"id": qid, "name": name, "causes": ["no-java-source"]})
            records.append({"id": qid, "name": name, "causes": ["no-java-source"],
                            "startNpc": None, "minLevel": None})
            continue
        java = java_path.read_text(encoding="utf-8", errors="replace")
        rec = parse_quest_file(java)
        rec.update({"id": qid, "name": name, "htmGraph": htm_graph(quest_dir),
                    "codeHints": {"kindLines": rec.pop("kindLines")}})
        causes = []
        if rec["startNpc"] is None:
            causes.append("missing-startNpc")
        if rec["minLevel"] is None:
            causes.append("missing-minLevel")
        if causes:
            rec["needsReview"] = True
            review.append({"id": qid, "name": name, "causes": causes})
        records.append(rec)
    return records, review


def review_report(review: list[dict]) -> int:
    """Print grouped review report; return its size (acceptance: <= 50)."""
    grouped = {}
    for r in review:
        for cause in r["causes"]:
            grouped.setdefault(cause, []).append(r["id"])
    print(f"[quests] needsReview entries={len(review)} grouped by cause:")
    for cause, ids in sorted(grouped.items()):
        print(f"  {cause}: {len(ids)} {ids[:12]}{'...' if len(ids)>12 else ''}")
    return len(review)


def main() -> int:
    if not QUESTS_ROOT.is_dir():
        print(f"[quests] WARN quests dir missing: {QUESTS_ROOT}")
        return 1
    records, review = extract()
    _lib.write_json(TARGET, records)
    with_start = sum(1 for r in records if r["startNpc"] is not None)
    with_min = sum(1 for r in records if r["minLevel"] is not None)
    print(f"[quests] records={len(records)} startNpc={with_start} minLevel={with_min} -> {TARGET.name}")
    nrev = review_report(review)
    print(f"[quests] needsReview={nrev} (<=50 required)")
    return 0 if nrev <= 50 else 1


if __name__ == "__main__":
    raise SystemExit(main())
