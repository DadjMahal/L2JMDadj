# 02 — loginserver/

Standalone auth + matchmaking gateway for client connections and authenticator for game-servers. 67 Java files, ~18k lines.

## Iteration scope

- `java/org/l2jmobius/loginserver/**`
- Related config: `config/LoginConfig.java`
- Related commons: `commons/network/**`, `commons/threads/ThreadPool`, `commons/database/DatabaseFactory`

## Entrypoint / control

- `LoginServer.main()` → singleton `LoginServer` → constructor runs:
  - `LoginConfig.load()`
  - `DatabaseFactory.init()`
  - `ThreadPool.init()`
  - `LoginController.load()`
  - `GameServerTable.getInstance()`
  - Starts `GameServerListener` thread
  - Starts NIO `ConnectionManager` for player clients (`LoginClient`) with `LoginPacketHandler`

## Class-by-class audit

### LoginServer.java

- **Purpose:** Bootstrap and lifecycle manager for login process.
- **Fields / State:** `_instance` singleton, `_gameServerListener`, `_loginStatus` (`volatile int`), logger.
- **Public API Surface:** `getInstance()`, `getStatus()`, `setStatus()`, `getGameServerListener()`, `shutdown(restart)`, `loadBanFile()`.
- **Control Flow:** `main()` assigns `_instance`; constructor initializes layers and net listeners; throws `System.exit()` on fatal listener init failures.
- **I/O:** reads `./log.cfg`, `./banned_ip.cfg`; bans delegated to `LoginController`.
- **Gotchas / Refactor Candidates:** direct `System.exit()` on init failure prevents container isolation.

### LoginController.java

- **Purpose:** Central auth/registry/session manager for login clients and gameservers.
- **Fields / State:**
  - RSA 1024-bit client keys: `ScrambledKeyPair[10]`
  - Blowfish keys for bootstrap: `byte[20][16]`
  - `_loginServerClients`: `ConcurrentHashMap<String, LoginClient>`
  - `_failedLoginAttemps`: `HashMap<String, Integer>` with explicit `synchronized` block
  - `_bannedIps`: `ConcurrentHashMap<String, Long>`
- **Public API Surface:** `load()`, `getInstance()`, `getScrambledRSAKeyPair()`, `getBlowfishKey()`, `assignSessionKeyToClient()`, `removeAuthedLoginClient()`, `getAuthedClient()`, `retriveAccountInfo()`, `tryCheckinAccount()`, `canCheckin()`, `isLoginPossible()`, IP-ban helpers, `purge()`, GS helpers, DB writebacks.
- **Control Flow:** called from `RequestAuthLogin`, `RequestServerList`, `RequestServerLogin`, `GameServerThread` player tracking.
- **I/O / DB:**
  - `SELECT login, password, IF(? > value OR value IS NULL, accessLevel, -1) AS accessLevel, lastServer FROM accounts LEFT JOIN account_data ON account_data.account_name=accounts.login AND account_data.var="ban_temp" WHERE login=?`
  - `INSERT INTO accounts (login, password, lastactive, accessLevel, lastIP) values (?, ?, ?, ?, ?)`
  - `UPDATE accounts SET lastactive=?, lastIP=? WHERE login=?`
  - `SELECT * FROM accounts_ipauth WHERE login=?`
- **Gotchas / Refactor Candidates:** typos (`retrive`, `attemps`). Blowfish key selection uses `Math.random()` while rest uses `Rnd`; failed-login map uses explicit `synchronized` around `HashMap` instead of `ConcurrentHashMap.compute`.

### GameServerTable.java

- **Purpose:** Auth registry of known/dynamically registered gameservers + server name XML loader + RSA keys for GS channel.
- **Fields / State:** `SERVER_NAMES` (`HashMap<Integer,String>`), `GAME_SERVER_TABLE` (`HashMap<Integer, GameServerInfo>`), RSA 512-bit GS keys `KeyPair[10]`.
- **Public API Surface:** `load()`, `parseDocument()`, getters, `register()`, `registerWithFirstAvailableId()`, `registerServerOnDB()`, `getServerNameById()`, `stringToHex()`, `initRSAKeys()`.
- **I/O:** `SELECT * FROM gameservers`; `INSERT INTO gameservers (hexid,server_id,host) values (?,?,?)`. XML: `data/servername.xml`.
- **Gotchas / Refactor Candidates:** `getRegisteredGameServers()` exposes internal mutable `HashMap`; RSA 512 is weak but preserved for protocol compat.

