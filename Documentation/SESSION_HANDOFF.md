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
- **25 AI characters exist** in DB; **live PvE combat (B4), live PvP (B5), AND live quest (B6) PROVEN** —
  CombatBot_01 leveled killing a Wolf/Keltir (exp 0→105), a two-bot fight produced mutual `Attack` hits
  (objId2:13/objId3:12 + CombatBot_02 damage), and an enter-world triggered the real server quest engine
  (added Q00255_Tutorial `Ex`/`ucMemo` state to `character_quests`). 0 online at rest.
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

- **Stream C started (done slice, 2026-08-03):** `PacketLogger.parseNpcInfo` OFF-BY-ONE fixed to the real
  `[objId][displayId+1000000][isAttackable][x][y][z][heading]` layout (Audit/35); hostile now packet-derived
  (attackable) with range-heuristic fallback; fake tests TASKS 54/63 → real assertions on
  `makeDecision()`/`makePvPDuidedDecision()`; `PerceptionAccuracyTest`(42) reconciled to the real layout.
  36/36 tests pass, BUILD SUCCESS. (RuntimeLog/2026-08-03-streamC-npc-info-fix.md)

- **Stream C slice 2 (done, 2026-08-03):** real client combat encoders `PacketCodec.encodeAction(0x04)` /
- **Stream C slice 3 (done, 2026-08-03):** decision→send wiring. `CombatFramePlanner` maps a combat
- **Stream C slice 6 (done, 2026-08-03) — LIVE PROVEN:** packet feedback into the decision loop. PacketLogger
  self-tracking is objId-aware (`setSelfObjectId`; a target wolf's StatusUpdate no longer clobbers the bot's HP —
  live ignored 23 `self=false` updates); CombatAI death-gates `makeDecision()`/`isBotAlive()` (live: self HP
  145→…→0 → `[CombatLoop] DEAD` → **0 Action frames after death**, was unlimited); `handleCombatEnd()` now really
  ends combat so a DeleteObject'd target → re-acquire next (RE_TARGET); DeleteObject logs at INFO. 59/59 tests.
  New tests: objId self-HP, DeleteObject removal, death gate, re-target. (RuntimeLog/2026-08-03-streamC-packet-feedback.md)
- **Stream C slice 5 (done, 2026-08-03) — LIVE PROVEN:** `CombatLoop` (examples) + `scripts/c5_live_combat_proof.sh`.
  Full engine-driven live loop: login → GameServerClient enter-world → startReader → `CombatAI.setPacketLogger`
  (share live reader buffer — this fix made decisions real) → loop `makeDecision()` → CombatFramePlanner →
  `sendGameFrame` (Action 0x04 / AttackRequest 0x0A). Proof scored `engaged-actions=18, serverConfirmedDamage=1`
  (target wolf HP 107→103 in server StatusUpdate). 55/55 tests, BUILD SUCCESS. Added the new unit test
  `testSetPacketLoggerSharesLiveBuffer`. `SKIP_RESTART=1` option to run without restarting LoginServer.
  (RuntimeLog/2026-08-03-streamC-live-driver.md) Note: level-2 ungeared bot dies before landing kills (no exp);
  server-confirmed damage is the proof. Next: Slice 6 (packet feedback drive) / B6b (NPC quest via
  RequestBypassToServer).
