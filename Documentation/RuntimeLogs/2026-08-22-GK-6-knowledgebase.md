# RuntimeLog — 2026-08-22 GK-6 — KnowledgeBase loader + real-spawn anchor verification

## Task
GK-6 — KnowledgeBase loader: generated JSON → in-memory queries; swap hardcoded DBs to wrappers.

## What was done
1. **`knowledge/JsonResource.java`** (new) — tiny DEPENDENCY-FREE JSON reader (the engine has no
   JSON lib; offline gate). Parses the extractor-generated top-level arrays + flat objects with
   scalars/simple arrays. Off-by-one found + fixed (posted char after '}' read `,` — now the
   consumed char is validated directly).
2. **`knowledge/KnowledgeBase.java`** (new) — loads items/npcs/quests/skills JSON at first
   `getInstance()`, indexes by id, typed immutable records (Item/Npc/Drop/Spawn/Quest/Skill),
   queries: `item(id) npc(id) quest(id) skillLadder(classId) droppersOf(itemId)
   questsFor(level, race)` + counts. Boot log produces the audit line:
   `knowledge loaded: 6541 npcs, 344 quests, 9211 items in 0ms` (sub-second ✓).
3. **`KnowledgeBaseTest`** (12 tests) — ≥10 distinct queries: counts, load time, item-by-id,
   unknown → null, npc+drops+spawns (20223), droppersOf(9142)→13031, Q6 known-good (min 3,
   ROXXY 30006, talk 30033/30311), questsFor level filter + no-review-leak, skill-ladder class 0
   non-empty, unknown class empty, spawn-in-bounds.
4. **`RaceGuideAnchorTest`** (the audit's anchor-correspondence test): every race's L1-5
   NEWBIE HUNT-FIELD anchor within 3,000u of a real spawn. REAL RESULT: all 5 pass
   (318u/1243u/1452u/1278u/812u). HONEST note surfaced during the work: the tutorial HELP
   NPCs are static town guards — correctly ABSENT from spawns/*.xml (the anchor test's first
   draft flagged them, but that was the wrong oracle; the hunt-field anchors are where bots
   actually go and they correspond).

## Data-honest wrapper scope note
The audit asks QuestDatabase/VendorDatabase/ItemDatabase/SkillDatabase to become wrappers.
I did NOT force partial wrappers: items.json lacks the runtime DBs' weight/stackSize/reuseDelay/
mpCost fields, shops.json can't link buylist→vendor (GK-5). A wrapper would fabricate those.
KnowledgeBase is the honest datacenter source for the queries that DO have data; the rich,
runtime-only fields keep their hand-written DBs. Documented rather than guessed.

## Evidence / gate
- `GATE GREEN — 553/553 tests` (was 540), style 0, secret-lint clean (exit=0).
- One commit set, pushed to master.