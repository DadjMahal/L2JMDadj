#!/usr/bin/env python3
"""GK-10 — dialog.json extractor: per-page NPC talk graph from quest html files.

Source (READ-ONLY): quest html dialogs `scripts/quests/QNNNNN_Name/*.htm`
(3,082 html files, 344 quest dirs). Complements quests.json `htmGraph` (which only
lists files per npc) with the actual links each page exposes, classified without
guessing:
  - kind dialogPage   — one record per html file: parsed bypass links + page flags
  - kind questDialog  — per-quest summary: giver (startNpc from quests.json),
    pages-per-npc and turn-in candidates (terminal pages — no outgoing page links)

feeds the quest driver with precomputed accept/turn-in surfaces (accept-start pages
are the giver's lowest-step pages; turn-in candidates are terminal pages).
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _lib  # noqa: E402

TARGET = _lib.TARGET_ROOT / "dialog.json"
QUESTS_ROOT = _lib.DATA_ROOT / "scripts" / "quests"

RE_PAGE = re.compile(r"^(\d+)-(\d+)([a-z]?)?\.html?$")
RE_SCRIPT = re.compile(r"^Script\s+(\w+)\s+(.+)$", re.S)
RE_LINK = re.compile(r"""<a[^>]*?action=["']bypass (.*?)["'][^>]*>(.*?)</a>""", re.S)


def parse_link(raw: str, caption: str) -> dict:
    """Classify one bypass command (text after 'bypass '). All fields keep raw truth."""
    cmd = raw.strip()
    if cmd.startswith("-h "):
        cmd = cmd[3:].strip()
    link = {"raw": raw.strip(), "kind": "other", "text": _lib.norm_name(caption)[:80]}
    m = RE_SCRIPT.match(cmd)
    if m:
        script = m.group(1)
        rest = m.group(2).strip()
        link["kind"] = "script"
        link["script"] = script
        # last token is a page target (<npc>-<step>[a-z].htm / <NN>.htm), else a bare param.
        tok = rest.split()[-1]
        tm = RE_PAGE.match(tok)
        if tm:
            link["target"] = tok
            if tm.group(1):
                link["targetNpc"] = int(tm.group(1))
        else:
            link["param"] = rest
        return link
    if re.fullmatch(r"TE\d+", cmd):
        link["kind"] = "token"
        link["token"] = cmd
        return link
    if cmd.startswith("npc_"):
        link["kind"] = "npc"
        return link
    return link


def page_step(page: str) -> int | None:
    """Numeric step of a dialog page name; None when it has no '<npc>-N' shape."""
    m = RE_PAGE.match(page)
    if m:
        return int(m.group(2))
    return None


def extract() -> list[dict]:
    quests = {q["id"]: q for q in (_lib.read_json(_lib.TARGET_ROOT / "quests.json") or [])}
    pages: list[dict] = []
    summaries: list[dict] = []
    for quest_dir in sorted(QUESTS_ROOT.iterdir()):
        if not quest_dir.is_dir():
            continue
        m = re.fullmatch(r"Q(\d+)_(.+)", quest_dir.name)
        if not m:
            continue
        qid = int(m.group(1))
        quest = quests.get(qid) or {}
        file_pages: list[dict] = []
        for f in sorted(quest_dir.glob("*.htm")):
            text = f.read_text(encoding="utf-8", errors="replace")
            links = []
            for raw, caption in RE_LINK.findall(text):
                # RE_LINK group(1) is already the text after 'bypass '
                links.append(parse_link(raw, caption))
            step = page_step(f.name)
            npc_seg = f.name.split("-")[0]
            npc_id = int(npc_seg) if npc_seg.isdigit() else None
            file_pages.append({
                "id": f"{qid}/{f.name}",
                "kind": "dialogPage",
                "questId": qid,
                "npcId": npc_id,
                "page": f.name,
                "step": step,
                "links": links,
                "isTerminal": not any(l.get("target") for l in links),
            })
        if not file_pages:
            continue
        # per-npc lowest step -> isFirstPage (the natural accept-start surface)
        for p in file_pages:
            if p["npcId"] is None:
                continue
            same = [q for q in file_pages if q["npcId"] == p["npcId"]]
            steps = [q["step"] for q in same if q["step"] is not None]
            p["isFirstPage"] = bool(steps) and p["step"] == min(steps)
        pages.extend(file_pages)
        # summary: giver + pages-per-npc + terminal (turn-in candidate) pages
        npc_pages = {}
        for q in file_pages:
            if q["npcId"] is None:
                continue
            npc_pages.setdefault(q["npcId"], []).append(q["page"])
        turn_ins = [{"npcId": q["npcId"], "page": q["page"]} for q in file_pages
                    if q["isTerminal"] and q["npcId"] is not None]
        start = quest.get("startNpc")
        first_pages = [q["page"] for q in file_pages
                       if q["npcId"] == start and q.get("isFirstPage")]
        summaries.append({
            "id": qid,
            "kind": "questDialog",
            "quest": quest_dir.name,
            "startNpc": start,
            "npcPages": [{"npcId": k, "pages": sorted(v)} for k, v in sorted(npc_pages.items())],
            "startPages": sorted(first_pages),
            "turnInCandidates": sorted(turn_ins,
                                       key=lambda x: (x["npcId"] or 0, x["page"])),
        })
    return summaries + pages


def main() -> int:
    data = extract()
    pages = [r for r in data if r["kind"] == "dialogPage"]
    _lib.write_json(TARGET, data)
    n_links = sum(len(r["links"]) for r in pages)
    print(f"[dialog] quests={sum(1 for r in data if r['kind']=='questDialog')} "
          f"pages={len(pages)} links={n_links} -> {TARGET.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())