- **Stream C slice 4 (done, 2026-08-03):** reusable in-engine `GameServerClient` (classic Socket) retaining
  the proven B3/B4 handshake (ProtocolVersion → KeyPacket → AuthLogin → CharSelectInfo → CharacterSelect →
  CharSelected → EnterWorld), a background reader feeding `PacketLogger`, and `attachToConnection` +
  `sendGameFrame`. Handshake payload builders added to `PacketCodec`
  (`encodeProtocolVersion`/`encodeAuthLogin`/`encodeCharacterSelect`/`encodeEnterWorld`). 54/54 tests pass,
  BUILD SUCCESS — incl. an in-process fake-GS integration test completing the handshake and sending a real
  `Action`(0x04) frame. (RuntimeLog/2026-08-03-streamC-gs-client.md) Remaining: a live driver + proof script.

  decision to ordered wire frames (ENGAGE/ATTACK → Action 0x04, 1000 ms flood gap, AttackRequest 0x0A;
  FLEE/RETREAT/BLOCK → MoveToLocation 0x01); `GameServerFrameWriter` emits framed packets; added
  `PacketCodec.encodeMoveToLocation` (B8-proven layout); `AIPlayerConnection.executeCombatDecision()`
  + `setGameServerWriter()`. 49/49 tests pass, BUILD SUCCESS.
  (RuntimeLog/2026-08-03-streamC-decision-to-send.md) Remaining: attach a persistent in-engine GS socket
  and a live decision→send proof.

  `encodeAttackRequest(0x0A)` in the B4-proven self-inclusive-size framing; `CombatAI.calculateDistanceTo`
  now real 3D distance from PacketLogger coords and `shouldDefend()` deterministic from real HP+hostile
  count (removed the `Math.random()` mocks); added `getPacketLogger()` + `getSelectedTargetObjId()`.
  41/41 tests pass, BUILD SUCCESS. (RuntimeLog/2026-08-03-streamC-combat-decisions.md)


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

## 4c. ✅ B5 — LIVE PVP — DONE 2026-08-03 (two bots fought each other)
`PvPProbe` (examples) = login BOTH accounts (`ai_combat_01` objId2, `ai_combat_02` objId3) → enter-world both
→ each sends `Action`(0x04)+`AttackRequest`(0x0A) on the other → two reader threads tally `Attack`(0x05) by
attacker objectId. Live proof (`Audit/36`, `scripts/b5_pvp_prove.sh`):
- Attack traffic broadcast to both connections: **attacker objId 2 → 13 hits / objId 3 → 12 hits**.
- DB damage: **`CombatBot_02` curHp 126 → 120** (real PvP damage from CombatBot_01).
- No L2JM server source changed; both bots logged out cleanly (online=0).
- PvP path: `AttackRequest` → `Creature.onForcedAttack` → flag + attack (blocked only in peace zones /
  non-attackable / confused). No newbie-protection gate in this config.

## 4d. ✅ B6 — LIVE QUEST — DONE 2026-08-03 (server processed the bot's tutorial on enter-world)
`QuestProbe` (examples) = enter-world → `EnterWorld.loadTutorial` → `Q00255_Tutorial` `notifyEvent("UC")` ran
the quest's live event handler and **wrote new quest state to `character_quests`**; the bot also exercised the
two-way quest protocol `RequestQuestList`(0x63) → `QuestList`(0x80). Live proof (`Audit/37`, `scripts/b6_quest_prove.sh`):
- DB delta for charId 2 (Q00255_Tutorial): **before=1 row → after=3 rows** (server added `Ex=-2`, `ucMemo=0`).
- The Tutorial is excluded from the visible QuestList by its `Ex` flag (so questCount=0 by design); the DB
  delta is the primary proof.
- `loadTutorial` only *advances* an existing tutorial state; the `<state>=Started` fixture mirrors char-creation opt-in.

## 4e. ✅ B8 — LIVE MOVEMENT — DONE 2026-08-03 (AI walked its real character to a destination)
`MoveProbe` (examples) = login (proven flow) → GS handshake → CharacterSelect → **EnterWorld** → wait for the
world burst → **`MoveToLocation`(0x01)** to Trader 30040's spawn (-82515,241221,-3728) → tally movement
packets. Live proof (`Audit/39`):
- Server broadcast **17× `CHAR_MOVE_TO_LOCATION`(0x01)** + **1× `VALIDATE_LOCATION`(0x61)** after our walk.
- DB (`gameserver.characters`, CombatBot_01): **(-83789,240799,-3717) → (-82515,241221,-3728)** = exactly the
  requested destination (persisted on logout). No L2JM server source changed.
- `MoveToLocation`(0x01) client layout: `[targetX,targetY,targetZ,originX,originY,originZ,moveType:int]`
  (moveType 0 = cursor-key walk, 1 = mouse). Server auto-paths the character to the target.

