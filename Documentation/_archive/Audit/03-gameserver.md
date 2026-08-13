# 03 — gameserver/

Iteration 03 inspected files:
- /home/volodro/L2JM/SourceCode/java/org/l2jmobius/gameserver/GameServer.java
- /home/volodro/L2JM/SourceCode/java/org/l2jmobius/gameserver/LoginServerThread.java
- /home/volodro/L2JM/SourceCode/java/org/l2jmobius/gameserver/Shutdown.java
- /home/volodro/L2JM/SourceCode/java/org/l2jmobius/gameserver/config/ConfigLoader.java
- /home/volodro/L2JM/SourceCode/java/org/l2jmobius/gameserver/config/GeneralConfig.java
- /home/volodro/L2JM/SourceCode/java/org/l2jmobius/gameserver/config/ServerConfig.java
- /home/volodro/L2JM/SourceCode/java/org/l2jmobius/gameserver/config/DevelopmentConfig.java
- /home/volodro/L2JM/SourceCode/java/org/l2jmobius/gameserver/config/FeatureConfig.java
- /home/volodro/L2JM/SourceCode/java/org/l2jmobius/gameserver/config/FloodProtectorConfig.java

---

## GameServer.java

### Purpose
Bootstrap class for the game server process. Builds the runtime environment, initializes data/caches/services, opens player connections, and launches the login-server bridge thread.

### Fields / State
- `START_TIME`: static startup timestamp marker.
- `_sectionStartTime`, `_previousSectionName`: transient timing state for optional section load logging.
- No instance state beyond startup timing; object is not long-lived after `new GameServer()`.

### Public API Surface
- `GameServer()` constructor performs virtually all startup.
- `getUsedMemoryMB()`: heap runtime metric.
- `getStartTime()`: exposes static bootstrap timestamp.
- `main(String[])`: entry point.

### Control Flow
`main()` -> `new GameServer()` -> ordered initialization:
1. `InterfaceConfig.load()` and optional GUI.
2. Log folder creation and `log.cfg` reading via `java.util.logging.LogManager`.
3. `ConfigLoader.init()` ->N config classes load.
4. `DatabaseFactory.init()`, `ThreadPool.init()`.
5. Time/Id manager + scripting engine + world/instance data.
6. Data caches: category/exp, skills, items, characters, clans, geo, NPCs, olympiad, seven signs, HTM/teleporter/cache.
7. Optional systems gated by config flags: premium, offline trade/play, sell buffs, multilingual, mail, wedding, fishing championship, daily reset, anti-feed.
8. Event dispatch `OnServerStart`.
9. `Runtime.addShutdownHook(Shutdown.getInstance())`.
10. Restore offline traders/players if enabled.
11. Optional restart/precautionary/deadlock watchers.
12. `System.gc()` + memory throughput logging.
13. `new ConnectionManager<>(ServerConfig.PORT_GAME, GameClient::new, new GamePacketHandler())` opens player listener.
14. `LoginServerThread.getInstance().start()` connects to login.

### I/O
- Files: `./log.cfg`, usually `./config/*.ini`, `.//config/chatfilter.txt`, `./config/hexid.txt`.
- Network: listens on `ServerConfig.PORT_GAME`; outbound login connection launched.
- DB via singleton table/managers during bootstrap.

### Gotchas / Refactor Candidates
- Constructor is an enormous sequential init; failure of any single sub-init aborts the whole process with limited local recovery.
- Order matters implicitly because classes use lazy singleton initialization; not enforced.
- Unconditional `System.gc()` is expensive and not reproducible on pause times.
- Multiple external HTTP/network calls during `ServerConfig.autoIpConfig()` can delay startup or fail depending on environment.
- GUI mixed with headless server startup via `InterfaceConfig`. Prefer explicit launch mode separation.

---

## LoginServerThread.java

### Purpose
Persistent client thread representing this game server connection to login server. Handles key exchange, registration, player auth flow, status sync, character replies, logout, password changes, and reconnection.

### Fields / State
- Network state fields for socket, streams, Blowfish, RSA, hexID, requestID, acceptAlternate, gamePort, reserveHost, maxPlayer, subnets, hosts.
- `_status`, `_serverName`: runtime registration/status values.
- `_accountsInGameServer`: `ConcurrentHashMap<String, GameClient>` tracking logged-in accounts on this GS.
- `_waitingClients`: `List<WaitingClient>` awaiting login auth responses; synchronized on mutation.
- Inner `SingletonHolder` pattern for singleton.

