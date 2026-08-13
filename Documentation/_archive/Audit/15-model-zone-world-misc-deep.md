# 15 — model/zone, world & misc (deep audit)

Resume checkpoint
- Current status: PROGRESS.md marks iteration 15 as **in_progress**.
- Scope: deep, file‑by‑file expansion of the thin phase‑1 summary (`15-model-zone-world-misc.md`).
- Target files to read:
  - gameserver/model/World.java (lines 221‑460)
  - gameserver/model/WorldRegion.java (lines 1‑220)
  - gameserver/model/Location.java
  - gameserver/model/zone/Zone.java (top 200 lines)
  - gameserver/model/zone/type/*.java (TownZone, SiegeZone, BossZone, FishingZone – top 100 each)
  - gameserver/model/spawns/Spawn.java (top 200 lines)
- Deliverable per‑class / per‑package audit using the exact template:
  - Purpose
  - Fields / State
  - Public API Surface
  - Control Flow
  - I/O
  - Gotchas / Refactor Candidates
  - Where to change X table linking concerns to class/method.
- Append a concise runtime log (≤70 lines).
- Update PROGRESS.md: mark 15 **done**, set 16 as **in_progress**.
- Commit the new markdown and updated PROGRESS.md.
- Runtime log limited to ≤70 lines for traceability.

---

## Expanded audit – World/Zone model

### World.java (gameserver/model/World.java: 221‑460)
- **Purpose** – Top‑level container that holds all world objects, manages global collections (`_allObjects`, `_allPlayers`, `_allGoodPlayers`, `_allEvilPlayers`), and coordinates inter‑object visibility.
- **Fields / State** – `_allObjects: ConcurrentHashMap<Integer, WorldObject>`, `_allPlayers: ConcurrentMap<Integer, Player>`, `_zones: byte[]` for zone counts, `_worldRegion: WorldRegion`, event listener sets, spawn‑related flags.
- **Public API Surface** – `addVisibleObject(WorldObject)`, `removeVisibleObject(WorldObject)`, `findObject(int)`, `getPlayers()`, `getAllGoodPlayers()`, `getAllEvilPlayers()`, `getVisibleObjects()`, `broadcastPacket(ServerPacket)`, `teleToLocation(int, int, int, int, int, int)`, `onDecay()`, `onTeleported()`.
- **Control Flow** – Object registration occurs during spawn (`addSpawn`), visibility is established via `WorldRegion` neighbour lists; `broadcastPacket` iterates `_knownPlayers`; `teleToLocation` uses `GeoEngine` for coordinate validation; `onDecay` triggers cleanup and optional disconnection.
- **I/O** – No direct file I/O; world state is persisted indirectly via DB operations in `CharInfoTable`, `NpcTable`, and `Spawn` managers; zone configurations are loaded from XML via `ZoneManager`.
- **Gotchas / Refactor Candidates** – Over‑exposed collections risk accidental external mutation; `addVisibleObject`/`removeVisibleObject` semantics differ for `_allObjects` vs region maps; `teleToLocation` performs extensive geodetic validation that can become performance bottleneck under heavy load.

### WorldRegion.java (gameserver/model/WorldRegion.java: 1‑220)
- **Purpose** – Represents a 256×256 pixel grid cell; maintains visibility lists, neighbor relationships, and zone‑specific properties.
- **Fields / State** – `_objects: Collection<WorldObject>`, `_players: Collection<Player>`, `_npcs: Collection<Npc>`, `_monsters: Collection<Npc>`, `_summons: Collection<Summon>`, neighbor arrays (`_adjacentRegions`), zone type flag, instanceId.
- **Public API Surface** – `addObject(WorldObject)`, `removeObject(WorldObject)`, `getVisibleObjects()`, `getNeighbors()`, `isActive()`, `isProtected()`, `getSpawn()`, `setInstanceId(int)`.
- **Control Flow** – Object addition triggers neighbor list updates; visibility is checked before packet broadcasting; region activation sets a flag used by movement and AI tasks.
- **I/O** – Region data is loaded from `config/zone/region/`, stored in `ZoneManager` singleton; no runtime file writes.
- **Gotchas / Refactor Candidates** – Neighbor computation is done on-the‑fly, which can be costly; no caching of adjacency lists; removal logic does not clean up stale references in neighbor arrays.

### Location.java
- **Purpose** – Immutable 3‑D coordinate holder used throughout the engine for position representation.
- **Fields / State** – `x: int`, `y: int`, `z: int`, optional `heading` in some subclasses.
- **Public API Surface** – Constructors, `getX()`, `getY()`, `getZ()`, `compareTo(Location)`, `distanceTo(Location)`, `toString()`.
- **Control Flow** – Used by `WorldObject` for position tracking; fed into `GeoEngine` for path‑finding and line‑of‑sight checks.
- **I/O** – None; created programmatically.
- **Gotchas / Refactor Candidates** – Lack of validation for extreme coordinate values; frequent object creation can cause GC pressure if not pooled.

### Zone.java (top 200 lines)
- **Purpose** – Abstract base defining common zone behaviour (e.g., `isInstance()`, `isProtected()`, `getSpawn()`, `getOwnerId()`). Concrete types inherit from it.
- **Fields / State** – `id: int`, `type: byte`, `params: ZoneSettings` (damage/modify flags), `active: boolean`.
- **Public API Surface** – `isInside(Location)`, `getSpawn()`, `setActive(boolean)`, `getType()`, `getParameters()`.
- **Control Flow** – Zones are registered with `ZoneManager` during server load; they are consulted by `World` for visibility and movement restrictions.
- **I/O** – Configuration loaded from `config/zone/*.xml` via `ZoneLoader`; persists zone lifecycle via XML descriptors.
- **Gotchas / Refactor Candidates** – Type flag is a raw byte leading to possible invalid values; insufficient validation of `params` fields.

### TownZone, SiegeZone, BossZone, FishingZone (inherited types)
- **Purpose** – Specialized zones that add extra mechanics:
  - *TownZone*: merchant interaction, town‑gate control, shop access.
  - *SiegeZone*: siege‑engine spawning, castle gate control, PvP‑flag toggles.
  - *BossZone*: boss‐spawn tracking, increased monster HP, special loot tables.
  - *FishingZone*: fishing‑spot activation, weather effects.
- **Fields / State** – Additional flags (`_townNpcId`, `_castleSiegeId`, `_fishingRate`), timers for dynamic events.
- **Public API Surface** – Event‑trigger methods (`onEnterWorld`, `onAttack`, `onDie`), reward distribution functions, timer scheduling.
- **Control Flow** – Event hooks are registered via `EventDispatcher`; timers are scheduled using `GameTimeTaskManager`; updates are persisted to DB on completion.
- **I/O** – Zone‑specific configs (`townzone.xml`, `siegezone.xml`, etc.) parsed at startup.
- **Gotchas / Refactor Candidates** – Direct coupling to `World` internals makes independent testing difficult; timers not automatically cleared on zone unload.

### Spawn.java (gameserver/model/spawns/Spawn.java: 1‑200)
- **Purpose** – Central repository for NPC/Monster/Human/Item spawn data; responsible for creation, deletion, and respawning of entities.
- **Fields / State** – `template: NpcTemplate`, `maxCount: int`, `currentCount: int`, `spawnList: List<Integer>`, `respawnDelay: int`, `isAutoSpawnEnabled: boolean`, `listOfSpawned: Set<Integer>`, coordinates (`x,y,z`), heading, instanceId.
- **Public API Surface** – `spawn()`, `despawn()`, `onDeath()`, `respawnTimerTask()`, `getAllNpcsInZone()`, `getSpawnRate()`.
- **Control Flow** – On server start, spawns are loaded from XML; each spawn entry schedules a respawn task if `isAutoSpawnEnabled`; `spawn()` verifies free space, updates count, registers object in `World`; on death `despawn()` removes from collections and may schedule cleanup.
- **I/O** – Spawn definitions in `config/spawns/*.xml`; parsing uses `SpawnParser`; DB interaction for persistent spawns via `SpawnTable`.
- **Gotchas / Refactor Candidates** – `maxCount` vs `currentCount` checks are not atomic, potential race condition; respawn timer handling lacks cancellation on server shutdown; spawn list mutation during iteration can cause `ConcurrentModificationException`.

---

## Where to change X

| Concern | Class / Method | Actionable Change | Related Files |
|---------|----------------|-------------------|--------------|
| Adjust global visibility radius | `WorldRegion` | Modify neighbor‑list generation and `_visibleObjects` populating logic |
| Change respawn timing | `Spawn` | Edit `respawnDelay` load from XML or hard‑code |
| Add new zone type (e.g., PvPZone) | `Zone` subclasses | Create subclass with extra PvP flag and override `isProtected()` |
| Modify teleport validation | `World.teleToLocation` | Add additional geodetic checks or safety validations |
| Improve zone activation performance | `WorldRegion` | Cache neighbor list or use bit‑mask flags |
| Refactor spawn list mutation | `Spawn` | Synchronize list access or use `CopyOnWriteArrayList` |

---

## Cross‑cutting Impact

- Modifications affect **packet routing** (`ServerPacket` broadcasts), **AI intention** calculations (visibility triggers `Intention` changes), **database persistence** (`WorldObject` saving), and **event dispatching** (zone entry/exit events).
- Changing zone activation logic may require updates to `WorldRegion` neighbor calculation and consequently to `Player` movement handling.
- Adding new zone types requires corresponding packet opcode updates in `ServerPackets` (e.g., `ZoneInfo` messages).

---

## Runtime Log
