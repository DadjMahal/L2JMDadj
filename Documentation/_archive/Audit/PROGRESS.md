# Source Code Audit — Progress & Resume State

> **This file is the single source of truth for the ongoing source code audit.**
> It is designed so that any new AI context window can resume the work without
> prior conversation memory.

## Goal

Produce a complete, deep knowledge base of `SourceCode/` so that any future AI
(including AI players that will play on the server) can quickly orient, understand
what state exists, what is observable, what actions are possible, and exactly where
to make a change or read behavior.

## Scope

- `SourceCode/java/org/l2jmobius/` — 2,473 Java files, ~207k lines
- `SourceCode/dist/game/data/scripts/` — 864 Java scripts (quests, AI, handlers)
- `SourceCode/dist/` config/data formats (where they define behavior)

## Conventions

- Documentation language: **English** (matches existing docs).
- Output location: `Documentation/Audit/<NN>-<slug>.md` (one file per iteration).
- Each iteration reads one coherent subsystem, produces one focused doc.
- Keep docs dense and factual: responsibilities, key classes, data flow, public
  API surface, gotchas, and "where to change X". Avoid pasting large code blocks.
- Foundational subsystems first, network before model (AI players need protocol knowledge), scripts last, synthesis last.

### Per-class audit template

For every class/file in scope, document:
- **Purpose** — one-sentence role.
- **Fields / State** — what it holds, mutability, concurrency notes.
- **Public API Surface** — method signatures + behavior summary.
- **Control Flow** — callers, callees, lifecycle hooks.
- **I/O** — DB queries, files, network calls.
- **Gotchas / Refactor Candidates** — structural notes only (no bug hunt).

Avoid pasting large code blocks. Prefer tables and dense bullet lists.

### Resume checkpoint (append to each iteration's Notes)

Use this structure inside `Notes` for recoverability:
- Read files:
- Still to read:
- Key structural findings so far:
- Next step:

### Runtime logs

After every context window or meaningful step, create / update:
`Documentation/RuntimeLogs/<timestamp>-<task>.md`

Contents: original prompt, objective, files modified, problems encountered,
how solved, remaining issues, completed work, recommended next steps (10-70 lines).

## How to resume (protocol for a new context window)

1. Read `Documentation/REQUIREMENTS.md`.
2. Read this file (`Documentation/Audit/PROGRESS.md`).
3. Find the first iteration below with status **`pending`**.
4. If a previous iteration is `in_progress`, finish it first:
   - Read its **Notes** field below — it records what was already read/analyzed.
   - Re-read only the scope files NOT already covered by the Notes.
   - Complete the Output doc, then mark `done`.
5. Before reading any scope files for a new iteration, set it to `in_progress`
   **and leave a Notes line** recording files as you read them
   (e.g. `01 | in_progress | commons/ | ... | read: network/*, database/* ; next: threads/*`).
   Update this file on disk periodically so a mid-iteration interruption is recoverable.
6. Read every file in that iteration's **Scope** (minus what Notes already covered).
7. Write the iteration's **Output** doc following the conventions above.
8. Update this file: set that iteration to `done`, fill its **Doc** + **Notes**,
   set the next `pending` to `in_progress` (with an empty Notes placeholder).
9. If token budget remains in the current context window, repeat from step 3.
10. Always end by saving this file so the next context window can continue.

> **Interruption safety:** the Notes field is the recovery mechanism. If you feel
> the context window is filling up, save the current Notes + status before doing
> anything else.

## Decisions log

- 2026-07-29: Chose English for audit docs (consistent with existing Documentation).
- 2026-07-29: Partitions ordered foundational-first, network before model (AI
  players need protocol knowledge), scripts last, synthesis last.
- 2026-07-29: Total estimate ~27 iterations, ~5-8M tokens, ~8-15h wall clock.

## Iterations

Format: `NN | status | title | scope | output | notes`

### Foundation

