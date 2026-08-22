# RuntimeLog — 2026-08-22 GK-4 — quests.json (the hard one)

## Task
GK-4 — `quests.json` from the 344 Java quest scripts + htm dialog graph.

## What was done
`scripts/datapack/extract_quests.py` — regex parser over the formulaic quest Java sources:
- **id/name** from the `QNNNNN_Name` dir; **symbols** from every `int NAME = <num>` const.
- **startNpc** (first `addStartNpc`), **talkNpcs** (`addTalkId`), **killNpcs** (`addKillId`),
  **items** (`registerQuestItems`), **rewards** (`rewardItems`) — all symbol-resolved.
- **minLevel**: `getLevel() < N` (min N), `getLevel() >= N` (min N), `getLevel() > N` (min N+1),
  `MIN_LEVEL = N` const — strictest wins. **maxLevel** from `getLevel() > N` / `MAX_LEVEL`.
- **races** (REQUIRED via `==`), **classExclusions** (restricted via `!=`).
- **htmGraph**: dialog `*.htm` grouped by leading `<npcId>` segment.
- **needsReview** flag + grouped review report; ambiguous symbols/fields recorded, never guessed.

## REAL acceptance (ALL MET)
- records=344; **startNpc=308, minLevel=306**; **startNpc + minLevel BOTH non-null = 302 (≥300 ✓)**
- **needsReview = 42 (≤50 ✓)** — grouped: missing-minLevel 38, missing-startNpc 36 (mostly
  saga Q70-81 family using a different helper structure)
- **Q00006 known-good MATCHES**: ROXXY startNpc=30006, talk=[30006,30033,30311], minLevel=3,
  rewards=[7559], items=[7571] ✓
- htmGraph sample: 30006 -> [30006-01..04.htm,...] ✓

## Evidence / gate
- `extract_all.py` **7/7** · `validate.py` files=7 issues=0 · quests.json 400K
- One commit set, pushed to master.