#### GameServerTable.GameServerInfo

- **Purpose:** Mutable DTO for one authenticated gameserver.
- **Fields / State:** id, hexId, port, maxPlayers, status/type/ageLimit, auth flag, thread ref, player count, external host binding rules, address list.
- **Public API Surface:** setters/getters for status/port/type/pvp/age/max/authed/addresses; `getExternalHost()`, `canLogin(LoginClient)`, `getCurrentPlayerCount()`, `setDown()`.
- **Control Flow:** updated by `GameServerAuth`, `GameServerThread`, `ServerStatus`, `RequestServerLogin`.
- **Gotchas:** `getStatus()` overrides values when LS itself is DOWN or GM-ONLY.

### GameServerListener.java

- **Purpose:** Accept GS sockets with flood protection, dispatches each to a `GameServerThread`.
- **Fields / State:** static `ConcurrentHashMap.newKeySet()` active threads.
- **Public API Surface:** `addClient(Socket)`, `removeGameServer(GameServerThread)`, `getGameServerCount()`, `getGameServers()`.
- **Control Flow:** `FloodProtectorListener.run()` accepts and calls `addClient`; thread removes itself.
- **I/O:** binds `LoginConfig.GAME_SERVER_LOGIN_HOST:LOGIN_SERVER_LOGIN_PORT`.
- **Gotchas:** active set is not bounded beyond runtime socket count.

### FloodProtectorListener.java

- **Purpose:** Base accept thread with per-IP flood enforcement.
- **Fields / State:** `ServerSocket`, `ConcurrentHashMap<String, ForeignConnection>` IP tracking map.
- **Public API Surface:** `addClient(Socket)` abstract, `removeFloodProtection(ip)`, `close()`.
- **Control Flow:** loop accepts + enforces `FLOOD_PROTECTION`, `FAST_CONNECTION_LIMIT`, `NORMAL_CONNECTION_TIME`, `FAST_CONNECTION_TIME`, `MAX_CONNECTION_PER_IP`.
- **Gotchas / Refactor Candidates:** `ForeignConnection` fields are package-public; encapsulation is free.

### GameServerThread.java

- **Purpose:** Single GS connection handler with handshake and packet loop and account tracking.
- **Fields / State:** `_accountsOnGameServer`, `_socket`, `_inputStream`, `_outputStream`, RSA keys, blowfish cipher, `_loginConnectionState`, linked `GameServerInfo`, advertised addresses, player count.
- **Public API Surface:** constructor starts thread; packet loop reads header bytes, decrypts via `NewCrypt`, dispatches to `GameServerPacketHandler.handlePacket`, `run()` packet; `sendPacket(BaseWritablePacket)` encrypts + writes; `requestCharacters(account)`; `kickPlayer(account)`; account tracking helpers; setters/getters.
- **Control Flow:** header-driven state transitions `CONNECTED` → `BF_CONNECTED` → `AUTHED`.
- **I/O:** direct socket IO, `NewCrypt`, `ScrambledKeyPair`.

### SessionKey.java

- **Purpose:** Immutable four-integer session identifier; equality sensitive to `LoginConfig.SHOW_LICENCE`.
- **Fields / State:** `_playOkID1/2`, `_loginOkID1/2`, static `CHECK_LOGIN_PAIR`.
- **Public API Surface:** `checkLoginPair()`, getters, `toString()`.
- **Gotchas:** warns not to log session identifiers.

### LoginClient.java

- **Purpose:** Per-player client protocol state machine in commons `Client` hierarchy.
- **Fields / State:** encryption fields, IP, sessionId, start time, account, accessLevel, lastServer, `SessionKey`, joinedGS, `ConnectionState`, char maps.
- **Public API Surface:** constructor, `onConnected()`, `onDisconnection()`, `sendPacket()`, close overloads by reason, char-map setters/getters.
- **Control Flow:** constructed by `ConnectionManager` factory; lifecycle in commons NIO.
- **Gotchas / Refactor Candidates:** lazy char Maps initialize to null; tolerate NPE near `getCharsOnServ()` / `getCharsWaitingDelOnServ()`.

### LoginEncryption.java