- 01 | done | commons/ | `java/org/l2jmobius/commons/**` (config, crypt, database, network, threads, time, ui, util) | `01-commons.md` | All 45 files read. Async NIO stack documented (critical for AI players). Key: LITTLE-ENDIAN, 2-byte header, Blowfish ECB + checksum + XOR handshake.
- 02 | done | loginserver/ | `java/org/l2jmobius/loginserver/**` | `02-loginserver.md` | Read all 67 files. Doc written with per-class template covering Purpose, Fields/State, Public API Surface, Control Flow, I/O, Gotchas/Refactor Candidates.

### Gameserver code

- 03 | done | GameServer bootstrap & config | `gameserver/{GameServer,LoginServerThread,Shutdown}.java`, `gameserver/config/**` | `03-gameserver.md` | Constructor-heavy bootstrap; singleton-heavy startup; config-driven behavior.
- 04 | done | gameserver network + representative model/data/managers | `gameserver/network/{GameClient,GamePacketHandler,Encryption,ConnectionState,ClientPackets,ServerPackets,ClientPacket,ServerPacket}.java`, `network/serverpackets/SystemMessage.java`, `gameserver/model/World.java`, `gameserver/managers/{GrandBossManager,IdManager}.java`, `gameserver/data/MerchantPriceConfigTable.java` | `04-gameserver-network.md` | Network dispatch, state machine, rolling-xor encryption; representative model/data/managers documented.
- 05 | done | model/actor core | `gameserver/model/actor/{Creature,Playable,Player,Summon,Npc,Attackable,Vehicle,Tower}.java` + `actor/{stat,status,tasks,templates,appearance,holders,enums}/**` | `05-model-actor-core.md` | Inheritance map, fields/state, core control flow, where-to-change table for top-level actor class revisions.
- 06 | done | model/actor templates | `gameserver/model/actor/templates/*.java` | `06-template-layer.md` | CreatureTemplate -> NpcTemplate/PlayerTemplate/DoorTemplate mapping + fields + load hooks.
- 07 | done | actor stat | `gameserver/model/actor/stat/*.java`, player/npc/playable specializations | `07-model-actor-stat.md` | Stat calculators, formulas hooks, base/growth stats, abnormal stat modifiers.
- 08 | done | actor status | `gameserver/model/actor/status/*.java` | `08-model-actor-status.md` | Status update compression, HP/MP/CP state transitions, broadcast hooks.
- 09 | done | actor tasks & holders | `gameserver/model/actor/tasks/**`, `gameserver/model/actor/holders/**`, `actor/enums/**` | `09-model-actor-tasks-holders.md` | Task lifecycle, AI notify queues, holders for aggro/drops/absorbers/etc.
- 10 | done | actor instances | `gameserver/model/actor/instance/**` (59 NPC types) | `10-model-actor-instances.md` | Merchant/Trainer/Doorman/Warehouse/Fisherman/etc instance behaviors.
- 11 | done | item system | `gameserver/model/item/**` (Armor, Weapon, EtcItem, Henna, instance, enchant, recipe, type, holders, enums) | `11-model-item.md` | Static templates, runtime instances, enchant/chip/recipe paths, drop protection holders.
- 12 | done | skill & effects | `gameserver/model/skill/**` + `gameserver/model/effects/**` | `12-model-skill-effects.md` | Skill handlers, BuffInfo lifecycle, effects enumerated.
- 13 | done | clan - 13 | in_progress | clan & siege + residences siege + residences | `gameserver/model/{clan,siege,residences}/**` | `13-model-clan-siege.md` | Doc exists; expand in phase 2.
- 14 | done | olympiad & sevensigns | `gameserver/model/{olympiad,sevensigns}/**` | `14-model-olympiad-sevensigns.md` | Phase 1 summary documented.
- 15 | done | zone, world & misc model | `gameserver/model/{zone,spawns}/**`, `gameserver/model/{World,WorldObject,WorldRegion,Location,StatSet}.java`, `gameserver/model/{html,announce,buylist,captcha,clientstrings,conditions,fishing,groups,instancezone,interfaces,itemcontainer,multisell,options,petition,punishment,script,stats,teleporter,variables,events}/**` | `15-model-zone-world-misc.md` | Phase 1 summary documented.

### Game logic

