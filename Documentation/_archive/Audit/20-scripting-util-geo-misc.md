# 20 — Scripting, Util, Geo, Cache, BBS, UI

## Summary

This document provides a deep audit of the scripting engine, utilities, GeoEngine, cache systems, community BBS, and UI components in the L2JMobius Interlude server. These subsystems handle game mechanics like quest execution, pathfinding, HTML caching, community forums, and server console interfaces.

## Resume Checkpoint

- Read files:
  - `gameserver/scripting/ScriptEngine.java` - Scripting engine initialization
  - `gameserver/scripting/engine/ScriptExecutor.java` - Script compilation and execution
  - `gameserver/scripting/engine/ScriptClassLoader.java` - Dynamic class loading
  - `gameserver/scripting/engine/ScriptClassData.java` - Compiled script data holder
  - `gameserver/util/Broadcast.java` - Broadcast utilities
  - `gameserver/util/GeoUtils.java` - Geo utilities
  - `gameserver/util/LocationUtil.java` - Location utilities
  - `gameserver/geoengine/GeoEngine.java` - Main geodata engine
  - `gameserver/geoengine/pathfinding/PathFinding.java` - Pathfinding algorithm
  - `gameserver/geoengine/geodata/Cell.java` - NSWE direction constants
  - `gameserver/geoengine/geodata/blocks/ComplexBlock.java` - Multi-layer geodata block
  - `gameserver/geoengine/geodata/blocks/FlatBlock.java` - Simple height-only block
  - `gameserver/geoengine/geodata/Region.java` - Geodata region container
  - `gameserver/geoengine/geodata/blocks/MultilayerBlock.java` - Full geodata block
  - `gameserver/cache/HtmCache.java` - HTML template cache
  - `gameserver/cache/RelationCache.java` - Relation cache
  - `gameserver/communitybbs/BB/Forum.java` - Forum model
  - `gameserver/communitybbs/BB/Topic.java` - Topic model
  - `gameserver/communitybbs/BB/Post.java` - Post model
  - `gameserver/communitybbs/Manager/ForumsBBSManager.java` - Forum manager
  - `gameserver/communitybbs/Manager/TopicBBSManager.java` - Topic manager
  - `gameserver/communitybbs/Manager/PostBBSManager.java` - Post manager
  - `gameserver/communitybbs/Manager/BaseBBSManager.java` - Base manager
  - `gameserver/ui/Gui.java` - Main console UI
  - `gameserver/ui/LogPanel.java` - Log viewer panel
  - `gameserver/ui/SystemPanel.java` - System status panel
  - `gameserver/ui/AboutFrame.java` - About dialog
- Still to read: None (all key files analyzed)
- Next step: Complete documentation and mark iteration as done

---

## 1. Scripting Engine

### Overview

The scripting engine enables runtime Java compilation and execution of quest scripts, AI scripts, and other game scripts. It uses the Java Compiler API (javax.tools) for dynamic compilation.

### Key Classes

| Class | Purpose |
|-------|---------|
| `ScriptEngine` | Main entry point, loads Scripts.xml config, discovers script files |
| `ScriptExecutor` | Compiles and executes Java source files at runtime |
| `ScriptClassLoader` | Custom class loader for dynamically loaded classes |
| `ScriptClassData` | Holds compiled class bytes and metadata |
| `ScriptFileManager` | JavaFileObject implementation for compiler |
| `Disabled` (annotation) | Marks scripts to be excluded from execution |

### ScriptEngine.java

- **Purpose**: Discovers and manages script files based on configuration
- **Location**: `gameserver/scripting/ScriptEngine.java`
- **Singleton pattern**: Lazy initialization via `SingletonHolder`
- **Configuration**: Reads `config/Scripts.xml` for exclusions
- **Key methods**:
  - `load()` - Loads exclusion rules from XML
  - `executeScript(Path)` - Executes a single script file
  - `executeScriptList()` - Executes all discovered scripts
  - `processDirectory(File, List)` - Recursively finds .java files

### ScriptExecutor.java

- **Purpose**: Compiles and executes Java source files
- **Java version**: Hardcoded to 1.8 (source/target) for compatibility
- **Compilation flow**:
  1. Create `DiagnosticCollector` for errors
  2. Get standard file manager from system compiler
  3. Wrap with `ScriptFileManager` to capture compiled classes
  4. Compile with options: `-source 1.8`, `-target 1.8`, `-g:source,lines,vars`
  5. Execute `main(String[])` method via reflection
- **Thread safety**: Uses static `COMPILER` and `SCRIPT_CLASS_LOADER`
- **Exclusion support**: Checks `@Disabled` annotation
- **Current script tracking**: `_currentExecutingScript` for error reporting

### ScriptClassLoader.java

