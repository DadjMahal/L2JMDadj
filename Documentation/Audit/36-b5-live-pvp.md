# 36 — B5: live PvP proof (spec) — 2026-08-03

> **B5.** Prove two AI players fight each other (PvP) via the external socket. `PvPProbe` drives two
> accounts online in the same GS, each sends `Action`(0x04)+`AttackRequest`(0x0A) on the other, and
> two reader threads tally the server `Attack`(0x05) packets **by attacker objectId** to prove mutual
> player-vs-player combat. No L2JM server source changes (all client-side, `AIPlayerEngine/`).

## Verified server facts (Interlude, SourceCode)
- `AttackRequest`(0x0A) → `target.onForcedAttack(player)` (`Creature.java:5288`): blocked only in a
  **peace zone** / if target `canBeAttacked()` false; otherwise flags the attacker and starts ATTACK
  intention (the standard PvP flag). Field/dungeon areas are PvP-legal.
- Server `Attack`(0x05) payload (`Attack.java` writeImpl): `[0x05][attackerObjId][targetId][damage]
  [flags][attackerX][attackerY][attackerZ][(hits-1)][...]{targetX][targetY][targetZ}`. First two ints =
  attacker objectId, target objectId → lets us attribute hits to a specific player.
- DB `characters`: `karma`, `pvpkills`, `pkkills` columns exist for a kill-based bonus proof.
- Players: objectId == charId for players. CombatBot_01 charId/objectId=2, CombatBot_02 charId/objectId=3.
  Both at the same open-field coordinate (Wolf zone; not a peace zone) so they are mutually visible/in range.

## Implementation (`AIPlayerEngine/.../examples/PvPProbe.java`)
1. Login + GS enter-world for BOTH accounts (`ai_combat_01`, `ai_combat_02`) — reuse the B4 proven
   enter-world flow (classic Socket, SO_TIMEOUT, `EnterWorld`(0x03)), one connection per bot.
2. Each bot sends `Action`(0x04) + 500ms + `AttackRequest`(0x0A) on the OTHER bot's objectId (A→3, B→2).
3. Two reader threads (one per socket) loop reading payloads; for each `ATTACK`(0x05) parse attacker
   objId + target objId + damage and tally `attackerId→count` per connection.
4. Read ~15–20s, then close both sockets (both log out), join threads, print per-connection attacker tallies.

## Verification (paste both)
- Probe stdout: per-connection ATTACK attacker-objId tallies. **PvP PROVEN if both connections see
  attacks attributed to attacker 2 and attacker 3** (mutual player-vs-player hits), plus any
  `StatusUpdate`/`SystemMessage` damage signals.
- DB bonus: `karma` / `pvpkills` / `pkkills` / `curHp` of either bot changing (a kill) — optional.

## Positioning note
Both bots must be alive and at a PvP-legal (non-peace-zone), monster-light spot. They are placed at the
Talking Island Wolf field (open world). Wolves may aggro; PvP verdict is isolated by attacker-objId
(players=2,3; wolves=large ids).

## ✅ Verified result — B5 PVP PROVEN 2026-08-03

`PvPProbe` logged in both accounts, entered both at the same open-field spot, and each sent
`Action`(0x04)+`AttackRequest`(0x0A) on the other. Two reader threads tallied `Attack`(0x05) by attacker
objectId on each connection. Pasted evidence (`/tmp/b5_pvp.log`):

```
[PvPProbe] A IN WORLD (ai_combat_01)      [PvPProbe] B IN WORLD (ai_combat_02)
[PvPProbe] A sent Action(0x04) on objId 3 ... A sent AttackRequest(0x0A) on objId 3
[PvPProbe] B sent Action(0x04) on objId 2 ... B sent AttackRequest(0x0A) on objId 2
=== A's connection Attack attacker-objId -> hits ===
  attacker objId 2 : 13 hits      <-- CombatBot_01 attacking
  attacker objId 3 : 12 hits      <-- CombatBot_02 attacking
=== B's connection Attack attacker-objId -> hits ===
  attacker objId 2 : 13 hits
  attacker objId 3 : 12 hits
A's conn saw attacks by {2,3}=true ; B's conn saw {2,3}=true
PVP PROVEN (mutual player-vs-player attacks) = true
```
**DB damage proof:** `CombatBot_02` `curHp` **126 → 120** after the fight (took real PvP damage from
CombatBot_01). No L2JM server source changed. Both bots logged out cleanly (online=0).

### Notes
- Player objectId == charId (CombatBot_01=2, CombatBot_02=3). Both connections see the same broadcast
  `Attack` packets, so each shows both attackers — mutual PvP is proven when a connection sees {2,3}.
- Neither bot died, so no `karma/pvpkills/pkkills` change (expected for a no-kill mutual fight);
  the damage on CombatBot_02 is the DB kill-proof substitute.

## Reproduce
`scripts/b5_pvp_prove.sh` (position+heal both bots → restart LS → run `PvPProbe` → assert mutual attacker
ids and/or CombatBot_02 hp drop).

