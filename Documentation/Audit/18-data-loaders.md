# 18 — data loaders (deep)

## Summary
The data loader subsystem is responsible for loading all static game data (items, NPCs, skills, clans, etc.) from persistent storage (SQL databases and XML files) into memory structures that are used throughout the server runtime. This subsystem ensures fast, concurrent access to immutable or rarely-changing data via cache-like managers (often implemented as singletons with concurrent maps). The data loaders are organized under `gameserver/data/` with subpackages `sql`, `xml`, `holders`, and `enums`. They follow a consistent pattern: a singleton class loads data during server startup (or reload) and provides read‑only accessors for the rest of the server.

Key responsibilities:
- Load and parse SQL tables into domain objects (e.g., `ClanTable`, `NpcTable`).
- Parse XML definition files into typed object collections (e.g., `ItemData`, `SkillData`).
- Provide holder classes that aggregate related data (e.g., `ArmorSet`, `TeleportLocationList`).
- Define enumerated constants used across the system (e.g., `StatType`, `Slot`).
- Support hot‑reload of certain data sets via `reload()` methods.
- Ensure thread‑safe reads using `ConcurrentHashMap` or synchronized collections.

## Expanded per‑class / per‑package audit

### SQL Data Loaders
*Pattern*: Extend `java.lang.Object`, implement a private constructor, load data via JDBC in a static init block or constructor, store in `ConcurrentHashMap<Integer, T>` or similar, provide `getObject(id)` and `getAllObjects()` methods, and expose a `getInstance()` singleton.

#### ClanTable (`gameserver/data/sql/ClanTable.java`)
- **Purpose**: Loads and manages clan records from the `clans` and related tables (`clan_skills`, `clan_wars`, `allies`, etc.).
- **Fields / State**:
  - `private final Map<Integer, Clan> _clans = new ConcurrentHashMap<>();` – thread‑safe map of clan ID to `Clan` object.
- **Public API Surface**:
  - `Clan getClan(int clanId)` – returns clan by ID.
  - `boolean createClan(Player player, String clanName)` – creates a new clan, inserts into DB, adds to map.
  - `boolean destroyClan(Clan clan)` – removes clan from DB and map.
  - `void updateClanInDB(Clan clan)` – persists changes.
  - Methods for managing allies, enemies, wars, siege participation, clan skills, reputation, rank calculation.
  - `void shutdown()` – saves all clans to DB on server shutdown.
- **Control Flow**:
  - Singleton instance created at class loading (`StaticHolder.INSTANCE`).
  - Constructor calls `restore()` which reads all clans from `clans` table, for each clan loads members, skills, wars, allies, etc.
  - Clan creation/deletion/update flow goes through DAO‑style methods that execute prepared statements.
  - Background tasks (e.g., war scheduling) are managed via `ThreadPool`.
- **I/O**:
  - Reads: `SELECT * FROM clans`, `SELECT * FROM clan_members WHERE clanId = ?`, `SELECT * FROM clan_skills WHERE clanId = ?`, etc.
  - Writes: `INSERT INTO clans (...)`, `UPDATE clans SET ... WHERE clan_id = ?`, `DELETE FROM clans WHERE clan_id = ?`, plus analogous statements for allied tables.
- **Gotchas / Refactor Candidates**:
  - The class is large (~1200 lines) and mixes data access with business logic (e.g., war management, ally handling). Consider separating pure DAO (`ClanDao`) from cache/service (`ClanManager`).
  - Some SQL statements are built via string concatenation; using `PreparedStatement` everywhere is good, but a few places still use `Statement` for simple queries (e.g., `restoreClanWars()` uses `Statement` – acceptable as no user input).
  - The class directly manipulates `Clan` model objects, causing tight coupling; however, this is common in the codebase.
### XML Data Loaders
*Pattern*: Similar singleton, but loads from XML files in `data/stats/` using custom `Document` parsers (e.g., `DocumentItem`, `DocumentSkill`). Data is parsed into domain model objects (e.g., `ItemTemplate`, `NpcTemplate`) and stored in arrays or maps for O(1) ID‑based lookup.

