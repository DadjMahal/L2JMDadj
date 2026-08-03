# 35 — B4: live NPC combat proof (spec) — 2026-08-03

> **B4.** Extend the B3 external-socket enter-world flow so an AI player actually attacks a real
> NPC monster, with combat proven by (a) a server→client `ATTACK`(0x05) / `STATUS_UPDATE`(0x0E)
> / `DIE`(0x06) / `DELETE_OBJECT`(0x12) packet after our client `Action`(0x04) and (b) the player's
> `exp` increasing in the `gameserver` DB. No L2JM server source changes (all client-side, `AIPlayerEngine/`).

## Verified server facts (Interlude, `SourceCode/java/...`, `PacketEncryption = False`)
- **Game crypt is DISABLED** (`ServerBuild/game/config/Server.ini`: `PacketEncryption = False`). Per
  `GameClient.encrypt/decrypt`, packets are plaintext when `PACKET_ENCRYPTION && _encryption != null` fails.
  → all GS packets (in + out) are plaintext; no Blowfish/XOR on the game channel.
- Client→server opcodes (`SourceCode/.../network/ClientPackets.java`):
  - `ACTION` = 0x04 — `Action.java` readImpl: `targetObjectId:int, originX, originY, originZ, actionId:byte`
    (0 = click → `obj.onAction(player)`; on an enemy monster this selects + sets ATTACK intention = auto-attack).
  - `ATTACK_REQUEST` = 0x0A — `AttackRequest.java` readImpl: same shape (cddddc); if target already
    selected → `target.onForcedAttack(player)` (force attack); else selects first.
  - `MOVE_TO_LOCATION` = 0x01 — `MoveToLocation.java` readImpl: `targetX,Y,Z, originX,Y,Z, movementMode:int`.
- Server→client opcodes (`SourceCode/.../network/ServerPackets.java`): `ATTACK`=0x05, `DIE`=0x06,
  `STATUS_UPDATE`=0x0E, `DELETE_OBJECT`=0x12, `NPC_INFO`=0x16, `STOP_MOVE`=0x47, `VALIDATE_LOCATION`=0x61,
  `SYSTEM_MESSAGE`=0x64. `NPC_INFO`(0x16) carries `objectId + npcId + x/y/z + heading`.
- Flood protector: `Action`/`AttackRequest` call `canPerformPlayerAction()` — space requests by ~1s.

## Engine state to reuse / avoid
- `EnterWorldProbe.java` (examples) = PROVEN B3 flow: login(Phase1) → GS `ProtocolVersion(746)` →
  `KeyPacket` → `AuthLogin(0x08)` → `CharSelectInfo(0x13)` → `CharacterSelect(0x0D)` → `CharSelected(0x15)`.
  Its `sendFrame()` prepends the 2-byte self-inclusive size; `readPayload()` reads `[2-byte size][payload]`.
  **Reuse this framing** for combat packets (plaintext since crypt disabled).
- `protocol/crypt/GameCrypt.java` exists but is a no-op here (crypt disabled).
- `protocol/PacketCodec.java` is NOT used for B4 — its `encodeAttack` prepends an `attackerObjId`
  (real packets don't) and uses 2-byte-opcode framing inconsistent with the proven probe. Avoid.
- `protocol/PacketLogger.java` parses `NPC_INFO`(0x16) into `EntityInfo(objectId,npcId,x,y,z,heading,isHostile)`;
  `findNearestHostile()` returns the closest hostile NPC. Reuse for target selection.

## CombatBot_01 baseline (live DB, 2026-08-03)
charId=2, level=1, exp=0, sp=0, x=16600 y=17000 z=434, online=0. exp=0 → clean before/after proof.

## Implementation (`AIPlayerEngine/.../examples/CombatProbe.java`)
1. Reuse `EnterWorldProbe`'s login + GS handshake verbatim (same `L2JProtocol.connectAndLogin` + GS frames).
2. After `CharSelected`(0x15): read server packets ~5s, feed each `NPC_INFO`(0x16) to a `PacketLogger` to
   collect nearby NPC `objectId`s + positions. Also capture the player's in-world position from `CharSelected`.
3. Pick the nearest NPC `objectId` (try hostiles first; fall back to any NPC).
4. Send `Action`(0x04): `[0x04][targetObjId][playerX][playerY][playerZ][0x00]` → select + auto-attack.
5. Wait ~1s (flood protector), send `AttackRequest`(0x0A): `[0x0A][targetObjId][0][0][0][0x00]` → force attack.
6. Read packets ~20s, tally opcodes of interest: `ATTACK`(0x05), `STATUS_UPDATE`(0x0E), `DIE`(0x06),
   `DELETE_OBJECT`(0x12), `SYSTEM_MESSAGE`(0x64), `STOP_MOVE`(0x47). Print the tally + first hex of each.
7. Exit (closes GS → online=0). Do NOT leave the player online (cleanup).

## Verification (paste both)
- Probe stdout: combat opcode tally after `Action`/`AttackRequest` (esp. `ATTACK`/`STATUS_UPDATE`/`DIE`).
- DB: `SELECT exp,sp,online FROM characters WHERE char_name='CombatBot_01'` BEFORE and AFTER.
  - B4 PROVEN if: `exp` increased AND/OR an `ATTACK`(0x05)/`DIE`(0x06) packet was received after our attack.

## Reproduce
`scripts/b4_combat_prove.sh` — restarts LoginServer (clear "account in use"), runs `CombatProbe`,
captures before/after `exp`, asserts combat opcode tally or exp delta > 0.