- **Purpose:** Per-connection cipher wrapper transitioning from static bootstrap Blowfish key to session Blowfish key.
- **Fields / State:** static header size 8, dynamic header 4, checksum 8, `_sessionCrypt`, `_usingStaticKey`.
- **Public API Surface:** `setKey()`, `decrypt()`, `encryptedSize()`, `encrypt()`.
- **Control Flow:** `encrypt()` uses XOR pass + static crypt then flips to checksum + session crypt.
- **Gotchas:** `decrypt()` assumes session key set; static phase should not call it.

### ScrambledKeyPair.java

- **Purpose:** RSA key holder with login-server-specific modulus scrambling for client exchange.
- **Fields / State:** `_pair`, `_scrambledModulus` cached after construction.
- **Public API Surface:** getters for scrambled modulus, public key, private key.

### LoginPacketHandler.java

- **Purpose:** Dispatch incoming player packets by opcode and `ConnectionState`.
- **Public API Surface:** `handlePacket(ReadableBuffer, LoginClient)` validates bounds, state, constructs packet via supplier.
- **Gotchas:** null suppliers return null packets for unimplemented PI opcodes.

### GameServerPacketHandler.java

- **Purpose:** Stateful dispatcher for GS→LS packets.
- **Public API Surface:** `handlePacket(byte[], GameServerThread)` opcode + state routing; invalid opcodes log and `forceClose`.
- **Supported opcodes:** 0x00 BlowFishKey, 0x01 GameServerAuth, 0x02 PlayerInGame, 0x03 PlayerLogout, 0x04 ChangeAccessLevel, 0x05 PlayerAuthRequest, 0x06 ServerStatus, 0x07 PlayerTracert, 0x08 ReplyCharacters, 0x09 RequestSendMail, 0x0A RequestTempBan, 0x0B ChangePassword.

### LoginClientPackets.java

- **Purpose:** Packet-ID → supplier + `ConnectionState` table for player packets.
- Entries: `AUTH_GAME_GUARD(0x07)`, `REQUEST_AUTH_LOGIN(0x00)`, `REQUEST_SERVER_LOGIN(0x02)`, `REQUEST_SERVER_LIST(0x05)`, `REQUEST_PI_AGREEMENT_CHECK(0x0E)`, `REQUEST_PI_AGREEMENT(0x0F)`.

### ConnectionState.java

Values: `CONNECTED`, `AUTHED_GG`, `AUTHED_LOGIN`.

### Enums

| Enum | Purpose |
|------|---------|
| `LoginFailReason` | 25+ numeric login failure codes to clients |
| `PlayFailReason` | play denial codes |
| `AccountKickedReason` | kick reasons |
| `LoginResult` | internal account checkin outcome |
| `GameServerState` | GS states (`CONNECTED`, `BF_CONNECTED`, `AUTHED`) |

### Client packets

| File | Purpose |
|------|---------|
| `AuthGameGuard` | Verifies GG session id, transitions to `AUTHED_GG`, replies `GGAuth`. |
| `RequestAuthLogin` | Reads 128/256 byte RSA block, decrypts credentials, checks account, kicks dupes, assigns session, sends `LoginOk`/`ServerList`. |
| `RequestServerList` | Validates login pair then sends `ServerList`. |
| `RequestServerLogin` | Validates session + login pair, checks LS status, delegates `isLoginPossible`, sends `PlayOk` or `PlayFail`. |

### GS packets

| File | Purpose |
|------|---------|
| `BlowFishKey` | Establishes Blowfish session for GS channel. |
| `GameServerAuth` | Reads hexId, port, hosts; registers or attaches `GameServerInfo`, replies `AuthResponse`. |
| `PlayerInGame` | Bulk account tracking onto GS thread. |
| `PlayerLogout` | Removes account tracking. |
| `ServerStatus` | Updates status flags per registered `GameServerInfo`. |
| `PlayerAuthRequest` | Auth query between GS and LS for a player. |
| `ChangeAccessLevel` | DB access-level update. |
| `PlayerTracert` | Persists tracert route. |
| `ReplyCharacters` | Stores character/deletion lists for `ServerList`. |
| `RequestTempBan` | Temporary ban from GS. |
| `ChangePassword` | Password migration from GS. |

### LS packets

| File | Purpose |
|------|---------|
| `LoginServerFail` | `0x01` numeric reason code for GS-side failures. |
| `AuthResponse` | `0x03` accepted server id. |
| `ChangePasswordResponse` | `0x04` result forwarded to GS. |
| `InitLS` | `0x00` LS→GS init packet. |
| `KickPlayer` | `0x08` instruct GS to disconnect account. |
| `PlayerAuthResponse` | `0x03` player authentication result. |

