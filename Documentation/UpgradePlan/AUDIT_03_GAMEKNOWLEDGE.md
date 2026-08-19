# 🗺 AUDIT 03 — Game knowledge / Guide Map from SourceCode (goal 3)

> Owner question: *"Dive deep into SourceCode. Improve the Guide Map. Create a full smart
> database set for AI Players covering: the entire quest path zero→hero; preferred items/builds
> per race/profession/level; all NPC info + locations with target/move priorities; which NPC
> drops which item."*

## 1. The goldmine (verified inventory of `SourceCode/dist/game/data/`)

| Source | Count | Format | Extractable knowledge |
|---|---|---|---|
| `scripts/quests/*/` | **346 quests** | Java + per-NPC `.htm` | quest id/name, start NPC (`addStartNpc`), talk NPCs (`addTalkId`), required items (`registerQuestItems`), rewards, min level/race/class gates, dialog link graph (htm files named `<npcId>-<step>.htm`) |
| `stats/npcs/*.xml` | 83 files | XML `<npc id=…>` + `<dropLists>` | NPC stats (level, HP, aggro, race/type), **full drop tables** (item id, chance, min/max) |
| `spawns/**/*.xml` | 186 files | XML | live NPC locations (x,y,z) per NPC id — ground truth for "where is X" |
| `stats/items/*.xml` | 95 files | XML | item stats: type, grade (D/C/B/A/S), price, crystal count, weapon type |
| `stats/skills/*.xml` + `SkillLearn.xml` | 34 + 1 | XML | skills + **which class learns what at which level** (class trees) |
| `buylists/` + `multisell/` | 616 + 93 | XML | vendor inventories & prices per NPC |
| `html/` dialogs | 3,082 | HTML | NPC dialog trees (bypass links — already half-parsed by QuestDialogDriver) |
| `teleporters/`, `CategoryData.xml` | 6 + 1 | XML | teleport graph, category groupings |

Verified example (Q00006_StepIntoTheFuture.java): constant `ROXXY = 30006`, `addStartNpc(ROXXY)`,
`addTalkId(ROXXY, BAULRO, SIR_COLLIN)`, `registerQuestItems(BAULRO_LETTER)`, rewards
`MARK_TRAVELER/SOE_GIRAN`, min-level/race checks in code — i.e. **every field a bot needs is
mechanically extractable.**

## 2. What the engine knows today (the gap)

| Class | Size | Coverage |
|---|---|---|
| `phase0/guide/RaceGuide.java` | 679 hard-coded lines | ~5 race starting zones + hunt anchors + BFS routes |
| `phase0/quest/QuestDatabase.java` | 699 lines | ~49 quests hand-entered |
| `phase0/town/VendorDatabase.java` | 335 lines | ~8 vendors |
| `phase0/inventory/ItemDatabase.java` | 168 lines | handful of items |
| `phase0/combat/SkillDatabase.java` | 143 lines | handful of skills |

That is <2% of the available game content, maintained by hand — exactly what the owner wants
to replace with generated ground truth.

## 3. Target design: generated knowledge base + one loader API

```
scripts/datapack/                    (Python3 stdlib extractors — dev-time tools)
  extract_npcs.py  extract_quests.py  extract_items.py  extract_skills.py
  extract_spawns.py  extract_shops.py  build_chains.py  build_guide.py
        │  run manually / by CI; output COMMITTED
        ▼
AIPlayerEngine/src/main/resources/knowledge/
  npcs.json      {id, name, level, hp, aggro, type, drops:[{item,chance,min,max}], spawns:[x,y,z,zone]}
  items.json     {id, name, grade, type, price, weaponType, crystal}
  quests.json    {id, name, startNpc, talkNpcs[], items[], rewards[], minLevel, races[], classes[], chain: {prev, next}}
  shops.json     {npcId, items:[{itemId, price, count}], multisell[]}
  skills.json    {id, class, level, skillId, cost}        (from SkillLearn.xml)
  chains.json    per race+class: ordered [questId, levelToTake] zero→hero
  guide.json     per race: start zone, hunting ladder by level, restock vendors, class-transfer NPCs
        │
        ▼
com.aiplayer.knowledge.KnowledgeBase  (loads once, indexed; typed queries):
  nextQuests(level, race, classId) · npc(id) · nearestNpc(id, pos) · droppersOf(itemId)
  vendorSelling(itemId) · skillLadder(classId) · huntZone(race, level) · route(a, b)
```

Rules:
- **Hand-written knowledge is banned** in new code — everything generated from the datapack,
  so a server datapack update = re-run extractors (diff-able JSON).