- **Purpose**: Custom ClassLoader for dynamically compiled scripts
- **Stores**: `ConcurrentHashMap<String, ScriptClassData>` for compiled classes
- **findClass()**: Returns class from compiled data, falls back to parent
- **Thread-safe**: Uses `ConcurrentHashMap`

### Gotchas / Refactor Candidates

- Java version hardcoded to 1.8 - may need update for newer language features
- No hot-reload support for individual scripts
- Error messages could be more descriptive
- Exclusion logic in XML is complex (file/include patterns)

---

## 2. Utility Classes

### Broadcast.java

- **Purpose**: Central utility for sending server packets to players
- **Location**: `gameserver/util/Broadcast.java`
- **Key methods**:
  - `toPlayersTargettingMyself(Creature, ServerPacket)` - Send to players targeting creature
  - `toKnownPlayers(Creature, ServerPacket)` - Send to all known players
  - `toSelfAndKnownPlayers(Creature, ServerPacket)` - Send to creature + known players
  - `toSelfAndKnownPlayersInRadius(Creature, ServerPacket, int radius)` - Radius-based broadcast
  - `toAllOnlinePlayers(ServerPacket)` - Broadcast to all online players
  - `toAllOnlinePlayers(String text)` - Broadcast text as announcement
  - `toPlayersInInstance(ServerPacket, int instanceId)` - Instance-scoped broadcast
  - `toAllPlayersInZoneType(Class<T> zoneType, ServerPacket...)` - Zone-based broadcast

- **Pattern**: Static methods, no state
- **Integration**: Uses `World.getInstance()` for player iteration

### GeoUtils.java

- **Purpose**: Debug utilities for geodata visualization
- **Location**: `gameserver/util/GeoUtils.java`
- **Key methods**:
  - `debug2DLine(Player, x, y, tx, ty, z)` - Draw 2D debug line
  - `debug3DLine(Player, x, y, z, tx, ty, tz)` - Draw 3D debug line
  - `hideDebugGrid(Player)` - Hide debug visualization
  - `computeNswe(int lastX, int lastY, int x, int y)` - Calculate movement direction

- **Use case**: Developer tools for pathfinding debugging
- **Dependencies**: `GeoEngine`, `GridLineIterator2D/3D`, `ExServerPrimitive`

### LocationUtil.java

- **Purpose**: Spatial calculations for positions and distances
- **Location**: `gameserver/util/LocationUtil.java`
- **Key methods**:
  - `calculateAngleFrom(origin, target)` - Angle between two locations
  - `calculateHeadingFrom(origin, target)` - Client-format heading
  - `calculateDistance(x1, y1, z1, x2, y2, z2, includeZ, squared)` - Distance calculation
  - `calculateDistance(ILocational, ILocational, boolean, boolean)` - Object-based distance
  - `checkIfInRange(range, obj1, obj2, includeZ)` - Range check with collision radius
  - `isInsideRangeOfObjectId(obj, targetObjId, radius)` - Object ID range check
  - `getRandomLocation(center, minRadius, maxRadius)` - Random position generator

- **Optimizations**: Compares radius² instead of sqrt for performance
- **Dependencies**: `Rnd` for random, `ILocational` interface

---

## 3. GeoEngine (Geospatial System)

### Overview

GeoEngine provides 3D geospatial data for the game world, enabling pathfinding, movement validation, and line-of-sight calculations. Uses region-based file storage (`.l2j` files).

### Key Classes

| Class | Purpose |
|-------|---------|
| `GeoEngine` | Main engine, singleton, loads geodata regions |
| `PathFinding` | A* pathfinding algorithm |
| `GeoLocation` | Pathfinding node with location |
| `GeoNode` | Pathfinding node with parent reference |
| `NodeBuffer` | Reusable pathfinding buffers |
| `Cell` | NSWE direction constants |
| `ComplexBlock` | Multi-layer geodata (height + NSWE) |
| `FlatBlock` | Simple height-only block |
| `Region` | Region container with multiple blocks |
| `NullRegion` | Empty region for ungeodata areas |
| `GridLineIterator2D/3D` | Grid traversal for LOS checks |

### GeoEngine.java

- **Purpose**: Central geodata manager
- **Singleton**: Lazy initialization via `SingletonHolder`
- **Coordinate system**:
  - World coordinates: `-655360` to `655360` (range: 1,310,720)
  - Geo coordinates: World >> 3 (divide by 8)
  - Region: 32x32 geo cells per region
  - Block: 128x128 world units = 16x16 geo cells

