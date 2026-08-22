# RuntimeLog — 2026-08-22 GK-10 — dialog.json (NPC talk graph index)

## Task
GK-10 — Dialog index: NPC talk graph from html (giver/turn-in links) — feeds quest driver.

## What was done
`scripts/datapack/extract_dialogs.py` — parses every quest html dialog
(`scripts/quests/QNNNNN_Name/*.htm`) into a factual per-page talk graph:

| kind | count | content |
|---|---|---|
| questDialog | 344 | per quest: giver (startNpc from quests.json), pages-per-npc, startPages (giver's lowest-step pages — the accept surface), turnInCandidates (terminal pages) |
| dialogPage | 9,190 | per html file: npcId/step (null when filename has no `<npc>-N` shape), parsed bypass links, isFirstPage + isTerminal flags |

- **4,667 links parsed**; types classified without guessing: `script`
  (Script Q_nnnn_… <page>), with page-target + targetNpc when the target is
  `<npc>-<step>.htm`; bare args (`wrong`/`right`/`accept`) recorded as `param`;
  `token` (TE0xx/-h), `npc` (npc_…), `other`. Raw bypass kept on every link.
- Honest flags: `isTerminal` = no outgoing page-link; `isFirstPage` = lowest step for
  (questId, npcId) — page names repeat across quests, so the pair is the key.
- `validate.py` 10/10 (dialog.json in EXPECTED; existing generic checks cover it),
  `extract_all.py` 10/10, SCHEMAS.md §dialog.json contract.
- `DialogKnowledgeTest` (7) locks real data with the engine's JsonResource: kinds
  present, quest 6 giver Roxxy 30006 + talk graph (Baulro 30311), 30006-05.htm →
  30006-06.htm link, firstPage = min step per quest+npc, terminal pages have no page
  links, turnInCandidates resolve to real pages, link kinds closed set.

## Pasted (audit acceptance)
```
[dialog] quests=344 pages=9190 links=4667 -> dialog.json
[validate] files=10 issues=0
[extract_all] done: 10/10 domains ok
mvn: Tests run: 567, Failures: 0, Errors: 0  (560→567)
gate.sh: GATE GREEN — style 0 violations, secrets clean
```

## Evidence / gate
- Feat commit `7df43337` (code+data+tests) + docs commit; pushed to master.
- The stale GK-8 WIP (`KnowledgeBase.java`, uncommitted) stays untracked — untouched.