#### ItemData (`gameserver/data/xml/ItemData.java`)
- **Purpose**: Central repository for all item templates (weapons, armor, etc.) loaded from XML files under `data/stats/items/`.
- **Fields / State**:
  - `private ItemTemplate[] _allTemplates;` – dense array indexed by item ID for fastest access.
  - Separate maps for quick category access: `_etcItems`, `_armors`, `_weapons` (all `HashMap<Integer, T>`).
  - `List<File> _itemFiles` – tracks XML files to process.
- **Public API Surface**:
  - `ItemTemplate getTemplate(int id)` – O(1) array lookup with bounds check.
  - Various `getAll*Id()` and `getAll*()` methods returning key sets or collections for each category.
  - `reload()` – clears caches and re‑parses all XML files.
- **Control Flow**:
  - Constructor calls `processDirectory()` to collect XML file paths, then `load()`.
  - `load()` iterates over files, uses `DocumentItem` to parse each into an `ItemTemplate` instance, dispatches to appropriate map based on item type (`EtcItem`, `Armor`, `Weapon`).
  - After parsing, builds the combined `_allTemplates` array.
  - Supports multithreaded parsing if `ThreadConfig.THREADS_FOR_LOADING` is true (uses a fixed‑size thread pool).
- **I/O**:
  - Reads XML files via `File` objects; no network or DB access.
  - Parsing uses custom SAX‑like utilities (`DocumentItem`) – efficient and low‑memory.
- **Gotchas / Refactor Candidates**:
  - The class is relatively concise but still does multiple responsibilities (file discovery, parsing, caching). Could be split pojo into an `ItemLoader` and `ItemRepository`.
  - The `reload()` method also forces reload of dependent data (e.g., `EnchantItemHPBonusData`) – creates hidden coupling; consider an event‑bus or explicit reload mechanism.
  - No validation of duplicate IDs beyond array overwrite; duplicate IDs in XML silently replace earlier entries.
### Holder Classes
*Pattern*: Plain old Java objects (POJOs) that hold related data, often with helper methods to check conditions or compute aggregates. Frequently used by skill AI, AI‑player decision making, or game logic.

#### ArmorSet (`gameserver/data/holders/ArmorSet.java`)
- **Purpose**: Defines an armor set bonus: which pieces (head, chest, legs, gloves, feet, shield) constitute the set, what stat bonuses and skills are granted when the full set is equipped, and special enchant‑level effects.
- **Fields / State**:
  - Lists of acceptable item IDs for each slot (`_chestId` single int, `_legs`, `_head`, `_gloves`, `_feet`, `_shield` as `List<Integer>`).
  - Lists of skill holders for normal skills, shield skills, and enchant‑6 skills.
  - Integer stats for CON, DEX, STR, MEN, WIT, INT modifiers.
- **Public API Surface**:
  - Builder‑style adders (`addChest(int)`, `addLegs(int)`, …) to populate the sets.
  - Query methods: `containAll(Player)`, `containShield(Player)`, `isEnchanted6(Player)`.
  - Getters for stats and skill lists.
- **Control Flow**:
  - Instances are created by XML parsers (e.g., `ArmorSetData`) when reading armor‑set definitions.
  - At runtime, skills AI or item‑equip logic calls `containAll(player)` to decide whether to apply set bonuses.
- **I/O**:
  - None – pure in‑memory data holder.
- **Gotchas / Refactor Candidates**:
  - The class uses mutable lists; after construction they are never modified, so they could be made `final` and the adders could be called only during initialization.
  - The containment checks iterate over lists each time; for large lists this could be optimized with `HashSet`.
  - The class is tightly coupled to `Player` and `Inventory`; consider passing only the needed item IDs (decoupling).
### Enumerations
*Pattern*: Simple `enum` classes defining constants used throughout the codebase (e.g., stats, slots, elemental values). Often include Javadoc comments explaining usage.

#### StatType (`gameserver/data/enums/StatType.java`)
- **Purpose**: Enumerates character statistics that can be modified by skills, items, or effects.
- **Fields / State**:
  - Enum constants: `HP`, `MP`, `XP`, `SP`, `GIM` (grab item modifier), plus placeholders for future stats.
