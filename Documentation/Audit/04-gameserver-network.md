# 04 — gameserver network + representative model/data/managers

Resume checkpoint
- Read files:
  - gameserver/network/GameClient.java
  - gameserver/network/GamePacketHandler.java
  - gameserver/network/ClientPackets.java
  - gameserver/network/ServerPackets.java
  - gameserver/network/Encryption.java
  - gameserver/network/ConnectionState.java
  - gameserver/network/serverpackets/ServerPacket.java
  - gameserver/network/clientpackets/ClientPacket.java
  - gameserver/network/serverpackets/SystemMessage.java
  - gameserver/model/World.java
  - gameserver/managers/GrandBossManager.java
  - gameserver/managers/IdManager.java
  - gameserver/data/MerchantPriceConfigTable.java
- Still to read:
  - Remaining network packets in clientpackets/serverpackets/loginserverpackets
  - managers/ data loaders handlers taskmanagers scripting util geo bbs ui
- Key structural findings so far:
  - Network layer reads packet id -> enum -> packet instance -> runImpl client packet.
  - packet encryption is optional game session-level Rolling-xor with dynamic offset in key[8..11].
  - GameClient maintains connection state, encryption, session, player linkage, HW info, flood protectors.
  - World is central registry + region visibility system; only one path for add/remove visible object.
  - GrandBossManager, IdManager, MerchantPriceConfigTable are classic singleton managers with DB/XML init.
- Next step:
  - Write structured mapping for these classes, then mark 03 done/04 in_progress.

---

## Network

### GameClient.java
Purpose: Represents one connected game client session on gameserver.
Fields/State: `ConnectionState`, optional `Encryption`, `_accountName`, `SessionKey`, `Player`, version/trace, flood protectors, hardware info, char slot mappings.
Public API: connect/disconnect hooks, packet send/write helpers, enable encryption, account/session accessors, character delete/restore helper, logout/send logouts, status commands, client-side tests.
Control Flow: extends `Client<Connection<GameClient>>` from commons. `onDisconnected()` sends logout to LoginServerThread. `sendPacket(clientPacket)` writes packet if attached. `markToDeleteChar()` reads clan membership and schedules delete/soft-delete. `restore()` resets delete timestamp.
I/O: direct JDBC deletes/updates for character cleanup; network send through commons write pipeline.
Gotchas/Refactor Candidates: Account cleanup DB cascade handled in client code, not centralized Data layer; character deletion logic lives partly here.

### GamePacketHandler.java
Purpose: Route raw game-client packet bytes to concrete packet objects.
Fields/State: stateless.
Public API: `handlePacket(buffer, client)`.
Control Flow: reads first byte; if `0xD0` reads ex-packet id, else normal id. validates array bounds and connection state via `PACKET_ARRAY`. Returns new packet enum instance.
I/O: none; just buffer read.
Gotchas/Refactor Candidates: Silent drop for invalid/state-mismatched packets; no debug logging even if configured.

### ClientPackets.java
Purpose: Catalog of all game-client packet ids, suppliers, and allowed states.
Fields/State: static `PACKET_ARRAY[maxPacketId+1]` built from enum values.
Public API: implicit via enum constants; build-time validation throws if packetId > 0xFF.
Control Flow: on static init, compute max packet id and populate array.
I/O: none.
Gotchas/Refactor Candidates: Sparse array mapping wastes memory; ex-packets need separate top-level entry instead of enum nesting.

### ServerPackets.java
Purpose: Registry of server -> client packet opcodes.
Fields/State: enum constants only.
Public API: implicit opcode constants.
Control Flow: none; data holder.
I/O: none.

### Encryption.java
Purpose: stateless-ish game-protocol XOR cipher; optional enablement with rolling offset in key bytes [8..11].
Fields/State: 16-byte inbound/outbound key copies; enabled flag.
Public API: `setKey`, `encrypt(data, offset, size)`, `decrypt(data, offset, size)`.
Control Flow: first encrypt call is an enablement skip; subsequent calls run rolling XOR and advance offset. decrypt mirrors with `last=enc` chaining.
I/O: none.
Gotchas/Refactor Candidates: Uses mutable shared key buffers; any concurrent packet writes would corrupt offset. Serialization path is tied to commons Buffer API.

