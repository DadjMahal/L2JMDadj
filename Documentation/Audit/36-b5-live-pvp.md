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

## Reproduce
`scripts/b5_pvp_prove.sh` — position+heal both bots → restart LS → run `PvPProbe` → assert mutual
attacker ids on both connections and/or DB PvP counter changes.
