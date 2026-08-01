# Scripts: Handlers, Custom & Conquerable Halls Audit (Iteration 25)

## Purpose
Audit of handler classes (`gameserver/handler/`), custom scripts (`dist/game/data/scripts/custom/`), and conquerable hall siege logic (`dist/game/data/scripts/conquerablehalls/`).

## Handler System (`gameserver/handler/`)

### Architecture
The handler system uses a registry pattern with the `IHandler<T, K>` interface:
- `T` — the handler interface (e.g., `IItemHandler`, `IAdminCommandHandler`)
- `K` — the lookup key type (e.g., `EtcItem`, `String` for commands)

Each handler category has a singleton manager that maintains a `Map<K, T>` of registered handlers.

### Handler Categories
| Manager | Interface | Key Type | Purpose |
|---|---|---|---|
| `AdminCommandHandler` | `IAdminCommandHandler` | `String` | Routes GM/admin commands from client packets. |
| `ItemHandler` | `IItemHandler` | `EtcItem` | Handles item usage (potions, scrolls, etc.). |
| `ChatHandler` | `IChatHandler` | `ChatType` | Handles chat commands (e.g., `.revenant`). |
| `VoicedCommandHandler` | `IVoicedCommandHandler` | `String` | Handles voiced commands (e.g., `.help`). |
| `UserCommandHandler` | `IUserCommandHandler` | `Integer` | Handles user commands (party, clan commands). |
| `BypassHandler` | `IBypassHandler` | `String` | Handles NPC dialog bypasses (HTML form actions). |
| `PunishmentHandler` | `IPunishmentHandler` | `PunishmentType` | Handles player punishments (ban, kick, etc.). |
| `TargetHandler` | `ITargetTypeHandler` | `TargetType` | Resolves skill target types. |
| `CommunityBoardHandler` | `IParseBoardHandler` / `IWriteBoardHandler` | `String` | Handles community board (BBS) interactions. |
| `ActionClickHandler` | `IActionClickHandler` | `String` | Handles action button clicks (F1–F12). |
| `ActionShiftHandler` | `IActionShiftHandler` | `String` | Handles shift-click actions on NPCs. |

### Public API (IHandler interface)
| Method | Purpose |
|---|---|
| `registerHandler(T handler)` | Registers a handler in the manager's map. |
| `removeHandler(T handler)` | Removes a handler from the map. |
| `getHandler(K key)` | Retrieves a handler by key. |
| `size()` | Returns number of registered handlers. |

### AdminCommandHandler Details
- **Access control**: Checks `AdminData.getInstance().hasAccess(command, player.getAccessLevel())` before execution.
- **Confirmation**: Supports admin command confirmation dialog (`useConfirm` flag).
- **Async execution**: Commands run via `ThreadPool.execute()` to prevent server freezes.
- **Audit logging**: If `GeneralConfig.GMAUDIT` is enabled, logs command usage via `GMAudit.logAction()`.
- **Performance warning**: Commands taking >5000ms trigger a warning message to the player.

### ItemHandler Details
- **Registration key**: Handler class simple name (e.g., `"Potion"`, `"Scroll"`).
- **Lookup**: By `EtcItem.getHandlerName()` — the handler name is stored in the item's XML template.
- **Interface method**: `onItemUse(Playable, Item, boolean forceUse)` — returns true if used successfully.

## Custom Scripts (`scripts/custom/`)

### Structure
| Module | Purpose |
|---|---|
| `DelevelManager` | Allows players to delevel (lose XP/levels). |
| `EchoCrystals` | Playback of recorded character sounds. |
| `FactionSystem` | Player-vs-player faction system. |
| `FakePlayers` | Simulated bot players for testing. |
| `MissQueen` | Character appearance/stat modification. |
| `NoblessMaster` | Noblesse quest/grade management. |
| `NpcLocationInfo` | NPC location information dialogs. |
| `RaidbossInfo` | Raid boss information display. |
| `SellBuff` | Buff selling service. |
| `ShadowWeapons` | Shadow weapon system. |
| `Transmog` | Transmogrification (appearance change). |
| `events` | Additional custom events. |

