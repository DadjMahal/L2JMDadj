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

## ✅ Verified result — B4 PROVEN 2026-08-03

`CombatProbe` (login → GS handshake → CharacterSelect → **EnterWorld(0x03)** → scan NPC_INFO →
Action(0x04)+AttackRequest(0x0A)) against the live ServerBuild, player positioned at the
Talking Island **Wolf** zone (gludio32_1725_03). Pasted evidence (`/tmp/b4_probe5.log`):

```
[CombatProbe] NPC_INFO objId=268439316 npcType=1020544 attackable=1 pos=(-83479,250275,-3596)   # Elder Keltir
[CombatProbe] NPC_INFO objId=268439318 npcType=1020120 attackable=1 pos=(-82725,250232,-3596)   # Wolf
[CombatProbe] NPC_INFO objId=268439321 npcType=1020481 attackable=1 pos=(-85893,251169,-3516)   # Bearded Keltir
[CombatProbe] NPC_INFO objId=268439789 npcType=1031032 attackable=0 pos=(-82901,253704,-3728)   # friendly NPC
[CombatProbe] TARGET acquired objId=268439316 npcType=1020544 at (-83479,250275,-3596)
[CombatProbe] sent Action(0x04) target=268439316
[CombatProbe] sent AttackRequest(0x0A) target=268439316
[CombatProbe] === COMBAT TALLY (after attack) ===
  ATTACK(0x05)=18   STATUS_UPDATE(0x0E)=32   SYSTEM_MESSAGE(0x64)=31
  STOP_MOVE(0x47)=1  VALIDATE_LOCATION(0x61)=2  NPC_INFO(0x16)=18
[CombatProbe] COMBAT PROVEN (attack|die > 0) = true
```
**DB proof (before → after), `gameserver.characters` CombatBot_01:**
```
BEFORE: level=1  exp=0  curHp=126/126
AFTER : level=2  exp=105  sp=2  curHp=145/145   online=0 (clean probe exit)
```
The AI player attacked a real Wolf/Keltir monster, the server broadcast 18 `ATTACK`(0x05) hits,
and the player **gained 105 exp and leveled 1→2** (HP 126→145). No L2JM server source changed.

## Discoveries during B4 (audit findings — important for continued work)
1. **The client MUST send `EnterWorld`(0x03) after `CharSelected`(0x15) to actually spawn.** Without
   it the GS stays in `ConnectionState.ENTERING` and never sends the world burst (zero NpcInfo).
   B3 only proved `online=1` (set by `CharacterSelect.runImpl`), NOT full world entry. EnterWorld
   payload (from `EnterWorld.java` readImpl): `[0x03][readBytes(32)][4×int][readBytes(32)][int][5×4 tracert]`
   = 105 bytes; all fields may be 0 (server builds 0.0.0.0 tracert IPs).
2. **Nio `SocketChannel` blocks forever and IGNORES `Socket.setSoTimeout`** in blocking mode — a read
   never times out, so the probe hung when the server paused. Fixed by using a classic `java.net.Socket`
   (its `InputStream` honors `setSoTimeout`; `DataInputStream.readFully` throws `SocketTimeoutException`).
3. **`PacketLogger.parseNpcInfo` is OFF-BY-ONE** vs the real `AbstractNpcInfo` layout. Real field order:
   `[0x16][objectId][displayId+1000000][isAttackable][x][y][z][heading]...`. The engine read
   `[objectId][id][x][y][z]`, so it misread `isAttackable` as `x`. The real packet gives `isAttackable`
   directly (better than the `isHostileNpc` heuristic). → PacketLogger needs this fix (Stream C/cleanup) —
   CombatProbe parses the real offsets inline.
4. **The 25 `ai_%` characters were created at (16600,17000,434) — in the void, not a real map.** There
   the player dies instantly (curHp→0) and sees no monsters. CombatBot_01 was repositioned + healed to
   the Talking Island Wolf zone. Other AI chars likely need the same relocation before PvE/quest work.
5. `combatProven` must require `ATTACK`(0x05) or `DIE`(0x06) — an idle/online player also receives
   `STATUS_UPDATE`(0x0E), so counting it caused a false "PROVEN" when the (dead) player's death packets
   arrived.

## Reproduce
`scripts/b4_combat_prove.sh` (position+heal bot → restart LS → run `CombatProbe` at the Wolf zone →
assert combatProven or exp increase).

