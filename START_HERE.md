# 🚀 START HERE — L2JM (read this first, every session)

> 📖 **For the FULL project history & near-context, read `Documentation/SESSION_HANDOFF.md`** (once per session, ~2–3k tokens). This file (below) is the fast orientation; the handoff is the depth.
> Rules live in `AGENT_ONBOARDING.md`; deep context is read **on demand** via the routing table.

## ⚠️ Resume check (do this first)
If `SESSION_IN_PROGRESS.md` exists at repo root → the last session was **rate-limited mid-work**.
Read it FIRST and resume the "Current step" (don't pick a new task). If absent, continue below.


## Project (one line)
L2JMobius **Interlude** server (`/home/volodro/L2JM`) + external-socket **AI Player Engine** (`AIPlayerEngine/`)
that connects as real client sockets — **no server code modifications**.

## Honest current state *(source: `real_status.sh` + `AIPlayerEngine/AIStatusLogs/ai_progress_report.txt`, refreshed at each milestone)*
- ✅ Server **UP**: LoginServer :2106, GameServer :7777 (since Jul 31).
- ✅ Source-code **audit complete** — iterations 1–30 in `Documentation/Audit/` (plus 31–35 protocol/combat).
- ✅ AIPlayerEngine **compiles** (155 files). Bootstrap + telemetry + perception scaffolding done.
- ✅ **Live NPC combat (B4) + live PvP (B5) + live quest (B6) PROVEN (2026-08-03)** — an AI bot killed a
  Wolf/Keltir (exp 0→105, level 1→2), two bots fought each other (Attacker objId2:13 / objId3:12 hits;
  CombatBot_02 took PvP damage), and an enter-world triggered the real server quest engine (added Q00255
  Tutorial state to `character_quests`). External-socket path proven end-to-end.
- 🔴 The **CombatAI/QuestAI/MerchantAI/SocialAI decision classes still run on mock data** — they need wiring
  to the real packets `CombatProbe` demonstrates (Stream C). PvP / quest / trade proofs (B5+) not yet live.
- 🔴 Fabricated status docs were quarantined (see Blockers).

## Current phase / next task
- **Current phase:** **B6 DONE — live quest proof PROVEN (2026-08-03).** An AI player's enter-world triggers
  the real server quest engine: the server ran `Q00255_Tutorial`'s `notifyEvent("UC")` and **added quest state
  (`Ex`, `ucMemo`) to `character_quests`** (DB before=1 row → after=3 rows). Trade proof (B7) is next.
- **Stream A DONE (2026-08-03):** A1 cold-start test (17/17 PASS; bootup ~73k→~1.3k tokens); A2 `real_status.sh` fix; A3 `count_ai_players.sh` fix (real DB: 25 registered, 0 online).
- **B1 DONE:** AI credentials valid (DB pw → Base64(SHA1); connectPlayer bug fixed).
- **B2 DONE (compiles, NOT live-proven):** real L2J login handshake — `LoginCrypt` + `L2JProtocol` rewrite; spec in `Audit/31-login-protocol-handshake.md`.
- **B3 — LIVE-PLAYER PROOF DONE (2026-08-03):** 1 AI player (`CombatBot_01`/`ai_combat_01`) proven **online=1** in the `gameserver` DB via the full external socket flow.
  - **Phase 0 (done):** Init decodes — root cause was **little-endian Blowfish ≠ JDK Blowfish**; ported server engine → `protocol/crypt/BlowfishEngine.java`; `Audit/32-init-decode.md`.
  - **Phase 1 (done):** login-server auth: Init → AuthGameGuard → RequestAuthLogin → **LoginOk** → ServerList → RequestServerLogin → **PlayOk**; SessionKey captured. Client packets use **session key + checksum** + **self-inclusive size**. `Audit/33-phase1-login-auth.md` + `scripts/b3_login_probe.sh`.
  - **Phase 2 (done):** GameServer enter-world: ProtocolVersion(746) → KeyPacket → **AuthLogin** → CharSelectInfo → **CharacterSelect** → CharSelected → `setOnlineStatus(true,true)` → **online=1** live-verified. `Audit/34-phase2-enter-world.md` + `scripts/b3_enter_world_prove.sh`.
- **B4 — LIVE NPC COMBAT PROVEN (2026-08-03):** `CombatProbe` (login → enter-world → **EnterWorld(0x03)** →
  NPC_INFO scan → **Action**(0x04)+**AttackRequest**(0x0A)) attacked a real Talking Island Wolf/Keltir monster:
  server broadcast **18 `ATTACK`(0x05)** hits; DB `exp` **0→105**, **level 1→2**, curHp 126→145. Spec + evidence:
  `Audit/35-b4-live-npc-combat.md`; reproduce: `scripts/b4_combat_prove.sh`.
  - Key discoveries: client MUST send **EnterWorld** to spawn; NIO SocketChannel ignores setSoTimeout (hang) —
    CombatProbe uses classic Socket; **PacketLogger.parseNpcInfo is off-by-one** (needs fix, Stream C);
    the 25 `ai_%` chars spawn in the **void** at (16600,17000,434) and die — relocated CombatBot_01 to the Wolf zone.
- **B5 — LIVE PVP PROVEN (2026-08-03):** `PvPProbe` (two GS connections: `ai_combat_01` objId2 &
  `ai_combat_02` objId3 at the same open field) each sent `Action`(0x04)+`AttackRequest`(0x0A) on the other →
  server broadcast **Attacker objId 2: 13 hits / objId 3: 12 hits** on both connections; **`CombatBot_02`
  curHp 126→120** (took real PvP damage). Evidence: `Audit/36-b5-live-pvp.md`; reproduce: `scripts/b5_pvp_prove.sh`.
  - AttackRequest on a player = `Creature.onForcedAttack` → PvP flag + attack (blocked only in peace zones).
- **B6 — LIVE QUEST PROOF PROVEN (2026-08-03):** `QuestProbe` enters the world → the server's
  `EnterWorld.loadTutorial` runs `Q00255_Tutorial`'s `notifyEvent("UC")` and **writes new quest state
  (`Ex=-2`, `ucMemo=0`) to `character_quests`** (DB delta before=1→after=3 rows); two-way quest protocol
  (`RequestQuestList`(0x63) → `QuestList`(0x80)) exercised. Evidence: `Audit/37-b6-live-quest.md`; reproduce:
  `scripts/b6_quest_prove.sh`. (Tutorial is excluded from the visible QuestList by its `Ex` flag — by design.)
- **Next tasks (B7+):** live **trade** proof — and wiring the proven PvE/PvP/quest packets into the engine's
  `CombatAI`/`QuestAI`/`PacketLogger` (Stream C). B6b (bot earns a quest via NPC talk+`RequestBypassToServer`) is a follow-on.

## Blockers / open issues
1. **✅ B3 RESOLVED (2026-08-03) — 1 AI player online.** Full external socket flow proven (no L2JM server source changed):
   - Phase 0: server **Blowfish is little-endian**, not JDK-compatible → ported `protocol/crypt/BlowfishEngine.java` (`Audit/32`).
   - Phase 1: client login packets use **session key + checksum** + **self-inclusive size** (`Audit/33`).
   - Phase 2: GS ProtocolVersion(746) → KeyPacket → AuthLogin → CharacterSelect → **online=1** live-verified (`Audit/34`, `scripts/b3_enter_world_prove.sh`).
2. **✅ B4 RESOLVED (2026-08-03) — live NPC combat PROVEN** (`CombatProbe`, `Audit/35`): AI player attacked a
   real Wolf/Keltir monster → 18 `ATTACK`(0x05) hits + **exp 0→105, level 1→2**.
3. **✅ B5 RESOLVED (2026-08-03) — live PvP PROVEN** (`PvPProbe`, `Audit/36`): two bots fought each other →
   mutual `Attack` hits (objId2:13 / objId3:12) + `CombatBot_02` took PvP damage (curHp 126→120).
4. **✅ B6 RESOLVED (2026-08-03) — live quest PROVEN** (`QuestProbe`, `Audit/37`): enter-world triggered the
   real server quest engine (`Q00255_Tutorial` UC handler) → server added quest state (`Ex`/`ucMemo`) to
   `character_quests` (DB 1→3 rows). **B7–B10 (live trade proof etc.) were next** — engine decision classes still on mock data.
4b. **✅ B8 RESOLVED (2026-08-03) — live MOVEMENT PROVEN** (`MoveProbe`, `Audit/39`): AI sent
   `MoveToLocation`(0x01) from Silvia's spot to Trader 30040's spawn (-82515,241221,-3728); the server
   broadcast 17× `CHAR_MOVE_TO_LOCATION`(0x01) + `VALIDATE_LOCATION`(0x61) and **`characters.x/y/z` moved
   from (-83789,240799,-3717) to exactly (-82515,241221,-3728)**. B7 (trade) still IN PROGRESS — buy-dialog
   open blocked on bypass validation (`Audit/38`).
4c. **✅ B9 RESOLVED (2026-08-03) — live CHAT PROVEN** (`ChatProbe`, `Audit/40`): bot A whispered a
   run-unique token to bot B via client `Say2`(0x38) (WHISPER, no level gate); the server delivered
   `CREATURE_SAY`(0x4A) containing the token to B's connection (`received = true`) and echoed to A
   (`echo = true`).
4d. **✅ B10 RESOLVED (2026-08-03) — live PARTY PROVEN** (`PartyProbe`, `Audit/41`): A invited B via
   `RequestJoinParty`(0x29); B accepted `RequestAnswerJoinParty`(0x2A); the server created a real party →
   joiner B received `PARTY_SMALL_WINDOW_ALL`(0x4E) and leader A received `PARTY_SMALL_WINDOW_ADD`(0x4F).
4e. **✅ B7 RESOLVED (2026-08-03) — live TRADE PROVEN** (`TradeProbe`, `Audit/38`): CombatBot_01 bought item
   118 (price 75) from Trader Silvia. Genuine merchant flow: 2× `Action`(0x04) → `NpcHtmlMessage`(0x0F) →
   extract the validated `npc_<objId>_Buy 3000300` bypass → `RequestBypassToServer`(0x21) → `BuyList`(0x11) →
   `RequestBuyItem`(0x1F). The server **deducted adena 500000→499925→499850 (×2 runs)** and **added item-118
   rows** (DB-verified). This **corrects the earlier "blocker" misdiagnosis** (real fixes: send 2 clicks —
   NpcClick targets on #1 and opens dialog on #2 — and send the full `npc_<objId>_Buy <listId>` bypass the
   server caches, not `Buy <listId>`).
   → **ALL B-stream live gameplay proofs are now DONE (B4 PvE, B5 PvP, B6 quest, B7 trade, B8 movement,
   B9 chat, B10 party).** Next: Stream C wiring of the proven packets into the engine.
4f. **🔄 STREAM C STARTED (2026-08-03) — slice 1 done:** `PacketLogger.parseNpcInfo` off-by-one fixed to the
   real `AbstractNpcInfo` layout (`[objId][displayId+1000000][isAttackable][x][y][z][heading]`), hostility now
   packet-derived; fake tests 54/63 → real assertions; `PerceptionAccuracyTest`(42) reconciled to the real
   layout. **36/36 tests PASS, BUILD SUCCESS.** (RuntimeLog/2026-08-03-streamC-npc-info-fix.md)
4g. **🔄 STREAM C — slice 2 done:** real combat encoders `PacketCodec.encodeAction(0x04)`/
   `encodeAttackRequest(0x0A)` in the B4-proven framing; `CombatAI.calculateDistanceTo` real 3D distance &
   `shouldDefend()` deterministic (no more `Math.random()`); `getPacketLogger()`/`getSelectedTargetObjId()`.
   **41/41 tests PASS.** (RuntimeLog/2026-08-03-streamC-combat-decisions.md)
4h. **🔄 STREAM C — slice 3 done (decision→send):** `CombatFramePlanner` maps a combat decision to ordered
   wire frames (Action 0x04 → flood-gap → AttackRequest 0x0A; FLEE → MoveToLocation 0x01);
   `GameServerFrameWriter` emits framed packets; `PacketCodec.encodeMoveToLocation` (B8 layout);
   `AIPlayerConnection.executeCombatDecision()` + `setGameServerWriter()`. **49/49 tests PASS.**
   (RuntimeLog/2026-08-03-streamC-decision-to-send.md)
4i. **🔄 STREAM C — slice 4 done (reusable GS client):** `GameServerClient` retains the proven B3/B4 handshake
   (ProtocolVersion→KeyPacket→AuthLogin→CharSelect→CharSelected→EnterWorld) over a classic Socket, has a
   background reader feeding `PacketLogger`, and `attachToConnection`/`sendGameFrame`. Handshake builders
   (`encodeProtocolVersion`/`encodeAuthLogin`/`encodeCharacterSelect`/`encodeEnterWorld`) added to PacketCodec.
   **54/54 tests PASS** incl. an in-process fake-GS handshake + real Action(0x04) send. (RuntimeLog/
   2026-08-03-streamC-gs-client.md) Remaining: a live driver + proof script.
5. **Fabricated docs quarantined** in `Documentation/_archive_fabricated/` (`PHASE2_COMPLETE.md`, `README-MAGIC.md`, `REFACTORED_ROADMAP.md` (333-task), `WorkLog/SMARTPROJECT.md`, 2 fake reports). **Trust only** `ai_progress_report.txt`, `MORNING_REPORT_*.txt`, `real_status.sh`.
6. **DB names:** accounts are in the **`loginserver`** DB; characters in **`gameserver`**. `real_status.sh` uses `sudo mysql -u root gameserver`.
7. ✅ Tasks 54 & 63 RESOLVED (Stream C, 2026-08-03) — their fake `assertTrue(true)` tests replaced with real assertions on `makeDecision()`/`makePvPDuidedDecision()`; now `done` in TASKS.md.

## Reality check — run these and paste the output
```bash
git -C /home/volodro/L2JM status --short | head
ss -tlnp 2>/dev/null | grep -E '2106|7777'   # expect both LISTEN
/home/volodro/L2JM/AIPlayerEngine/AIStatusLogs/real_status.sh
```

## Routing table (read ONLY the file your task touches)
| Task touches | Read first |
|---|---|
| Combat AI | `engine/CombatAI.java`, `Audit/15-combat-ai.md`, `Audit/04-gameserver-network.md` |
| Network / protocol | `Audit/01-commons.md`, `Audit/04-gameserver-network.md`, `protocol/L2JProtocol.java` |
| Perception | `Audit/PART2-01-perception-systems.md`, `protocol/PacketLogger.java` |
| Quest system | `Audit/30-quest-progression.md`, `engine/QuestAI.java` |
| Goals / personality / emotion | `Documentation/goal-personality-system.md`, `Documentation/Streams.md` (Stream D), `engine/GoalTree.java`, `engine/LongTermGoalsAI.java`, `advanced/`, `Audit/36-goal-personality-audit.md` |
| Social / economy / trading | `Documentation/social-economy-system.md`, `Documentation/Streams.md` (Stream E), `engine/MerchantAI.java`, `engine/SocialAI.java`, `engine/ActivityScheduler.java`, `economy/`, `social/`, `Audit/37-social-economy-audit.md` |
| Multi-agent / QA / meta | `Documentation/MultiAgentQA.md`, `Documentation/Streams.md` (Stream F), `engine/AIPlayerManager.java`, `metrics/PerformanceMetrics.java`, `monitor/`, `scripts/check_style.sh`, `scripts/verify_no_dead_code.sh`, `Audit/38-multiagent-audit.md` |
| Docs / workflow only | `AGENT_ONBOARDING.md`, `Documentation/WORKFLOW.md`, `Documentation/Streams.md` |
| Anything L2JMobius protocol | **audit-first** — read the matching `Audit/*.md` before writing code |

## Rules (7 hard rules — full text in `AGENT_ONBOARDING.md`)
1. Verify before claim (no "working" without pasted output). 2. No fake logs. 3. Usage validation (grep for callers).
4. Audit-first. 5. Document before code. 6. Leave cleaner than you found it.
7. **Milestone doc-sync** — update the knowledge base (this file, `STATUS.md`, `SESSION_HANDOFF.md`,
   `TASKS.md`, `ai_progress_report.txt`) + commit after EVERY milestone, even mid-session (see `WORKFLOW.md` §3).