### Server packets (client responses)

| File | Purpose |
|------|---------|
| `Init` | `0x00` session ID + RSA pubkey + blowfish key + reserved payload. |
| `LoginFail` | `0x01` with `LoginFailReason`. |
| `AccountKicked` | `0x02` numeric kick reason. |
| `LoginOk` | `0x03` loginOk pair + reserved fields. |
| `ServerList` | `0x04` per-server list + char counts + deletion timers. |
| `PlayFail` | `0x06` with `PlayFailReason`. |
| `PlayOk` | `0x07` session pair. |
| `GGAuth` | `0x0b` GG success. |
| `LoginOptFail` | `0x0D` option/feature failure. |

## Config surface

| Property | Default | Notes |
|----------|---------|-------|
| `GAME_SERVER_LOGIN_HOST` | `127.0.0.1` | GS listener bind IP |
| `GAME_SERVER_LOGIN_PORT` | `9013` | GS listener port |
| `LOGIN_BIND_ADDRESS` | `0.0.0.0` | player bind IP |
| `PORT_LOGIN` | `2106` | player bind port |
| `ACCEPT_NEW_GAMESERVER` | `true` | allow first-time GS registration |
| `LOGIN_TRY_BEFORE_BAN` | `5` | failed attempts before temp-ban |
| `LOGIN_BLOCK_AFTER_BAN` | `900` | seconds |
| `LOGIN_SERVER_SCHEDULE_RESTART` | `false` | optional periodic restart |
| `LOGIN_SERVER_SCHEDULE_RESTART_TIME` | `24` | hours |
| `SHOW_LICENCE` | `true` | enables login pair check in `SessionKey.equals` |
| `AUTO_CREATE_ACCOUNTS` | `true` | insert new accounts on first login |
| `FLOOD_PROTECTION` | `true` | enables `FloodProtectorListener` enforcement |
| `FAST_CONNECTION_LIMIT` | `15` | fast-connect threshold |
| `NORMAL_CONNECTION_TIME` | `700` | ms |
| `FAST_CONNECTION_TIME` | `350` | ms |
| `MAX_CONNECTION_PER_IP` | `50` | max connections per remote IP |

## Data model

- `AccountInfo` — immutable snapshot: normalized lowercase login, encoded password hash, access level, last server.
- `SessionKey` — four 32-bit ints; equality is policy-driven.
- `GameServerInfo` — mutable game-server registration with status/players/address binding.

## Networking summary

- Player flow: `NIO AsynchronousServerSocketChannel` → `Connection<LoginClient>` → `LoginClient` → `ReadHandler` reads packet → `LoginPacketHandler` → `LoginClientPacket.run()`.
- GS flow: blocking `ServerSocket` in `FloodProtectorListener` → `GameServerThread` with per-thread RSA/Blowfish ciphering and a synchronous packet-read-decrypt-run loop.

## Database summary

Tables touched by login package:
- `accounts`: login, password, lastactive, accessLevel, lastIP, lastServer, pcIp, hop1..4
- `account_data`: `ban_temp` lookup for account access coercion
- `gameservers`: hexId, server_id, host
- `accounts_ipauth`: ip + type allow/deny

## Where to change X

| Goal | Where |
|------|-------|
| Change login ports / GS listen host | `config/LoginConfig.java` / `config/Server.ini` |
| Change RSA key size or count | `LoginController` constructor / `GameServerTable.initRSAKeys()` |
| Add new client packet | new `LoginClientPackets` entry + `LoginClientPacket` subclass |
| Change game-server auth rules | `GameServerAuth.handleRegProcess()` |
| Change account lock/ban behavior | `LoginController.recordFailedLoginAttemp`, `tryCheckinAccount()`, `canCheckin()` |
| Add new GS opcode | `GameServerPacketHandler.handlePacket` + new `BaseReadablePacket` class |
| Change session key policy | `SessionKey` + `LoginConfig.SHOW_LICENCE` |
| Change IP ban format/loader | `LoginServer.processBanLine` + `LoginController._bannedIps` |

## Refactor notes

- Wrap `GameServerTable.getRegisteredGameServers()` with an unmodifiable view.
- Encapsulate `ForeignConnection` fields in `FloodProtectorListener`.
- Stabilize naming (`retrive`, `attemps`) if renaming paths is allowed in future cleanup.