- **Key methods**:
  - `hasGeo(int x, int y)` - Check if geodata exists
  - `getNearestZ(int x, int y, int z)` - Get terrain height
  - `getNextLowerZ(int x, int y, int z)` - Get floor below
  - `getNextHigherZ(int x, int y, int z)` - Get floor above
  - `canMoveToTarget(from, to)` - Validate movement
  - `canSeeTarget(creature, target)` - Line-of-sight check
  - `getValidLocation(x, y, z, nswe)` - Find valid spawn location

- **File format**: `%d_%d.l2j` (region_x_region_y)
- **Loading**: Memory-mapped files via `RandomAccessFile` and `FileChannel.map()`

### PathFinding.java

- **Purpose**: A* pathfinding algorithm
- **Configuration**: `GeoEngineConfig.PATHFIND_BUFFERS` (format: "size;count")
- **Buffer management**: Thread-safe buffer pool with locking
- **Algorithm**:
  1. Find closest geodata position to start/end
  2. Run A* search with heuristic
  3. Post-filter with diagonal strategy
  4. Return optimized path as `GeoLocation` list

- **Key methods**:
  - `getPath(creature, target)` - Main pathfinding entry
  - `getNodes(start, target)` - A* search
  - `constructPath(node)` - Build path from nodes

### Geodata Block Types

| Block | Structure | Use Case |
|-------|-----------|----------|
| `FlatBlock` | Single short (height) | Flat terrain |
| `ComplexBlock` | 1024 shorts (height + NSWE per cell) | Full geodata |
| `MultilayerBlock` | Multiple layers | Multi-story buildings |

### Gotchas / Refactor Candidates

- Geodata files must exist for pathfinding to work correctly
- Missing geodata may cause NPCs to teleport incorrectly
- Buffer allocation can be slow for large maps
- `canSeeTarget` has complex logic with door/fence checks

---

## 4. Cache Systems

### HtmCache.java

- **Purpose**: Caches HTML templates for NPC dialogs, BBS pages, etc.
- **Location**: `gameserver/cache/HtmCache.java`
- **Singleton**: Lazy initialization

- **Configuration** (via `GeneralConfig.HTM_CACHE`):
  - `true` - Pre-load all HTML files into memory
  - `false` - Lazy load (cache on demand)

- **Key methods**:
  - `reload()` - Re-parse all HTML files
  - `reload(File)` - Parse specific directory
  - `getHtm(Player, path)` - Get HTML content with player prefix
  - `contains(path)` - Check if HTML exists
  - `loadFile(File)` - Parse single HTML file

- **Processing**:
  - Removes HTML comments (`<!--.*?-->`)
  - Removes tabs and newlines
  - UTF-8 encoding with ASCII validation option
  - Player-specific prefix support (for localization)

- **Memory tracking**: `_loadedFiles`, `_bytesBuffLen` for stats

### RelationCache.java

- **Purpose**: Caches player relationship data (enemy/friendly lists)
- **Location**: `gameserver/cache/RelationCache.java`
- **Simple implementation**: Uses `ConcurrentHashMap` for relationship bitmasks

---

## 5. Community BBS System

### Overview

The Community BBS (Bulletin Board System) provides in-game forums, topics, and posts accessible via the community board interface.

### Key Components

#### BB Models

| Class | Purpose |
|-------|---------|
| `Forum` | Forum container with topics/children |
| `Topic` | Discussion thread with posts |
| `Post` | Individual message in a topic |
| `Mail` | Private messaging system |

#### Managers

| Class | Purpose |
|-------|---------|
| `BaseBBSManager` | Abstract base with HTML helpers |
| `ForumsBBSManager` | Forum CRUD operations |
| `TopicBBSManager` | Topic CRUD operations |
| `PostBBSManager` | Post CRUD operations |

### Forum.java

- **Purpose**: Forum container with hierarchical structure
- **Types**: ROOT(0), NORMAL(1), CLAN(2), MEMO(3), MAIL(4)
- **Permissions**: INVISIBLE(0), ALL(1), CLANMEMBERONLY(2), OWNERONLY(3)
- **State**: `_children` (subforums), `_topic` (topics), `_loaded` flag
- **Database**: Loads from `forums` table, posts from `posts`, topics from `topic`
- **Key methods**:
  - `vload()` - Lazy load forum data
  - `getChildren()` - Load child forums
  - `getTopic(id)` - Get topic by ID
  - `addTopic(Topic)` - Add topic to forum
  - `insertIntoDb()` - Save new forum

### Topic.java

- **Purpose**: Discussion thread
- **Type**: NORMAL(0), MEMO(1)
- **Fields**: ID, forum ID, name, date, owner name/ID, type, reply count
- **Immutable**: Most fields final after construction
- **Database**: Inserts into `topic` table

### Post.java