### Public API Surface
- `getInstance()`: singleton access.
- `start()`: thread start method, runs main loop.
- `addWaitingClientAndSendRequest(account, client, key)`
- `removeWaitingClient(client)`
- `sendLogout(account)`
- `addGameServerLogin(account, client)`
- `sendServerStatus(type, status) / sendServerType()`
- `sendChangePassword(...)`
- `getServerStatus() / getStatusString()`
- `getServerName()`
- `getClient(name)`
- `setServerStatus(status)`

### Control Flow
1. `start()` opens socket to login host/port; if connection is lost, loops with `RECONNECT_DELAY`.
2. Handshake: `InitLS` -> revision check -> load RSA pubkey -> send `BlowFishKey` packet -> switch cipher -> send `AuthRequest`.
3. Login server failure path: `LoginServerFail`.
4. Success path: `AuthResponse` -> saves hexid -> sends `ServerStatus` + online players.
5. Main packet loop dispatches opcodes:
   - `0x03` PlayerAuthResponse -> match waiting client, restore session if mismatch, store in `_accountsInGameServer` or kick.
   - `0x04` KickPlayer -> `doKickPlayer`.
   - `0x05` RequestCharacters -> `getCharsOnServer`.
   - `0x06` ChangePasswordResponse -> logged.
   - `0x00/0x01/0x02` BlowFishKey/Register status flows handled in setup phase.
6. On disconnect/exception: cleanup socket; on interrupt break; then sleep/retry.

### I/O
- TCP socket to login server; RSA + Blowfish for proto security.
- DB write on `saveHexid(serverID, hexToString(_hexID))`.
- Writes through `sendPacket(...)` wrappers to LS.

### Gotchas / Refactor Candidates
- `_waitingClients` is synchronized as a whole block/list; could be backed by concurrent structure.
- Registration phase and runtime loop interleave in one thread; high coupling.
- `sendPacket` implementation pulled from earlier reads appears to intersperse packet writes with lock management; need visibility in `sendPacket`.

---

## Shutdown.java

### Purpose
Registered JVM shutdown hook. Saves all persistent state cleanly before process exit.

### Fields / State
- Singleton via `SingletonHolder`.
- Internal counters: `_shutdownCount`, `_restartCount`.

### Public API Surface
- `getInstance()`: singleton access.
- `startShutdown(String msg, int seconds, boolean restart)`
- `startShutdown(String msg, int seconds)`
- `restart(String msg, int seconds)`
- `restart()`
- `countdown()`
- timed count + broadcast helper methods and accessors.

### Control Flow
- `startShutdown`/`restart` checks if already active, else increments counters, schedules countdown task, broadcasts messages by stage.
- `countdown()` closes all player connections, broadcasts `ServerClose` based on config-intercept GM restriction, closes craft/item-repair shops, then sequentially asks managers to persist data:
  - `ClanHallSiegeManager`, `ItemManager`, `TopManager`, `Olympiad`, `Hero`, `SevenSigns`, `RaidBosses`, `GrandBosses`, `PartyMatchWaitingList`, `DimensionalRift`.
  - BBS, clan data, punishment, quest events, manor, CHSiege, global vars, fishing championship, scheme buffer, dropped items.
- Sleeps fixed 5000 ms before process exit path continues.
- `disconnectAllCharacters()` iterates `World.players` and stores/deletes character sessions.

### I/O
- DB saves via many singleton managers.
- Network: broadcasts `ServerClose`, `SystemMessage`, `CriticalNextUpdate` to online players.
- File: drops/ground save if enabled.
- Thread pool for scheduled shutdown countdown.

### Gotchas / Refactor Candidates
- Single static shutdown orchestrator knows many domain managers; violates single-responsibility.
- Hardcoded `Thread.sleep(5000)`; not configurable.
- If a single save fails or hangs, the shutdown chain stalls.

---

## ConfigLoader.java

### Purpose
Central bootstrap for every server configuration object. Single pass ordering for startup.

### Public API Surface
- `init()` static method calling all `load()` methods in sequence.

### Control Flow / I/O
- Loads 15 built-in config classes, then 32 custom config classes; each reads its own config file, usually `./config/<Name>.ini` / `.ini` + some TXT/XML extras in subclasses.

### Gotchas / Refactor Candidates
- Order-dependent; configs that depend on each other need current sequence.
- 47 manual loader invocations in one place: if one fails, startup halts.

---

## GeneralConfig.java

### Purpose
Loads server-wide gameplay toggles, timers, community rules, item persistence, wilderness/minigames from `General.ini`.

### Fields / State
- Large set of public static mutable flags: GM behavior, logging/audit, item drop rules, community board/manor/fishing/auction/rift, jail/punish defaults, custom load flags, multisell/ enchant limits.

