# Scripts: AI, Village Master, Vehicles, Events Audit (Iteration 24)

## Purpose
Audit of AI scripts, village master scripts, vehicle scripts, and event scripts under `dist/game/data/scripts/{ai,village_master,vehicles,events}/*`.

## AI Scripts (`ai/`)

### Structure
Three subdirectories:
- **`ai/areas/`** — Area-specific AI (CrumaTower, DwarvenVillage, ForestOfTheDead, HotSprings, etc.)
- **`ai/bosses/`** — Boss AI (Antharas, Baium, Core, Frintezza, Orfen, QueenAnt, Sailren, Valakas, Zaken, DrChaos)
- **`ai/others/`** — Miscellaneous NPC AI (Chests, Gordon, HealerTrainer, ArenaManager, etc.)

### AI Types
Most AI scripts extend `org.l2jmobius.gameserver.model.script.Script` (not `Quest`). Key event hooks:
| Hook | Purpose |
|---|---|
| `onAttack(Npc, Player, int damage, boolean isSummon)` | NPC attacked by player. |
| `onAttack(Npc, Player, int damage, boolean isSummon, Skill skill)` | Attack with skill context. |
| `onKill(Npc, Player killer, boolean isSummon)` | NPC killed by player. |
| `onSkillSee(Npc, Player caster, Skill skill, List<WorldObject> targets, boolean isSummon)` | Skill used targeting this NPC. |
| `onFirstTalk(Npc, Player)` | First conversation with NPC (returns HTML). |
| `onSpawn(Npc)` | NPC spawned. |
| `onMoveFinished(Npc)` | NPC finished moving. |
| `onEnterZone(Creature, ZoneType)` | Creature enters a zone. |
| `onExitZone(Creature, ZoneType)` | Creature exits a zone. |
| `onSummonSpawn(Summon)` | Player summon spawned. |

### Registration Methods (inherited from Quest/Script)
| Method | Purpose |
|---|---|
| `addAttackId(int...)` | Register NPCs that trigger `onAttack`. |
| `addKillId(int...)` | Register NPCs that trigger `onKill`. |
| `addFirstTalkId(int...)` | Register NPCs that trigger `onFirstTalk`. |
| `addSkillSeeId(int...)` | Register NPCs that trigger `onSkillSee`. |
| `addSpawnId(int...)` | Register NPCs that trigger `onSpawn`. |
| `addEnterZoneIds(int...)` | Register zones for `onEnterZone`. |
| `addExitZoneIds(int...)` | Register zones for `onExitZone`. |
| `addTalkId(int...)` | Register NPCs for conversation. |

### Representative AI: Chests.java
- **NPCs**: 70 treasure chest NPC IDs (18265–18298, 21671–21822).
- **Mechanics**: Chests explode ("treasureBomb") when attacked or force-opened with a non-unlock skill. Mimics retaliate as normal monsters.
- **Pattern**: Uses `instanceof Chest` check, `isBox()`, `isInteracted()` state tracking.
- **Registration**: `addAttackId` + `addSkillSeeId` for all chest IDs.

### Boss AI Patterns
- Bosses typically extend `Quest` and manage complex state machines (stages, timers, skill rotations).
- Use `addSpawn`, `startQuestTimer`, `broadcastPacket` for cinematic effects.
- Handle player count checks, revive mechanics, and special attacks.

## Village Master Scripts (`village_master/`)

### Structure
15 subdirectories, each handling a specific class transfer or clan function:
- **Class transfers**: `FirstClassTransferTalk`, `DarkElfChange1/2`, `OrcChange1/2`, `ElfHumanFighterChange1/2`, `ElfHumanWizardChange1/2`, `DwarfBlacksmithChange1/2`, `DwarfWarehouseChange1/2`, `ElfHumanClericChange2`
- **Clan/residence**: `ClanMaster`, `AllianceMaster`

### Representative: ClanMaster.java
- **NPCs**: 110+ clan leader NPCs across all towns.
- **Mechanics**: Handles clan creation, clan leveling, clan skills, dissolution, disbanding.
- **Pattern**: Uses `onEvent` to route dialog button clicks to HTML responses. `LEADER_REQUIRED` map redirects non-leaders to "no" variants of HTML pages.
- **Registration**: `addStartNpc(NPCS)` + `addTalkId(NPCS)` for all clan master NPCs.

