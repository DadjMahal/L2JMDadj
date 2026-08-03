# SESSION HANDOFF — Full project state & knowledge (2026-08-03)

> **Read this to reach near-full context cheaply** (~2-3k tokens). Live source: `START_HERE.md` (orient) +
> this file (depth) + `TASKS.md` (board). This avoids re-deriving the ~70k tokens of history.
> Save-update note: this doc is rewritten at the end of each significant session.

## 1. Project (one line)
L2JMobius **Interlude** server (`/home/volodro/L2JM`) + external-socket **AI Player Engine**
(`AIPlayerEngine/`, separate Maven project) that connects as real client sockets — **no server code mods**.

## 2. Honest current state (verified, not claimed)
- Server **UP** since Jul 31: LoginServer :2106, GameServer :7777.
- Source-code audit complete (iterations 1–30, `Documentation/Audit/`).
- Docs restructured: `START_HERE.md` = single entry; `Documentation/WORKFLOW.md` = single rules doc;
  `TASKS.md` = 103-task board (collision-fixed); fabricated docs quarantined in `Documentation/_archive_*`.
- **25 AI characters exist** in DB; **live PvE combat PROVEN (B4)** — `CombatBot_01` fought a Talking Island
  Wolf/Elder Keltir (18 server `ATTACK` packets) and **leveled 1→2 / gained 105 exp**. 0 AI online at rest.
- AIPlayerEngine **compiles** (155 files). The external socket path is proven end-to-end
  (login → enter-world → `EnterWorld`(0x03) → real NPC combat). The Combat/Quest/Merchant/Social **decision**
  classes still use **mock data** internally and need wiring to the real packets now proven by `CombatProbe`
  (`Documentation/Audit/35-b4-live-npc-combat.md`).

## 3. Work completed (committed)
- **Stream A (done):** A1 cold-start test (`scripts/cold_start_test.sh`, 17/17 PASS; bootup 73k→~1.3k tokens);
  A2 fixed `real_status.sh` double-print; A3 fixed `count_ai_players.sh` (sudo mysql; real: 25 registered/0 online).
- **B1 (done):** AI account credentials valid — DB (`loginserver` DB) passwords were PLAINTEXT; set to `Base64(SHA1("ai123pass"))`
  for all 25 `ai_%` accounts; fixed `AIPlayerManager.connectPlayer` double-prefix bug (`ai_ai_` → `ai_`). Build OK.
- **B2 (done, compiles, NOT live-verified):** real L2J login handshake implemented:
  - `protocol/LoginCrypt.java` (NEW): `unscrambleModulus`, `buildPublicKey` (RSA 1024/F4=65537), `rsaEncrypt` (RSA/ECB/NoPadding),
    `blowfishEncrypt/Decrypt`, `appendChecksum/verifyChecksum`, `encXORPass`, `reverseXORPass`, `buildAuthBlock`.
  - `protocol/L2JProtocol.java` (REWRITTEN): parse Init → unscramble RSA → AuthGameGuard(0x07) → RequestAuthLogin(0x00) → LoginOk(0x03)/ServerList(0x04).
  - Spec doc: `Documentation/Audit/31-login-protocol-handshake.md`.

## 4. ✅ B3 — LIVE LOGIN — DONE 2026-08-03 (1 AI player online=1)
B4–B10 (live NPC combat, PvP, quest, trade proof) are now UNGATED. Full external socket flow (no server source changes):
- **Phase 0:** little-endian Blowfish ≠ JDK → ported `protocol/crypt/BlowfishEngine.java` (`Audit/32`).
- **Phase 1:** login server auth to PlayOk; client packets = session key + checksum + self-inclusive size (`Audit/33`).
- **Phase 2:** GS ProtocolVersion(746) → KeyPacket (packetEncryption=0 → plaintext) → AuthLogin → CharSelectInfo → CharacterSelect → CharSelected → online=1 (`Audit/34`, `scripts/b3_enter_world_prove.sh`).

## 4b. ✅ B4 — LIVE NPC COMBAT — DONE 2026-08-03 (attacked a real monster)
`CombatProbe` (examples) = login → GS handshake → CharacterSelect → **EnterWorld(0x03)** → scan `NPC_INFO`(0x16)
→ **Action**(0x04)+**AttackRequest**(0x0A) → comb tally. Live proof (`Audit/35`, `scripts/b4_combat_prove.sh`):
- Target = a real Talking Island Elder Keltir/Wolf (`npcType` = id+1000000, `attackable=1`).
- Server broadcast **18 `ATTACK`(0x05)** + 32 `STATUS_UPDATE`(0x0E) + 31 `SYSTEM_MESSAGE`(0x64).
- DB: CombatBot_01 **level 1/exp 0 → level 2/exp 105**, curHp 126→145. No L2JM server source changed.