### ConnectionState.java
Purpose: client lifecycle states in gameserver.
Values: CONNECTED | DISCONNECTED | CLOSING | AUTHENTICATED | ENTERING | IN_GAME.

### ServerPacket.java
Purpose: Abstract write side for packets sent from server to client.
Fields/State: none beyond base; checks client detached/disconnected.
Public API: `write(client, buffer)` wrapper; `runImpl(player)` hook.
Control Flow: write handler catches Exception, logs via PacketLogger, returns boolean success.

### ClientPacket.java
Purpose: Abstract read side for packets sent from client to server.
Fields/State: none beyond base.
Public API: `read()` wrapper, `run()` wrapper around `readImpl/runImpl`; helper `getPlayer()`.
Control Flow: catches exceptions and logs; special case EnterWorld failure closes socket.

### SystemMessage.java
Purpose: Parametrized server-to-client text packet with typed system-message parameters.
Fields/State: `SystemMessageId`, `SMParam[]`, index.
Public API: constructors by id/text; typed appenders addString/addInt/addLong/addPcName/addDoorName/addNpcName/addCastleId...; `append()` resizes beyond initial paramCount.
I/O: none.
Gotchas/Refactor Candidates: Mutable param count can hide bug mismatches versus database-driven SystemMessageId definitions.

## Representative Model/Data/Managers

### World.java
Purpose: Top-level spatial/object registry for the game world.
Fields/State: `_allObjects`, `_allPlayers`, `_allGoodPlayers`, `_allEvilPlayers`, `_petsInstance`, grid and region dimensions, visibility maps by `WorldRegion.` central observers by region.
Public API: object/region/pet/npc lookups; add/remove visible object; world region init by grid; foreach visible helpers.
Control Flow: observers register in 3x3x3 neighboring region arrays on region init. `addVisibleObject()` iterates visible observers and describes creature AI; sends info between pairs. `removeVisibleObject()` removes old region; tells AI forget. Faction partitioned player maps.
I/O: none.
Gotchas/Refactor Candidates: comments warn that `addVisibleObject` DOES NOT add to global `_allObjects`; `removeVisibleObject` DOES NOT remove from global `_allObjects`; dual add/remove methods creates footgun.

### GrandBossManager.java
Purpose: persistence + runtime state tracker for grand boss instances.
Fields/State: static SQL strings, `BOSSES` map, `_storedInfo`, `_bossStatus`, `_zones`. Singleton via holder.
Public API: init/zones, add/get/set status, start/end spawn timers, DB saves, set/check zone check helpers.
Control Flow: `init()` loads `grandboss_data` once; schedules periodic `storeMe` every 5 minutes. `initZones()` loads `grandboss_list` for zone ACL then links allowed players.
I/O: JDBC for load/store.
Gotchas/Refactor Candidates: `updateDb(bossId,true)` forced commit on status change; map iteration for `getZone(obj)` in tight loops; zone lists not rebalanced on joins/leaves.

### IdManager.java
Purpose: allocate/release unique object IDs within configured range.
Fields/State: `BitSet _freeIds`, counters, `Lock`, static singleton holder.
Public API: `getNextId()`, `releaseId()`, `getAvailableIdCount()`.
Control Flow: constructor cleans timestamps/statuses from DB, seeds bit set from used IDs. On allocation marks used; when utilization >= threshold, auto grow via next-prime capacity; then searches from current/0.
I/O: indirect DB reads in constructor.
Gotchas/Refactor Candidates: Allocation logic is complex and modifies bitmap during search; release without validation of object lifetimes.

