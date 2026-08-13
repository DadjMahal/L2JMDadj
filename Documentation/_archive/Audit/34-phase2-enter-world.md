# 34 — Phase 2: GameServer enter-world proven — 1 AI player ONLINE (online=1) — 2026-08-03

> **B3 DONE.** Full external-socket path verified against the running ServerBuild:
> LoginServer auth (Phase 1) → GameServer handshake → AuthLogin → CharSelectInfo → CharacterSelect
> → player loads into the world → **`characters.online = 1` in the `gameserver` DB, live-verified**.
> Zero L2JM server source changes (all client-side code in `AIPlayerEngine/`).

## GameServer handshake (reverse-engineered from gameserver source)
1. **ProtocolVersion (0x00)** — client→server, PLAINTEXT: `[00][int protocolRevision]`.
   Accepted value on this server: **746** (`AllowedProtocolRevisions = 746` in `game/config/Server.ini`).
2. **KeyPacket (0x00)** — server→client, PLAINTEXT (the server's first `encrypt()` call only enables the
   cipher): `[00][result:1][key:8][packetEncryption:int][serverId:int][1][obfuscation:int]`.
   - This server sends `packetEncryption = 0` → **the game crypt is DISABLED**; all GS packets are
     plaintext (GameClient.encrypt/decrypt no-op when `PACKET_ENCRYPTION && _encryption != null` fails).
   - If enabled, the game key = the 8 KeyPacket bytes + fixed suffix
     `C8 27 93 01 A1 6C 31 97` (BlowFishKeygen.KEY_TAIL_BYTES); engine has `protocol/crypt/GameCrypt.java`
     (stateful XOR mirror of gameserver `Encryption`).
3. **AuthLogin (0x08)** — encrypted only if game crypt enabled: `[08][login UTF-16LE+\0][playKey2][playKey1][loginKey1][loginKey2]`
   from the Phase-1 SessionKey (loginKey=loginOk, playKey=playOk). The GS validates it with the LoginServer.
4. **CharSelectInfo (0x13)** — server sends the char list (this account: 1 char, `CombatBot_01`).
5. **CharacterSelect (0x0D)** — client→server: `[0D][charSlot:int][unk1:short][unk2:int][unk3:int][unk4:int]`
   (unks = 0). Server loads the character, calls `player.setOnlineStatus(true, true)` → **DB online=1**,
   then sends **CharSelected (0x15)** + world packets.

## Verified live result (pasted 2026-08-03)
```
sudo mysql -u root gameserver -e "SELECT char_name, account_name, online FROM characters WHERE account_name LIKE 'ai_%';"
char_name     account_name  online
CombatBot_01  ai_combat_01  1
```
`real_status.sh`: **AI players currently online: 1**.

## Reproduce
`scripts/b3_enter_world_prove.sh` runs the probe, holds the GS connection after CharSelected,
checks the DB (`online=1`), and asserts.

## Notes
- `EnterWorldProbe.java` (examples) implements login + GS enter-world in one process so the LS session
  key stays valid during the GS handshake.
- After each full login the account stays "in use" on the LS (normal): the script restarts the LoginServer
  (watchdog auto-restarts in ~10s) to clear it before re-running.
- The AI is online only while the GS connection is held; closing it logs the player out (online=0).
