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
- **25 AI characters exist** in DB at level 1, **0 online** — no AI has ever actually played.
- AIPlayerEngine **compiles** (155 files). Combat/Quest/Merchant/Social AI use **mock data**, not connected to real gameplay.

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

## 4. 🔥 B3 — LIVE LOGIN — BLOCKED (the real frontier)
B4–B10 (live NPC combat, PvP, quest, trade proof) are **ALL gated on B3** (a connected in-game player). B3 is not solved.

### What I found (empirical + source)
- Live probe (`LoginProbe`/`RawInitProbe`) connected to :2106, got the **Init** frame: **194 bytes**,
  2-byte LE **self-inclusive** size (`ReadHandler`: `dataSize = size - HEADER_SIZE`, `HEADER_SIZE=2`), payload starts at `[2]` (192 bytes).
- `LoginClient` (lines 84–135): `_encryption.setKey(_blowfishKey)` set at connect (per-session random blowfish key);
  `onConnected()` sends `Init` via `sendPacket → encrypt() → LoginEncryption.encrypt`; `_usingStaticKey=true` only for the
  **first** packet = the **Init uses the STATIC blowfish key + encXORPass**; later packets use the session key + checksum.
- STATIC blowfish key = `{0x6b,0x60,0xcb,0x5b,0x82,0xce,0x90,0xb1,0xcc,0x2b,0x6c,0x55,0x6c,0x6c,0x6c,0x6c}`.
- **Negative finding:** `blowfishDecrypt(STATIC) + reverseXORPass` did NOT give a stable Init (opcode 0x00 in one run,
  0x6d in the next; protoRev magic `0x0000c621` never found when brute-forcing XOR offsets 0–10).

### Most likely causes to investigate next (NOT yet resolved)
1. The server's **custom 1468-line `BlowfishEngine`** output may not match JDK `javax.crypto "Blowfish/ECB/NoPadding"`.
   VERIFY: port the custom engine (or confirm standard-compatible) — if incompatible, my decrypt corrupts every byte.
2. `reverseXORPass` offset/order (server's `encXORPass(data, offset=HEADER_SIZE, packetEndOffset, key)`).
3. Init may use the **session** key not static (but source says static-first).

### The rest of the handshake (from Audit/31, ready to implement once Init decodes)
- `AuthGameGuard` (0x07, static key + XOR, payload = sessionId + 4×0) → server replies `GGAuth`(0x0b).
- `RequestAuthLogin` (0x00, RSA-encrypt 128-byte block with unscrambled pubkey; user@0x5E14 / pass@0x6C16;
  session-key + checksum) → `LoginOk`(0x03) or `ServerList`(0x04).
- RSA 1024-bit, exponent F4=65537, `RSA/ECB/NoPadding`.
- Client→S opcodes: AuthGameGuard=0x07, RequestAuthLogin=0x00, RequestServerLogin=0x02, RequestServerList=0x05.

## 5. Recommended paths (choose ONE; all D-B tasks hang on it)
1. **Finish B3** (dedicated crypto pass): verify/port the custom BlowfishEngine, decode Init → LoginOk → connected player.
2. **Pivot to server-side `FakePlayer`** (in-process; the approach `PARSE_Tasks.md` originally recommended) — much faster to
   "AI players actually playing", avoids the whole external-socket protocol rabbit hole.
3. **Do unblocked work**: Stream C (replace fake `assertTrue(true)` tests — tasks 54/63) or Stream G (stub-class cleanup ~145 files).

## 6. Key file/command map
- Orient: `START_HERE.md` · Rules: `Documentation/WORKFLOW.md` · Board: `TASKS.md` · Status: `STATUS.md`
- Login spec: `Documentation/Audit/31-login-protocol-handshake.md` · SQL DB: `loginserver` (accounts) / `gameserver` (characters)
- `scripts/session_start.sh` (resume-aware) · `scripts/session_end.sh` (commit+cleanup) · `scripts/real_status.sh` (real state)
- Probe/testers: `AIPlayerEngine/.../examples/LoginProbe.java`, `.../RawInitProbe.java`