### MerchantPriceConfigTable.java
Purpose: merchant tax/price config by castle/zone.
Fields/State: static singleton, `_mpcs` map, default price config refs.
Public API: `loadXML`, `loadInstances`, `updateReferences`, lookup API.
Control Flow: parses XML from datapack; default price config required; references then linked to castles/zones.
I/O: XML parse from `ServerConfig.DATAPACK_ROOT`.

---

## Conclusion — gameserver package

### Architecture summary
Top-level gameserver package owns process bootstrap, login bridge thread, shutdown hook, and top-level config loading. Game systems are implemented in subpackages including `network`, `model`, `managers`, `data`, `taskmanagers`, `scripting`, `cache`, `communitybbs`, `geoengine`, `util`, `ui`.

### Dependency map

| Layer | Depends on | Coupling | Notes |
|-------|-----------|----------|-------|
| network | commons/network + model/managers | high | entry point for all player interactions |
| model | many subpackages | very high | central actor/world state |
| managers | model/data/db | high | behavior/state engines for game features |
| data | xml/sql/cache | medium | statically scoped data loaders |

### Where to change X

- Connection flow? `GameClient` states and `GamePacketHandler` dispatch.
- Packet opcode? `ClientPackets` enum + corresponding packet class.
- Encryption? `Encryption.setKey/encrypt/decrypt`; toggle via `ServerConfig.PACKET_ENCRYPTION`.
- Region/object visibility? `World.addVisibleObject/removeVisibleObject` and `WorldRegion` helpers.
- Boss/zone feature? `GrandBossManager.addZone/status/DB API`.
- Unique IDs? `IdManager.getNextId`/`releaseId` and init route.
- Merchant taxes? `MerchantPriceConfigTable` and XML configs.

---

## Runtime log

## Extended representative packets/data/managers

### AuthLogin.java
Purpose: Handle game-client login session handshake from client.
Fields/State: loginName, session ints.
Public API: readImpl reads lowercase account and keys; runImpl rejects empty account or invalid protocol, then registers account in LoginServerThread and sends PlayerAuthRequest with session key.
I/O: network via LoginServerThread.
Gotchas/Refactor Candidates: Tight coupling to LoginServerThread singleton; commented out client.setSessionId suggests inconsistency between client and LS session ownership.

### CharacterSelect.java
Purpose: Execute character selection and enter world.
Fields/State: slot + unknown C4 fields.
Public API: readImpl slot; runImpl does dualbox/faction checks, loads player from disk via client.load, sets client/player, restores saved location, fires OnPlayerSelect event, transitions to ENTERING, sends CharSelected.
I/O: DB via client.load/CharInfoTable; DB-backed access checks.
Gotchas/Refactor Candidates: Large orchestration in one packet class - rules stack in single runImpl.

### AuthRequest.java (LS->GS)
Purpose: Serialize game-server registration to login server.
Fields/State: id, acceptAlternate, reserveHost, port, maxPlayers, hexId, subnets/hosts.
Public API: constructor normalizes nulls; write emits opcode 0x01 and all fields.
I/O: none.

### PlayerAuthRequest.java (LS->GS)
Purpose: Send player session keys for login verification.
Fields/State: account, SessionKey.
Public API: constructor defaults account to empty; write sends opcode, account, play/login ints.

### CharInfoTable.java
Purpose: Cache character id->name and accesslevel mappings with DB fallback.
Fields/State: concurrent maps of names/accesslevels.
Public API: addName/removeName/getIdByName/getNameById/getAccessLevelById/doesCharNameExist/account character counts.
Control Flow: loads all characters at init; on miss, queries DB and caches result.
I/O: direct JDBC selects on characters table.
Gotchas/Refactor Candidates: getIdByName linear scan falls back to DB then scans transient results by while rs.next then returns last row instead of first match? look later.

### CastleManager.java
Purpose: Lookup/manipulate castles by owner/region/name ids.
Fields/State: CopyOnWriteArrayList of Castle; siege date map; static circle items array.
Public API: findNearest, getCastle variants, indexes, siege dae validation.
I/O: none in shown portion; DB elsewhere.
Gotchas/Refactor Candidates: lookup by linear scan instead of indexed maps.

---