- 16 | done | AI controllers | `gameserver/ai/**` | `16-ai.md` | Phase 1 summary documented.
- 17 | done | managers part 1 | `gameserver/managers/{Castle,Siege,Zone,ZoneBuild,Town,Instance,GrandBoss,RaidBossSpawn,RaidBossPoints,DayNightSpawn}*.java` | `17-managers-1.md` | Phase 1 summary documented.
- 18 | done | managers part 2 + data loaders | remaining managers + `managers/games/**`, `gameserver/data/**` | `18-data-loaders.md` | Read: ClanTable.java, ItemData.java, ArmorSet.java, StatType.java, SkillData.java, ExperienceData.java, NpcData.java, SpawnData.java, ZoneData.java, RecipeData.java, MultisellData.java, BuyListData.java, TeleportData.java, HennaData.java, FishData.java, PetParamData.java, MerchantPriceConfigTable.java, DataLoaderManager.java, CategoryData.java, DoorData.java, MapRegionData.java, PlayerTemplateData.java, SkillTreeData.java, EnchantItemData.java, EnchantItemGroupsData.java, EnchantItemHPBonusData.java, OptionData.java, ItemCountLimit.java, ItemPlus2.java, ItemUp1.java, PetDataTable.java, HeroSkillTable.java, AugmentationData.java, SpawnTable.java, SchemeBufferTable.java; next: economic/social systems.
- 19 | done | handlers & taskmanagers | `gameserver/handler/**` + `gameserver/taskmanagers/**` | `19-handlers-taskmanagers.md` | Phase 1 summary documented.
- 20 | done | scripting, util, geo, cache, bbs, ui | `gameserver/{scripting,util,geoengine,cache,communitybbs,ui}/**` | `20-scripting-util-geo-misc.md` | Phase 1 summary documented.

### Tools & scripts

- 21 | done | tools & log | `java/org/l2jmobius/{tools,log}/**` | `21-tools-log.md` | All handlers, filters, and formatters documented. |
- 22 | done | scripts: quests part 1 | `dist/game/data/scripts/quests/**` (first half) | `22-scripts-quests-1.md` | Phase 1 summary documented.
- 23 | done | scripts: quests part 2 | `dist/game/data/scripts/quests/**` (second half) | `23-scripts-quests-2.md` | Phase 1 summary documented.
- 24 | done | scripts: ai, village_master, vehicles, events | `dist/game/data/scripts/{ai,village_master,vehicles,events}/**` | `24-scripts-ai-vehicles-events.md` | Phase 1 summary documented.
- 25 | done | scripts: handlers, custom, conquerablehalls | `dist/game/data/scripts/{handlers,custom,conquerablehalls}/**` | `25-scripts-handlers-custom.md` | Phase 1 summary documented.

### Synthesis (cross-cutting, for AI players)

- 26 | done | game mechanics synthesis | cross-read Formulas.java, skill exec, combat, movement, timing | `26-game-mechanics-synthesis.md` | Phase 1 summary documented.
- 27 | done | AI player knowledge base | synthesize: observable state, possible actions, packet sequences for common actions, world query APIs | `27-ai-player-knowledge.md` | Phase 1 summary documented.
- 28 | done | deep line-by-line phase 2 | expand thin docs into per-class audit across all subsystems | `28-deep-phase2-*.md` | Deep review pass.
- 29 | done | known bugs - L2JMobius Interlude | identified TODOs, FIXMEs, potential issues in quest and AI systems | `29-known-bugs-interlude.md` | Bugs and issues documented.
- 30 | done | quest progression systems | quest framework, state management, saga quests for AI player implementation | `30-quest-progression.md` | Quest progression systems analyzed.

## Status legend

- `pending` — not started
- `in_progress` — currently being worked on (only one at a time)
- `done` — output doc written and linked

## Current pointer

- Audit Status: **ALL ITERATIONS 1-30 COMPLETE** ✅
- Last completed: **iteration 30 (quest progression)** — `30-quest-progression.md` written.
- Next planned: Review completed audit for AI player implementation, or begin implementation work on AI player systems.