### B4 discoveries (matters for B5+ / Stream C / G)
1. **Client MUST send `EnterWorld`(0x03) after `CharSelected`** to spawn; otherwise GS stays in
   `ConnectionState.ENTERING` and sends no world burst (B3's `online=1` is set in CharacterSelect.runImpl,
   so B3 worked without it). EnterWorld payload (105 B): `[0x03][32B][4×int][32B][int][5×4 tracert]`.
2. **NIO `SocketChannel` ignores `setSoTimeout`** and blocks forever on paused reads → the probe hung.
   Fixed with a classic `java.net.Socket` + `DataInputStream.readFully` (honors `setSoTimeout`).
3. **`PacketLogger.parseNpcInfo` is OFF-BY-ONE** vs real `AbstractNpcInfo`:
   `[0x16][objectId][displayId+1000000][isAttackable][x][y][z][heading]`. Fix in Stream C; CombatProbe parses real offsets.
4. **All 25 `ai_%` chars were created at (16600,17000,434) — in the void** (no real map): they die instantly
   (curHp=0) and see no monsters. CombatBot_01 was relocated + healed to the Talking Island Wolf zone.
   Other AI chars need the same before PvE/quest proof.
5. `combatProven` must require `ATTACK`(0x05)/`DIE`(0x06), NOT `STATUS_UPDATE`(0x0E) (idle players get those).

### Most likely causes / next — B3 & B4 crypto/enter-world fully RESOLVED (above). Remaining live gap:
B5–B10 (PvP, quest, trade proof) + wiring the proven packets into `CombatAI`/`PacketLogger` (Stream C).

### What I found (empirical + source)
- Live probe (`LoginProbe`/`RawInitProbe`) connected to :2106, got the **Init** frame: **194 bytes**,
  2-byte LE **self-inclusive** size (`ReadHandler`: `dataSize = size - HEADER_SIZE`, `HEADER_SIZE=2`), payload starts at `[2]` (192 bytes).
- `LoginClient` (lines 84–135): `_encryption.setKey(_blowfishKey)` set at connect (per-session random blowfish key);
  `onConnected()` sends `Init` via `sendPacket → encrypt() → LoginEncryption.encrypt`; `_usingStaticKey=true` only for the
  **first** packet = the **Init uses the STATIC blowfish key + encXORPass**; later packets use the session key + checksum.
- STATIC blowfish key = `{0x6b,0x60,0xcb,0x5b,0x82,0xce,0x90,0xb1,0xcc,0x2b,0x6c,0x55,0x6c,0x6c,0x6c,0x6c}`.
- **Resolved (B3):** the Init instability was the **little-endian Blowfish ≠ JDK** mismatch — ported the server's
  engine to `protocol/crypt/BlowfishEngine.java` (see `Audit/32`); `reverseXORPass` order was fixed; the first
  packet uses the STATIC key + XOR, later packets the session key + checksum.

### The rest of the handshake (from Audit/31, ready to implement once Init decodes)
- `AuthGameGuard` (0x07, static key + XOR, payload = sessionId + 4×0) → server replies `GGAuth`(0x0b).
- `RequestAuthLogin` (0x00, RSA-encrypt 128-byte block with unscrambled pubkey; user@0x5E14 / pass@0x6C16;
  session-key + checksum) → `LoginOk`(0x03) or `ServerList`(0x04).
- RSA 1024-bit, exponent F4=65537, `RSA/ECB/NoPadding`.
- Client→S opcodes: AuthGameGuard=0x07, RequestAuthLogin=0x00, RequestServerLogin=0x02, RequestServerList=0x05.

## 5. Recommended paths (next — B5+)
1. **B5 — live PvP proof** (a second bot / flag+PK rules) — extends `CombatProbe` (login → enter-world → attack).
2. **Wire the proven packets into the engine** (Stream C): fix `PacketLogger.parseNpcInfo` to the real
   `AbstractNpcInfo` layout, then route real Action/Attack/StatusUpdate into `CombatAI.makeDecision()`.
3. **Relocate + heal the remaining 24 `ai_%` chars** (still in the void at 16600,17000,434 → die on login)
   so quest/trade proofs can proceed.
4. Stream C/G cleanup — fake `assertTrue(true)` tests 54/63; ~145 unwired stub classes.

## 6. Key file/command map
- Orient: `START_HERE.md` · Rules: `Documentation/WORKFLOW.md` · Board: `TASKS.md` · Status: `STATUS.md`
- Login spec: `Documentation/Audit/31-login-protocol-handshake.md` · SQL DB: `loginserver` (accounts) / `gameserver` (characters)
- `scripts/session_start.sh` (resume-aware) · `scripts/session_end.sh` (commit+cleanup) · `scripts/real_status.sh` (real state)
- Probe/testers: `AIPlayerEngine/.../examples/LoginProbe.java`, `.../RawInitProbe.java`