### Representative: FirstClassTransferTalk.java
- Handles first-class transfer (level 20) dialog for all races.
- Uses `onFirstTalk` to return the appropriate HTML based on player race/class.

## Vehicle Scripts (`vehicles/`)

### Structure
7 boat/vehicle route scripts, each implementing `Runnable`:
- `BoatGiranTalking` — Giran ↔ Talking Island (868s trip)
- `BoatGludinRune` — Gludin ↔ Rune
- `BoatInnadrilTour` — Innadril sightseeing tour
- `BoatRunePrimeval` — Rune ↔ Primeval Isle
- `BoatTalkingGludin` — reverse of GludinRune
- `BoatHarborRune` — Harbor ↔ Rune
- `BoatRuneGiran` — Rune ↔ Giran

### Mechanics
- Each boat route is a state machine with 18+ cycle steps (cases 0–18).
- Uses `VehiclePathPoint[]` arrays defining waypoints (x, y, z, heading, speed).
- `BoatManager` manages dock occupancy, broadcasts arrival/departure packets.
- `ThreadPool.schedule(this, delayMs)` schedules each step transition.
- `main()` creates a `Boat` instance via `BoatManager.getInstance().getNewBoat()`, registers the engine, and starts it.
- Boats charge Adena (item 57) for rides via `_boat.payForRide()`.

## Event Scripts (`events/`)

### Structure
| Event | Purpose |
|---|---|
| `Christmas` | Santa NPC buffs nearby players every 15s during event period. |
| `HeavyMedal` | Medal-based event with special drops and rewards. |
| `L2Day` | Letter collection event. |

### LongTimeEvent Base Class
- `LongTimeEvent` extends `Script` and provides:
  - Date-range-based activation (`_startDate`, `_endDate`).
  - Drop period support (`_dropStartDate`, `_dropEndDate`).
  - NPC spawn management (`_spawnList`).
  - Event-specific drops (`_dropList`).
  - Item cleanup on event end (`_destroyItemsOnEnd`).
  - Configurable via `data/scripts/events/<EventName>/config.xml`.
  - Auto-schedules start/end via `ThreadPool.schedule(new ScheduleStart(), delay)`.

### Representative: Christmas
- **NPC**: SANTA (31863)
- **Buffs**: Fighter and Mage buff arrays using `SkillHolder` (Wind Walk, Shield, Magic Barrier, etc.).
- **Mechanics**: On spawn, starts a 15-second repeating timer (`startQuestTimer("SantaBlessings", 15000, npc, null)`). Timer iterates nearby players, casts appropriate buffs, and reschedules itself.
- **Registration**: `addStartNpc`, `addFirstTalkId`, `addTalkId`, `addSpawnId` for SANTA.

## Gotchas / Refactor Candidates
- AI scripts extend `Script` but many also extend `Quest` — inconsistent base class usage.
- Vehicle scripts use `Runnable` instead of `Script` — different lifecycle model.
- No centralized event registration; each script self-registers in its constructor.
- `LongTimeEvent` config XML parsing is embedded — no shared schema validation.
- Boss AI scripts are large and monolithic — difficult to maintain individual boss mechanics.

## Notes (Resume Checkpoint)
- Read files:
  - `dist/game/data/scripts/ai/others/Chests.java`
  - `dist/game/data/scripts/vehicles/BoatGiranTalking.java`
  - `dist/game/data/scripts/village_master/ClanMaster/ClanMaster.java`
  - `dist/game/data/scripts/events/Christmas/Christmas.java`
  - `java/org/l2jmobius/gameserver/model/script/LongTimeEvent.java` (structure)
  - `java/org/l2jmobius/gameserver/model/script/Quest.java` (registration methods)
- Still to read: individual AI scripts for bosses, area-specific AI, and remaining village master and event scripts.
- Key structural findings: AI scripts use event-driven callbacks via `Script` or `Quest` base. Village masters handle class transfers and clan management. Vehicles are Runnable state machines with hardcoded paths. Events extend `LongTimeEvent` for date-based activation.
- Next step: read boss AI scripts and remaining village master scripts to complete the audit.