### Common Pattern
Custom scripts extend `Quest` or `Script` and register event handlers (NPCs, items, chat commands). They integrate with the existing handler system and game mechanics.

## Conquerable Halls (`scripts/conquerablehalls/`)

### Structure
6 clan hall siege scripts, each extending `ClanHallSiegeEngine`:
| Script | Hall ID | Purpose |
|---|---|---|
| `BanditStronghold` | 35 | Flag war siege for Bandit Stronghold. |
| `DevastatedCastle` | 34 | Siege for Devastated Castle. |
| `FortressOfResistance` | 21 | Siege for Fortress of Resistance. |
| `FortressOfTheDead` | 64 | Siege for Fortress of the Dead. |
| `RainbowSpringsChateau` | 62 | Siege for Rainbow Springs Chateau. |
| `WildBeastReserve` | 63 | Siege for Wild Beast Reserve. |

### ClanHallSiegeEngine Base Class
- **Extends**: `Script implements Siegable`
- **Fields**: `_attackers` (ConcurrentHashMap of SiegeClan), `_guards` (Collection of Spawn), `_hall` (SiegableHall), `_siegeTask` (ScheduledFuture), `_missionAccomplished` (boolean).
- **Constructor**: Takes hall ID, initializes from `CHSiegeManager`, schedules siege start 1 hour before the scheduled time.
- **DB I/O**: Loads/saves attackers from `clanhall_siege_attackers` table. Loads/saves guards from `clanhall_siege_guards` table.
- **Lifecycle**: Siege progresses through states (PREPARATION, CLOSED, REGISTRATION, RUNNING, FINISHED) via scheduled tasks.

### BanditStronghold Specifics
- **NPCs**: ROYAL_FLAG (35422), FLAG_RED (35423), ALLY_1–4 (35428–35431)
- **Mechanics**: Flag war — clans compete to control flags. Uses `SpecialSiegeGuardAI` for guard behavior.
- **DB**: Uses `siegable_hall_flagwar_attackers` and `siegable_hall_flagwar_attackers_members` tables.
- **Pattern**: Extends ClanHallSiegeEngine, overrides siege event handlers for custom flag-war mechanics.

## Gotchas / Refactor Candidates
- Handler lookup uses string-based keys — no type safety for command/item handler names.
- `AdminCommandHandler.getHandler()` strips command arguments by splitting on space — may break commands with spaces.
- `ItemHandler` registers by class simple name — renaming a handler class breaks item templates.
- `ClanHallSiegeEngine` hardcodes hall IDs as constants — adding new halls requires code changes.
- Custom scripts have no standardized registration — each self-instantiates in its `main()` method.
- `BanditStronghold` uses different DB table names than `ClanHallSiegeEngine` base class — inconsistent schema.

## Notes (Resume Checkpoint)
- Read files:
  - `java/org/l2jmobius/gameserver/handler/AdminCommandHandler.java`
  - `java/org/l2jmobius/gameserver/handler/ItemHandler.java`
  - `java/org/l2jmobius/gameserver/handler/IItemHandler.java`
  - `dist/game/data/scripts/conquerablehalls/BanditStronghold/BanditStronghold.java`
  - `java/org/l2jmobius/gameserver/model/siege/clanhalls/ClanHallSiegeEngine.java` (structure)
  - `dist/game/data/scripts/custom/` (directory listing)
- Still to read: individual handler implementations (IAdminCommandHandler, IChatHandler, etc.), remaining custom scripts, and other conquerable hall scripts.
- Key structural findings: Handler system uses registry pattern with singleton managers. Custom scripts are self-registering. Conquerable halls extend ClanHallSiegeEngine with hall-specific logic.
- Next step: read handler implementations and remaining custom/conquerable hall scripts.