## 4f. ✅ B9 — LIVE CHAT — DONE 2026-08-03 (server delivered the AI's whisper to another bot)
`ChatProbe` (examples) = two-bot enter-world (proven flow) → bot A sends **`Say2`(0x38)** whisper →
token delivered to bot B as **`CREATURE_SAY`(0x4A)**. Live proof (`Audit/40`, `scripts/b9_chat_prove.sh`):
- Client `Say2`(0x38): `[0x38][text:UTF-16LE null-term][type:int][target:UTF-16LE null-term (whisper)]`;
  WHISPER client id = 2; `readString()` = null-terminated UTF-16LE.
- Server `CREATURE_SAY`(0x4A): `[0x4A][senderObjId][chatType][senderName][text]`.
- Whisper (`ChatWhisper`) has NO level gate/range (GENERAL chat is gated to `MinimumChatLevel`=20 → not usable
  for L1-2 bots). B's connection got the token (`received=true`), A got its `->CombatBot_02` echo (`echo=true`).
- No L2JM server source changed.

## 4g. ✅ B10 — LIVE PARTY — DONE 2026-08-03 (two AI bots formed a real party)
`PartyProbe` (examples) = two-bot enter-world (proven flow, co-located) → A sends `RequestJoinParty`(0x29) →
server asks B `AskJoinParty`(0x39) → B accepts `RequestAnswerJoinParty`(0x2A) → server `Party.addPartyMember`
pushes party windows. Live proof (`Audit/41`, `scripts/b10_party_prove.sh`):
- Client `RequestJoinParty`(0x29): `[0x29][targetName:UTF-16LE null-term][partyDistributionTypeId:int]`
  (invite by NAME; dist RANDOM=1).
- Client `RequestAnswerJoinParty`(0x2A): `[0x2A][response:int]` (1 = accept).
- On accept, `Party.addPartyMember` sends `PARTY_SMALL_WINDOW_ALL`(0x4E) to the **joiner** (B, len 83) and
  `PARTY_SMALL_WINDOW_ADD`(0x4F) to the **existing members** (leader A, len 79) — only after a real `Party`
  exists. A also got `JOIN_PARTY`(0x3A). Invite requires `target.isVisibleFor(requestor)` (bots co-located).
- No L2JM server source changed. (First build watched the wrong connections → false; fixed + verified.)

### Most likely causes / next — **ALL B-stream live proofs DONE**: B3 login, B4 PvE, B5 PvP, B6 quest,
B7 trade, B8 movement, B9 chat, B10 party — every one PROVEN with live wire/DB evidence and no server source
changes. Next: **Stream C** — wire the proven packets into `CombatAI`/`QuestAI`/`PacketLogger` (replace mock
data) and reconcile fake-test tasks 54/63. B6b (bot earns a quest via NPC talk + `RequestBypassToServer`(0x21))
is a follow-on. B7's earlier "buy-open blocked" note is corrected — the genuine merchant flow (2 clicks + full
`npc_<objId>_Buy <listId>` bypass) works; see `Audit/38`.

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

## 5. Recommended paths (next — B7+)
1. **B7 — live trade proof** (a bot buys/sells with an NPC merchant; extends Action+RequestBuyItem/RequestSellItem).
2. **Wire the proven packets into the engine** (Stream C): fix `PacketLogger.parseNpcInfo` to the real
   `AbstractNpcInfo` layout, then route real Action/Attack/StatusUpdate/QuestList into `CombatAI`/`QuestAI`.
3. **B6b — bot earns a quest via NPC talk + `RequestBypassToServer`(0x21)** (needs NPC navigation + HTML/bypass).
4. **Relocate + heal the remaining 23 `ai_%` chars** (still in the void at 16600,17000,434 → die on login).
5. Stream C/G cleanup — fake `assertTrue(true)` tests 54/63; ~145 unwired stub classes.

## 6. Key file/command map
- Orient: `START_HERE.md` · Rules: `Documentation/WORKFLOW.md` · Board: `TASKS.md` · Status: `STATUS.md`
- Login spec: `Documentation/Audit/31-login-protocol-handshake.md` · SQL DB: `loginserver` (accounts) / `gameserver` (characters)
- `scripts/session_start.sh` (resume-aware) · `scripts/session_end.sh` (commit+cleanup) · `scripts/real_status.sh` (real state)
- Probe/testers: `AIPlayerEngine/.../examples/LoginProbe.java`, `.../RawInitProbe.java`
