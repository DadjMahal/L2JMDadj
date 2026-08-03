# Stream C — Slice 5: Live Combat Loop Driver + Proof (LIVE PROVEN 2026-08-03)

## Result: C5 PROVEN (live, against the running L2JM server, no server source changes)

The final Stream C gap — driving the in-engine pieces together in one live loop — is now closed and
proven live:

```
scripts/c5_live_combat_proof.sh ai_combat_01 CombatBot_01 ai123pass -82759 250149 -3600 20
```
-> `engaged-actions=18; target=268439318 serverConfirmedDamage=1` -> **[OK] C5 PROVEN** (exit 0).

## What was added

1. **`AIPlayerEngine/.../examples/CombatLoop.java`** — live driver (`main`):
   - `L2JProtocol` login auth (LoginServer :2106) -> `GameServerClient.connectAndEnterWorld` (GS :7777)
     -> `startReader()`.
   - **Position seeding** from the known spawn (wx,wy,wz) so distance-to-enemy works even before a self
     CharInfo arrives (live runs showed the server sends no self CharInfo in this enter-world flow;
     `PacketLogger.playerX/Y/Z` stay 0 otherwise).
   - **`CombatAI.setPacketLogger(gs.getPacketLogger())`** — the fix that made decisions real: `CombatAI`
     previously held its OWN empty private logger, so `detectNearbyEnemy()` never saw live NPC_INFO.
     Now it shares the exact buffer the reader feeds.
   - Loop: sync pos -> `CombatAI.makeDecision()` -> `CombatFramePlanner.plan()` -> `sendGameFrame(...)`
     (real Action 0x04 + AttackRequest 0x0A), with grep-able `ENGAGED` / `LIVE COMBAT LOOP COMPLETE` markers.
2. **`CombatAI`**: `packetLogger` no longer `final`; added idempotent `setPacketLogger(PacketLogger)`.
3. **`scripts/c5_live_combat_proof.sh`** — reproduces the B4 relocation/heal + DB before/after, runs the
   driver, and scores on **server-confirmed target damage OR exp increase**:
   - `serverConfirmedDamage`: the engaged target's STATUS_UPDATE must show CUR_HP < MAX_HP.
   - `SKIP_RESTART=1` supported (default 0 = b4-style LoginServer restart to clear stale auth).
4. **`CombatAITest.testSetPacketLoggerSharesLiveBuffer`** — new unit test (55/55): attaching a shared
   logger makes `makeDecision()` attack the hostile NPC parsed into it and select it as target.

## Live evidence (grep from /tmp/c5_combat_out.txt)

```
[CombatLoop] in world at (-82759,250149,-3600) — running live combat loop for 20s
[CombatLoop] SENT opcode=0x04 target=268439318 action=ATTACK hostileCount=14   (Action)
[CombatLoop] SENT opcode=0x0A target=268439318 action=ATTACK hostileCount=14   (AttackRequest)
[CombatLoop] ENGAGED target=268439318 action=ATTACK
INFO: STATUS_UPDATE: objId=268439318 [MAX_HP=107, CUR_HP=107] ...   <- server confirmed the target
INFO: STATUS_UPDATE: objId=268439318 [MAX_HP=107, CUR_HP=103] ...   <- OUR Action landed 4 damage
[CombatLoop] LIVE COMBAT LOOP COMPLETE sentActions=18 targetsTracked=17
```

The target wolf `268439318` (npc 20120) dropping 107 -> 103 HP in the server's own StatusUpdate is the
causal proof: engine decision -> real frames -> server processed our attack.

## Note (limitation)
The level-2, ungeared bot is outnumbered (~14-17 hostiles) and dies before landing a kill, so exp did
not rise in these runs; evidence is the server-confirmed target damage. Equipping/farming a single
weaker mob would yield an exp increase if desired.

## Chain
Stream C slices 1-4 (parse/decide/plan/GS-client) + slice 5 (live driver) = the engine now performs a
complete live combat loop. Next likely: Slice 6 (packet feedback drive) / B6b (NPC quest via
RequestBypassToServer).