- **Public API Surface**:
  - Inherits `Enum` methods (`name()`, `ordinal()`, `valueOf(String)`, `values()`).
- **Control Flow**:
  - No logic; used as a type‑safe parameter in skill effect classes, formulas, and condition checks.
- **I/O**:
  - None.
- **Gotchas / Refactor Candidates**:
  - The enum is sparsely populated; many stats are represented as hard‑coded strings elsewhere (e.g., in `Func` subclasses). Consider expanding the enum to cover all stats for stronger typing.
  - The comment for `GIM` is incomplete; should be clarified.
### Other Notable Data Loaders
- **NpcTable** (`gameserver/data/sql/NpcTable.java`): Loads NPC templates, AI scripts, and spawn data; similar pattern to `ClanTable`.
- **SkillTable** (`gameserver/data/sql/SkillTable.java`): Loads skills from `skills` table, loads skill trees, and handles skill acquisition logic.
- **NpcData** (`gameserver/data/xml/NpcData.java`): Parses XML NPC definitions (similar to `ItemData`).
- **SkillData** (`gameserver/data/xml/SkillData.java`): Parses XML skill definitions.
- **SpawnData** (`gameserver/data/xml/SpawnData.java`): Parses spawn points and creates `Spawn` objects.
- **TeleporterData** (`gameserver/data/xml/TeleporterData.java`): Loads teleporter NPC lists and locations.
- **Held items** like `HennaData`, `RecipeListTable`, `FishData`, etc., each following the same loader pattern.
## Cross‑Cutting Observations
1. **Consistent Singleton Pattern**: Almost all data loaders use the holder‑static‑inner‑class singleton idiom, ensuring lazy, thread‑safe initialization.
2. **Separation of Storage**: SQL loaders tend to store data in `ConcurrentHashMap` for concurrent reads; XML loaders often use a combination of maps and a flat array for fastest ID‑based lookup.
3. **Immutability After Load**: Once loaded, the data structures are effectively immutable (no setters exposed), which simplifies reasoning for AI‑player systems.
4. **Reload Support**: Most loaders provide a `reload()` method that re‑parses source files/tables, enabling hot‑updates without server restart (used by GMs and automated scripts).
5. **Potential Improvements**:
   - Introduce a generic `DataLoader<T>` interface to enforce a common contract (`load()`, `reload()`, `getObject(int)`).
   - Replace ad‑hoc XML parsers with a standard library (e.g., JAXB) for better maintainability, though custom parsers are faster.
   - Consider moving to a dependency‑injection framework for testability, though the static approach works well for a game server.
6. **AI‑Player Relevance**: All data exposed by these loaders is read‑only after initialization, making it safe for AI bots to query without synchronization concerns. The deterministic loading order ensures that AI can rely on the same data set as human players.
## Where to Change X
| Concern | Class / Method | Actionable Change |
|---------|----------------|-------------------|
| Add a new stat type (e.g., `Accuracy`) | `StatType` enum | Add `ACCURATE` constant and update all places that reference stats to use the enum. |
| Change how clan data is cached (e.g., add LRU eviction) | `ClanTable._clans` | Replace `ConcurrentHashMap` with a cache library (Caffeine) and adjust get/put logic. |
| Add a new XML‑based data type (e.g., `MountData`) | Create new class under `data/xml/` following `ItemData` pattern; register in `DataLoaderManager` if exists. |
| Optimize holder lookup (e.g., `ArmorSet.containsAll`) | `ArmorSet._legs`, etc., change from `List<Integer>` to `IntArraySet` or `HashSet<Integer>` for O(1) contains. |
| Introduce validation for duplicate IDs in XML loaders | `ItemData.load()` | Before inserting into map, check if ID already exists and log warning or throw. |
## Conclusion
The data loader subsystem is a solid, performant foundation for the game’s static data. Its clear separation of concerns (SQL vs. XML, holders, enums) and adherence to singleton/caching patterns make it easy to reason about and safe for concurrent access by game logic and AI agents. Minor refactoring could improve modularity and type safety, but the current implementation meets the needs of a high‑performance MMO server.
The subsystem is critical for AI‑player reasoning because most game‑world knowledge (item stats, NPC abilities, skill effects) originates from these loaded data structures.