- Existing RaceGuide/QuestDatabase/VendorDatabase **keep their public APIs** but become thin
  wrappers over KnowledgeBase (callers don't change; EP-3 moves them into `knowledge/`).
- Bots consume it only via KnowledgeBase queries (this is the in-process form of the owner's
  "middleware" idea — see RESEARCH_05 for the service/HTTP evolution).

## 4. Task prompts for Deepseek-v4-flash

| ID | Task | Effort | Depends | Status |
|---|---|---|---|---|
| GK-1 | Extractor skeleton + JSON schemas + validator | S | – | TODO |
| GK-2 | NPC + spawn + drops extractor → npcs.json | M | GK-1 | TODO |
| GK-3 | Items + skills + class-tree extractor → items/skills.json | M | GK-1 | TODO |
| GK-4 | Quest extractor (Java sources + htm graph) → quests.json | L | GK-1 | TODO |
| GK-5 | Shop extractor (buylists + multisell) → shops.json | M | GK-1 | TODO |
| GK-6 | KnowledgeBase loader + swap hardcoded DBs to wrappers | M | GK-2..5 | TODO |
| GK-7 | Zero→hero chain builder per race/class → chains.json | M | GK-4 | TODO |
| GK-8 | Gear/build recommender wired into RestockPlanner | M | GK-3, GK-5 | TODO |

### PROMPT GK-1
```
TASK GK-1: Knowledge extractor skeleton

CONTEXT
- Source of truth: SourceCode/dist/game/data/ (READ-ONLY). Target: generated JSON committed to
  AIPlayerEngine/src/main/resources/knowledge/. Read Audit 03 §3 for the schema sketch.

GOAL
scripts/datapack/ with: a shared _lib.py (XML walk helpers, id/name normalization, coordinate
rounding), a per-domain extractor stub (npcs/items/quests/skills/spawns/shops), a runner
`extract_all.py`, and scripts/datapack/SCHEMAS.md documenting every JSON file field-by-field.
A validator `validate.py` checks: schema presence, no null ids, coordinates within world bounds
(-204800..204800 x/y, -16000..16000 z — sanity-check against a few known spawns), drops chances
in (0,1].

ACCEPTANCE
- extract_all.py runs clean printing per-file entity counts; validate.py exits 0 on empty-but-
  valid outputs; SCHEMAS.md ≤150 lines; RuntimeLog.
```

### PROMPT GK-2
```
TASK GK-2: npcs.json — NPCs, spawns, drop tables

CONTEXT
- stats/npcs/*.xml (83 files): <npc id=…> stat entries + <dropLists> sections.
- spawns/**/*.xml (186): live coordinates per npc id.

GOAL
npcs.json: one record per NPC id {id, name, level, hp, aggroRange, isAggressive, type,
drops:[{itemId, chance, min, max}], spawns:[{x,y,z,zoneHint}]} — merged from both sources.
Include a build report: counts, top-50 mobs by drop value (join items.json later — for now by
chance×count), and 5 sanity spot-checks vs the XML (paste the pairs).

ACCEPTANCE
- npcs.json committed; ≥ 5,000 NPC records and ≥ 100k drop rows expected (paste real counts);
  validate.py green; spot-check rows pasted; RuntimeLog.
```

### PROMPT GK-3
```
TASK GK-3: items.json + skills.json + class trees

CONTEXT
- stats/items/*.xml (95), stats/skills/*.xml (34), SkillLearn.xml, CategoryData.xml.

GOAL
items.json {id, name, grade, slot/type, price, weaponType, soulshotGroup}; skills.json from
SkillLearn.xml {classId, level → skillId, spCost} forming per-class skill ladders; classes.json
{race → baseClass → 2ndProfession(level 20 quest) → 3rdProfession(level 40)} derived from
CategoryData/class-id conventions (verify mapping against org.l2jmobius ClassId semantics in
SourceCode/java — read-only!).

ACCEPTANCE
- All three files + counts (items ~5-10k expected — paste real); every race's 3-tier class chain
  printable via a --summary flag (paste the 5 chains); validate.py green; RuntimeLog.
```

### PROMPT GK-4
```
TASK GK-4: quests.json — the hard one

CONTEXT
- 346 Java quest scripts + 3,082 htm dialogs. Verified anatomy (Q00006): NPC id constants,
  addStartNpc/addTalkId, registerQuestItems, rewardItems calls, level/race/class gates
  (chk+MIN_LEVEL etc.), htm files named <npcId>-<step>.htm giving the dialog graph.

GOAL
quests.json {id, name, minLevel, maxLevel?, races[], classes[], startNpc, talkNpcs[], items[],
rewards[], htmGraph:{npcId:[file…]} , codeHints:{kindLines:[…]}}. Parse with regex over the
Java source (they're formulaic); where a field is ambiguous record null + a "needsReview" flag
rather than guessing. Produce a review report listing all needsReview quests grouped by cause.

ACCEPTANCE
- ≥ 300 quests extracted with startNpc + minLevel non-null (paste count + 3 sample records incl.
  Q00006 matching known-good: ROXXY 30006, talk 30006/30033/30311, min level 3);
  needsReview report ≤ 50 entries; validate.py green; RuntimeLog.
```

### PROMPT GK-5
```
TASK GK-5: shops.json — vendor inventories

CONTEXT
- buylists/ (616 xml) + multisell/ (93) + MerchantPriceConfig.xml; npcId↔shop linkage inside
  the files.

GOAL
shops.json {npcId, buyList:[{itemId, price?}], multisells:[{resultItem, ingredients[]}]}. Join
with VendorDatabase's 8 known vendors as ground-truth spot-checks (they were verified live in
S7-T03 — the 4 towns' grocers must match).

ACCEPTANCE
- shops.json + counts (paste); the 4 known town grocers from VendorDatabase appear with correct
  npcIds and stock (paste comparison table); validate.py green; RuntimeLog.
```

### PROMPT GK-6
```
TASK GK-6: KnowledgeBase loader + retire hardcode

CONTEXT
- knowledge JSON files exist (GK-2..5). Existing consumers: phase0/quest/QuestDatabase,
  phase0/town/VendorDatabase, phase0/inventory/ItemDatabase, phase0/combat/SkillDatabase.

GOAL
com.aiplayer.knowledge.KnowledgeBase: loads all JSON at startup (sub-second — index by id),
typed records (NpcInfo, QuestInfo…), queries: npc(id), nearestNpcOf(id,pos), droppersOf(itemId),
vendorSelling(itemId), questsFor(level,race,classId), skillLadder(classId). QuestDatabase/
VendorDatabase/ItemDatabase/SkillDatabase become lazy wrappers delegating to KnowledgeBase
(keep their public method signatures; existing callers untouched). RaceGuide stays hand-written
for now but add a test asserting its anchors correspond to real spawn clusters in npcs.json
(nearest spawn within 3,000 units).

ACCEPTANCE
- Tests green incl. new KnowledgeBaseTest (≥10 queries) and the RaceGuide-anchor test (paste);
  engine boot log line "knowledge loaded: N npcs, M quests, K items in Xms"; RuntimeLog.
```

### PROMPT GK-7
```
TASK GK-7: chains.json — zero→hero roadmaps

CONTEXT
- quests.json (GK-4) + classes.json (GK-3). Owner goal 3.2.1 "entire quest path (from zero to
  hero)".

GOAL
build_chains.py computes per race+base-class an ordered roadmap: newbie quests (level gates ≤10,
race-matched), 1st class transfer at 20, key leveling quests 25-40, 2nd transfer at 40, flag
endgame 40+ quests. Algorithm: sort by (minLevel, questId), insert class-transfer quests at
thresholds from classes.json, mark side-quest vs chain via rewards/known-series ids. Output
chains.json + human-readable chains.md (review artifact).

ACCEPTANCE
- chains.json covers all 5 races × their base classes; each chain has ≥8 steps and correct
  class-transfer quests at levels 20/40 (paste Human fighter + Orc mystic chains);
  validate.py green; RuntimeLog.
```

### PROMPT GK-8
```
TASK GK-8: Build recommender (gear per race/class/level)

CONTEXT
- items.json grades + weaponTypes, shops.json availability, npcs.json drops. Consumer:
  phase0/town RestockPlanner (already plans potion/soulshot buys) — post EP-3: behavior.town.

GOAL
knowledge/BuildGuide: for (classId, level) → recommended {weapon (right weaponType, best grade
available at level), armor pieces by slot, soulshot type} scored by (grade, price, shop
availability; drops as fallback source with dropper list). Expose
BuildGuide.recommend(classId, level, adena) → plan RestockPlanner can merge. Unit tests with
real data (e.g., Human fighter L20 wants a D-grade sword sold in Giran — assert against
items/shops json).

ACCEPTANCE
- Tests green (≥6 recommendation cases pasted: L1/L10/L20/L25/L40 across 2 classes);
  one live 10-bot run shows at least one bot buying a recommended weapon (paste event);
  RuntimeLog.
```