- **Purpose**: Individual message
- **CPost inner class**: Data container for post fields
- **Security**: Sanitizes HTML (removes `<script>` tags with `action`/`bypass`)
- **Methods**:
  - `getPostText()` - Returns sanitized text
  - `updateText(int)` - Updates post in database
  - `deleteMe(Topic)` - Removes post

### ForumsBBSManager.java

- **Purpose**: Manage all forums
- **Singleton**: Lazy initialization
- **State**: `_table` (Collection<Forum>), `_lastid` (next ID counter)
- **Initialization**: Loads root forums from `SELECT forum_id FROM forums WHERE forum_type = 0`
- **Key methods**:
  - `getForumByName(name)` - Lookup by name
  - `getForumByID(id)` - Lookup by ID
  - `createNewForum(name, parent, type, perm, oid)` - Create and persist

### TopicBBSManager.java

- **Purpose**: Manage all topics
- **Singleton**: Lazy initialization
- **State**: `_table` (Collection<Topic>), `_maxId` (Map<Forum, Integer>)
- **Key methods**:
  - `addTopic(Topic)` - Add to collection
  - `getTopicByID(id)` - Find by ID
  - `showTopic(Forum, index, Player)` - Render topic list HTML

### BaseBBSManager.java

- **Purpose**: Abstract base with HTML helper methods
- **Key methods**:
  - `send1001(html, player)` - Send simple HTML (max 8192 chars)
  - `send1002(player, string, string2, string3)` - Send email-style message

### Integration

- BBS commands handled by `CommunityBoardHandler`
- Uses bypass commands like `_bbshome`, `_bbstopics;read`, `_bbsposts;read`
- Database tables: `forums`, `topic`, `posts`

---

## 6. UI Components

### Overview

Server console UI built with Swing, providing real-time monitoring and administrative controls.

### Gui.java

- **Purpose**: Main console window
- **Location**: `gameserver/ui/Gui.java`
- **Features**:
  - Console output redirection (stdout/stderr → text area)
  - Menu bar: File, Options, Help
  - System panel overlay (top-right)
  - Splash screen on startup
  - Dark theme support

- **Key methods**:
  - `redirectSystemStreams()` - Redirect System.out/err to JTextArea
  - `updateTextArea(String)` - Append text to console
  - Menu handlers for shutdown/restart options

- **Shutdown options**: "Shutdown", "Restart", "Abort", "Cancel", "Confirm"

### LogPanel.java

- **Purpose**: Log file viewer and deleter
- **Location**: `gameserver/ui/LogPanel.java`
- **Features**:
  - File browser with directory navigation
  - Search functionality with highlights
  - File size display
  - Delete mode for log cleanup

- **UI components**: JTextArea, JComboBox, JTextField, JProgressBar, JList

### SystemPanel.java

- **Purpose**: Server status overlay panel
- **Location**: `gameserver/ui/SystemPanel.java`
- **Displays**:
  - Protocol version(s)
  - Connected player count
  - Max connected count (peak)
  - Offline trade count
  - Elapsed time
  - Java version
  - Build date

- **Updates**: Timer task every 1 second
- **Styling**: Configurable dark/light theme

### AboutFrame.java

- **Purpose**: About dialog
- **Location**: `gameserver/ui/AboutFrame.java`
- **Displays**:
  - "L2jMobius" title
  - Copyright year range
  - Protocol numbers
  - Website link (clickable)
- **Features**: Clickable URL opens browser

---

## Where to Change X

| Concern | Class / Method | Actionable Change |
|---------|----------------|-------------------|
| Add new script type | `ScriptEngine.load()` | Add exclusion rule to Scripts.xml |
| Disable script | `ScriptExecutor.executeMainMethod()` | Add `@Disabled` annotation |
| Change broadcast target | `Broadcast` class | Add new `toXxx()` method |
| Modify pathfinding | `PathFinding` class | Adjust heuristic or buffer config |
| Add geodata check | `GeoEngine` | Add new `canXxx()` method |
| Add HTML file | `HtmCache.reload()` | Place in datapack `data/html/` |
| Add new BBS feature | `TopicBBSManager` | Add handler in `parsewrite()` |
| Add console command | `Gui` | Add JMenuItem and handler |

---

## Key Files for AI Player Implementation

| System | Key Files | Purpose |
|--------|-----------|---------|
| Scripting | ScriptEngine.java, Quest.java | Quest execution, custom behavior |
| Movement | GeoEngine.java, PathFinding.java | Pathfinding, location validation |
| UI | Gui.java, LogPanel.java | Console monitoring |
| BBS | ForumsBBSManager.java, TopicBBSManager.java | Community interaction |

---

## Related Audit References

- See 17-handlers-taskmanagers-util-geo-bbs-ui.md for handler patterns
- See 24-scripts-ai-vehicles-events.md for script patterns
- See 27-ai-player-knowledge.md for AI player foundation
