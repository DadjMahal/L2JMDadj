# 🌍 AUDIT 06 — Full game-content coverage (AI must play ALL of SourceCode)

> Owner directive: *"Be sure we are building AI Players who fully observe and play the whole
> game content written in current SourceCode. Full integration."*
> Scope: every player-facing system in the Interlude SourceCode, mapped to AI capability →
> current status → task. This is the coverage CONTRACT for "AI plays the whole game".

## 1. Content inventory (verified in SourceCode)

| Domain | What exists (verified) | Scale |
|---|---|---|
| Identity & progression | 5 races, base classes → 2nd prof (Lv20, village_master scripts) → 3rd prof (Lv40 saga quests); subclasses; noblesse; hero | 16 village_master scripts, saga quests among 346 |
| Quests | `scripts/quests/` — newbie chains → class transfer → sagas → endgame | **346 quests** |
| Combat | melee/archery/magic, buffs/heals/DoTs, aggro/leash, spoil+sweep (dwarf), summon combat | 89 `scripts/ai/`, full skill XML |
| Items & gear | grades NG→S, shop/buylist + multisell, enchant, soul/spiritshots, augmentation, crystals, shadow items, recipes/crafting | 95 item XMLs, 616 buylists, 93 multisells |
| Economy | shops, **private buy/sell stores**, warehouse + freight, manor (CastleManorManager), castle tax, fishing (+ FishingChampionshipManager) | manor + tax managers |
| Social | all chat channels, mail + community board forums (communitybbs/**), friends list, party (8 mechanisms incl. loot rules), clan/alliance/wars, wedding, mentors, petitions | BB/Manager packages |
| PvP | flag/karma, **DuelManager**, Olympiad (hero), castle sieges + mercenary, clan-hall sieges (CHSiegeManager), cursed weapons | 43 managers total |
| PvE endgame | RaidBossSpawnManager + RaidBossPointsManager (live leaderboard), GrandBossManager, world raid AIs | points manager = leaderboard hook |
| World & travel | gatekeepers (6 teleporter files), **boats** (BoatManager, vehicles scripts), routes (WalkingManager), pets (wolf/hatchling/strider + evolutions), wyvern for castle lords | |
| Events & seasons | Seven Signs + festivals, dimensional rift, four sepulchers, instances, champion monsters, event scripts | instances/, events/ |
| Server features (42 Custom ini) | **FakePlayers** (server-side NPC-players — study as inspiration, ours are external), SellBuffs, Wedding, Banking, OfflineTrade/OfflinePlay, ChampionMonsters, Premium, … | config/Custom/ |
| Admin surface | 215 client packets, admin command system (scriptable handlers), BBS, petition | engine must speak packets, not admin |

## 2. AI capability × status matrix (the contract)

Status legend: ✅ proven live · 🟡 partial (works between bots / primitives only) · ❌ absent

| # | Capability | Status | Evidence / gap |
|---|---|---|---|
| 1 | Login, char select, enter world | ✅ | core path since B1-B3 |
| 2 | Movement, routing, relocation | ✅ | S5: 100% hop-success live |
| 3 | PvE combat + XP/leveling | ✅ | B4, H5 airtight; fleet farms |
| 4 | Loot pickup / Adena | 🟡 | party-loot *decision* tested; ground pickup & sweep unproven |
| 5 | Quest accept/dialog/journal | ✅ | S3-T01 live (Q6 accepted) |
| 6 | Quest complete → turn-in loop | 🟡 | primitives locked by tests; end-to-end soak pending (IN-6 consumes) |
| 7 | Vendor buy / restock | ✅ | B7 live (Silvia, DB-verified) |
| 8 | Potions / soulshots in combat | ✅ | S6-T04/T05 live |
| 9 | Death handling / respawn | ✅ | S6-T06 |
| 10 | Chat (say/shout/party/whisper) | 🟡 | B9 send proven; receive+reply flow absent |
| 11 | Party (bot↔bot) | 🟡 | B10 basics; loot rules, assist, disband absent |
| 12 | **Party with HUMANS** (the fun one) | ❌ | invite accept, follow, assist, polite loot — nothing |
| 13 | Private buy/sell store | ❌ | biggest economy visibility win |
| 14 | Warehouse / freight / crystals | ❌ | |
| 15 | Enchant / augmentation | ❌ | |
| 16 | Crafting (dwarf life) + manor | ❌ | recipes + seeds/harvest |
| 17 | Fishing | ❌ | championship = engagement hook |
| 18 | Mail / BBS forums / friends list | ❌ | communitybbs ready server-side |
| 19 | Duels / arena PvP vs humans | ❌ | DuelManager exists |
| 20 | Subclass / noblesse / hero / Olympiad | ❌ | late-game; needs 76+ |
| 21 | Sieges / clan wars / cursed weapons | ❌ | fleet-scale; later phases |
| 22 | Seven Signs / festivals / instances | ❌ | later phases |
| 23 | Pets/summons/boats travel | ❌ | boats = WalkingManager routes |
| 24 | Class transfer automation | 🟡 | S3-T09/T10 primitives tested |

**Reading:** the core survival loop (1-9) is genuinely proven. Everything that makes the world
feel *inhabited* to a human player (10-19) is missing — and that IS the product per the owner's
goal ("server where regular players join and play WITH AI players"). Coverage strategy: close
12, 13, 10-receive, 11-full first (the "social-economic presence" cluster), then 14-16, 17-19;
20-22 are endgame fleet projects gated by bot level progression.

## 3. Task prompts for Deepseek-v4-flash

| ID | Task | Effort | Depends | Status |
|---|---|---|---|---|
| CO-1 | Coverage matrix as living artifact (docs + dashboard page) | S | – | TODO |
| CO-2 | Private sell/buy store (bot shopfront in town) | M | GK-5 | TODO |
| CO-3 | Party-with-humans full loop (accept/invite, follow, assist, loot rules) | L | – | TODO |
| CO-4 | Chat receive→reply pipeline (channels, whisper, LLM-assisted canned) | M | BR-6 | TODO |
| CO-5 | Warehouse + crystals + safe enchant | M | – | TODO |
| CO-6 | Crafting + manor participation (dwarf career) | L | CO-2 | TODO |
| CO-7 | Fishing (idle-friendly minigame + championship) | M | – | TODO |
| CO-8 | Duels + PvP etiquette (accept, fair play, no karma grief) | M | – | TODO |
| CO-9 | "Plays-everything" regression suite (matrix rows → smoke probes) | M | all | TODO |

### PROMPT CO-1
```
TASK CO-1: Living coverage matrix

CONTEXT
- Audit 06 §2 is the contract; it must not rot in a doc.

GOAL
1. Documentation/UpgradePlan/CONTENT_MATRIX.md generated from a small Python script
   scripts/coverage_matrix.py that reads a YAML of rows (capability, status, evidence-task,
   packet-ids) and renders the markdown table — statuses updated by editing YAML only.
2. Dashboard ops page section "Coverage" rendering the same YAML (read via /api/v1/coverage).

ACCEPTANCE
- Script regenerates the doc byte-identically from YAML (paste diff run); ops page shows the
  table; RuntimeLog.
```

### PROMPT CO-2
```
TASK CO-2: Private store — the bot shopfront

CONTEXT
- Interlude private stores: SetPrivateStore* packets (sell list with price, title shown over
  the char). KnowledgeBase (GK-5/6) knows items+prices; SellManager has pure sell logic.

GOAL
A bot in town can: pick a selling spot (LandmarkPlanner), open a SELL store titled e.g.
"Dwarf crafts — cheap D grade" with priced items (fair price = shop price × factor from
ItemValueEstimator), sit (offline-trade style presence), react to sale events, reprice/close on
schedule. Also BUY stores for materials the bot's career needs (crafting prep). Store lifecycle
logged to events.jsonl (type=store_open/store_sell/store_close).

ACCEPTANCE
- Tests green (+packet encode/decode tests, +pricing policy test); live proof: 1 bot opens a
  store, a GM/test human buys an item, adena+item movement verified in DB (paste before/after);
  RuntimeLog.
```

### PROMPT CO-3
```
TASK CO-3: Party with humans — the flagship social loop

CONTEXT
- Capability 12. Humans invite via party UI; bot receives RequestJoinParty (0x29-ish — verify
  exact opcode in SourceCode clientpackets, read-only), must AnswerJoinParty (accept), then
  behave as a member. Read org.l2jmobius Party.java + RequestJoinParty/AnswerJoinParty handlers
  FIRST and document the exact flow (leadership, loot rules, exp distribution) in RuntimeLog.

GOAL
Bot party behaviors: accept invites (personality-gated: mentor bots always accept newbies),
invite a human who whispers "party?" (L2 custom), follow leader, assist leader's target (no
steal-aggro), respect loot rule (FindersKeepers vs round-robin via party packets), leave
politely on disband/idle-leader with a chat line. Party state machine unit-tested; live drill
with the owner acting as the human.

ACCEPTANCE
- Flow doc + tests green (+4 state-machine tests); live drill transcript pasted (invite→2 kills
  together→loot respected→disband); RuntimeLog.
```

### PROMPT CO-4
```
TASK CO-4: Chat that talks back

CONTEXT
- B9 proved SEND. Receive path: chat packets carry speaker/ChannelType/text; ChatResponder +
  Persona exist (phase0/social) but unwired to real packets. BR-6 provides the LLM assist hook.

GOAL
Pipeline: packet → ChatIntent (greeting/question/request/insult/price-check via keyword+LLM) →
Persona-styled reply → rate-limited send (never spam; per-channel cooldowns). Whisper replies
priority. Canned fallback when LLM off. Safety: no GM impersonation, no URL posting, no real
credentials — hard filters BEFORE any LLM output is sent.

ACCEPTANCE
- Tests green (intent routing, filters, cooldown); live: whisper a bot "hi, where drop D-grade
  bow?" → bot replies naming NPC+zone from KnowledgeBase (paste); RuntimeLog.
```

### PROMPT CO-5
```
TASK CO-5: Warehouse, crystals, enchant

CONTEXT
- Capabilities 14-15. Warehouse deposit/withdraw packets; crystalization; enchant via
  EnchantScroll packets with failure risk.

GOAL
Bot inventory management matures: overflow → warehouse deposit (not just sell); crystalize
drops when profitable (ItemValueEstimator ×crystal yield); enchant own gear up to +3 SAFE
only (Interlude safe enchant), stop on fail budget. All behind InventoryPolicy (behavior.town)
so thresholds are config+learnable, not new hardcode.

ACCEPTANCE
- Tests green (+3 policy tests); live: bot with full bags deposits and crystalizes (paste
  inventory before/after + adena); one +3 enchant attempt logged with outcome; RuntimeLog.
```

### PROMPT CO-6
```
TASK CO-6: The dwarf career — crafting + manor

CONTEXT
- Capability 16. Recipes.xml, RecipeController packets (make item), manor: buy seeds, harvest
  in castle territory, sell fruit. Dwarf bots = server's industrial base (fun economy).

GOAL
Crafting loop: learn recipe (village masters), gather materials (buy store CO-2 + grind), craft
on demand (own use + private store stock). Manor loop: seed/harvest/sell per CastleManorManager
cycle. Career gated to dwarf personalities; full event trail for the dashboard.

ACCEPTANCE
- Tests green (+craft-decision and manor-window tests); live: one dwarf crafts ≥1 D-grade item
  and lists it in its store (paste craft event + store listing); RuntimeLog.
```

### PROMPT CO-7
```
TASK CO-7: Fishing citizen

CONTEXT
- Capability 17. Fishing packets (cast/reel/pump minigame), FishingChampionshipManager keeps a
  server leaderboard — engagement hook (real players see bots competing).

GOAL
Fishing behavior for "relaxing" personality slots: go to shore spot, cast, play the reel
minigame (reaction-timed per HumanizedRandom), keep fish, register championship catches. Idle-
friendly: fills gaps between hunt schedules.

ACCEPTANCE
- Tests green (minigame reaction model); live: 1 bot fishes 10 min, ≥1 catch registered
  (paste championship/character fish data); RuntimeLog.
```

### PROMPT CO-8
```
TASK CO-8: Duels & PvP etiquette

CONTEXT
- Capability 18-19. DuelManager: request/answer packets, arena rules, no karma in duel. Bots
  must be FUN opponents: fight credibly, not perfectly (personality skill ceilings), never
  gank lowbies, never karma on innocents (server rules).

GOAL
Duel state machine: accept politely (chat), fight with rotation + humanlike errors (HumanizedRandom
timing), win/lose gracefully (gg line), cooldown between duels. Hard rule: no world-PvP attack
unless attacked first or fleet policy allows (config PVP_MODE: never|defensive|arena_only).

ACCEPTANCE
- Tests green (+duel FSM tests, +etiquette guard test that PVP_MODE=never never sends Attack on
  a neutral); live: owner duels a bot, full transcript (request→fight→result chat) pasted;
  RuntimeLog.
```

### PROMPT CO-9
```
TASK CO-9: Plays-everything regression suite

CONTEXT
- As matrix rows turn 🟡→✅ they need an automated proof, or coverage claims rot.

GOAL
examples/SoakSuite.java (cli/): parameterized smoke probes per capability row — each probe runs
1 bot, drives the capability, asserts observable outcome (journal/event/DB), emits JSON verdict
row. CI-style runner: scripts/soak.sh [rows...] → exits non-zero on any FAIL; dashboard ops
button "Run coverage soak" triggers it via control plane (BR-4).

ACCEPTANCE
- Suite covers ≥10 green rows today (the ✅ ones) + red-skips for the rest (paste run);
  soak.sh wired to ops page; RuntimeLog.
```