### Public API Surface
- `load()` static method reading many config keys with defaults.

### Control Flow
- Static assignments from `ConfigReader`; side effects include enum parsing for chat channels and drop-disposition.

### Gotchas / Refactor Candidates
- Public static mutable globals; any class can mutate state without compile-time contract.
- Very large file with mixed semantic concerns.

---

## ServerConfig.java

### Purpose
Loads core server networking/identification configs from `Server.ini`, reads optional `ipconfig.xml`, `chatfilter.txt`, `hexid.txt`.

### Fields / State
- Static public mutable fields: hostname/ports, encryption, name templates, player limits, restart schedule, precautions, runtime paths.
- Nested static `IPConfigData` class for auto/manual network config.

### Public API Surface
- `load()`: reads Server.ini + chatfilter + hexid.
- `getServerTypeId(String[])`: bitmask mapping for server types.
- `saveHexid(...)`: writes/generates hexid.txt if missing.

### Control Flow
- IP config class first checks `ipconfig.xml`; otherwise auto-detects external IP and subnet masks from up network interfaces; logs and stores results.

### I/O
- Reads `Server.ini`, `ipconfig.xml`, `chatfilter.txt`, `hexid.txt`; writes `hexid.txt` when registered.
- External HTTP call to AWS check-IP endpoint during auto-ip.

### Gotchas / Refactor Candidates
- Static mutable fields again.
- External HTTP dependency during config load makes startup offline/infra dependent.

---

## DevelopmentConfig.java

### Purpose
Loads debug/dev mode flags from `Development.ini`.

### Fields / State
- Public static mutable booleans/Set for packet debug and excluded packet names.

### Public API Surface
- `load()`: parses debug flags.

---

## FeatureConfig.java

### Purpose
Loads feature-related numeric/bonus values from `Feature.ini` such as clan hall fees, experience/HP/MP regen rates, siege defenses, seven signs/fort/castle point tables and skill costs.

### Fields / State
- Huge public static numeric configuration; many multipliers/ratios/points.

### Gotchas / Refactor Candidates
- Extremely long file acting as a flat map of tuning constants. Suitable for typed config value objects.

---

## FloodProtectorConfig.java

### Purpose
Loads per-action flood protector plugins from `FloodProtector.ini` using `FloodProtectorSettings` beans.

### Public API Surface
- `load()` initializes all configured settings through private helper.

### Control Flow
- Iterates 16 actions; each reads interval, log flag, punishment limit/type/time.

---

## Conclusion — gameserver package top level

### Architecture summary
Top-level `gameserver` package holds the process lifecycle classes. Actual game systems are implemented in `gameserver.data`, `gameserver.managers`, `gameserver.model`, `gameserver.network`, `gameserver.taskmanagers`, `gameserver.scripting`, `gameserver.ui`, etc.; these are deferred in this iteration.

### Bootstrap map
- `GameServer.main()` is the process entrypoint and uses a constructor-heavy initialization chain.
- `ConfigLoader.init()` is the config aggregation point.
- `LoginServerThread` is the persistent client bridge to login auth.
- `Shutdown` is JVM hook and final saver.

### Dependency table

| Class | Depends on | Coupling | Notes |
|-------|-----------|----------|-------|
| GameServer | almost every `gameserver.*` singleton | very high | bootstraps everything in strict order |
| LoginServerThread | `commons.network`, login packets, `DatabaseFactory`, `World`, `GameClient` | high | persistent login link thread |
| Shutdown | many `*Manager` singletons, `World`, network packets, thread pool | very high | god-method cleanup |
| ConfigLoader | 47 config classes | medium | linear bootstrap |
| ServerConfig | ConfigReader, XML helper, filesystem/network | medium | external HTTP in startup |

### Where to change X

- Change startup/startup timing? `GameServer.printSection()` / `_sectionStartTime`.
- Add new startup system? call its singleton init in `GameServer()` constructor in the chosen section, also consider adding config switch under the corresponding `Config` class and gate in `GameServer`.
- Change login LS revision? `LoginServerThread` hardcoded `REVISION` field.
- Change player tracking map? `LoginServerThread._accountsInGameServer`.
- Add shutdown save? `Shutdown.countdown()` and a relevant manager’s save method.
- Add startup toggle / dev flag? `DevelopmentConfig.load()`.
- Change chat filter list install? `ServerConfig.loadChatFilter()`.
- Change server host detection? `ServerConfig.IPConfigData.autoIpConfig()`.

---

## Runtime log
