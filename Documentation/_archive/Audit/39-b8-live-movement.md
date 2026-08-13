# 39 — B8: live movement proof (spec) — 2026-08-03

> **B8.** Prove an AI player deliberately moves its real character to a destination over the external socket.
> `MoveProbe` enters CombatBot_01, sends `MoveToLocation`(0x01), the server walks the character, and we
> verify the world-position change in `gameserver.characters` (x/y/z). No L2JM server source changes.

## Verified server facts (Interlude, SourceCode)
- `MOVE_TO_LOCATION` = 0x01 (`ClientPackets.java:46`, `MoveToLocation.java`): readImpl =
  `targetX,Y,Z, originX,Y,Z, movementMode:int` (0 = cursor keys, 1 = mouse).
- Server replies with movement broadcasts: `CHAR_MOVE_TO_LOCATION`(0x01?, server char move) / `VALIDATE_LOCATION`(0x61) / `STOP_MOVE`(0x47).
- On disconnect/logout the server persists the character's last position to `characters.x/y/z`.

## Implementation (`AIPlayerEngine/.../examples/MoveProbe.java`)
1. Login + enter-world for `ai_combat_01` (CombatBot_01) — reuse the proven single-bot flow.
2. Record the spawn position, then send `MoveToLocation`(0x01) to a valid, walkable destination
   (trader NPC 30040's spawn `-82515,241221,-3728`, ~1.4k units away in Talking Island village ground).
   payload = `[0x01][tX][tY][tZ][oX][oY][oZ][moveType:int=0]`.
3. Read ~15s, tally movement packets received (move confirm / validate / stop).
4. Close (logout) → server persists the walked position to DB.

## ✅ Result — B8 PROVEN (2026-08-03)

**MoveProbe** entered CombatBot_01 (proven login+enter-world flow), sent `MoveToLocation(0x01)` to
(-82515,241221,-3728) from its current position (-83789,240799,-3717), and the server walked it there.

**Wire evidence (MoveProbe stdout):**
- After the MoveToLocation, the server broadcast **17× CHAR_MOVE_TO_LOCATION(0x01)** + **1× VALIDATE_LOCATION(0x61)**.
- Also 37 NPC_INFO + 2 SYSTEM_MESSAGE during the same window.

**DB proof (gameserver.characters, CombatBot_01):**
- Before: `-83789 240799 -3717` (B7 left it at Silvia's spot)
- After:  `-82515 241221 -3728` = **exactly the requested destination**
- (online=0 on both reads; the position persisted on logout.)

**B8 ══ PROVEN**: the AI deliberately moved its real character to a destination over the external socket,
and the server persisted the arrived position.

## Reproduce
```
cd /home/volodro/L2JM/AIPlayerEngine && mvn compile
nohup timeout 40 bash -c 'java -cp target/classes com.aiplayer.examples.MoveProbe ai_combat_01 ai123pass 127.0.0.1 7777' > /tmp/move_probe.out 2>&1 &
# after ~26s: expect "CHAR_MOVE_TO_LOCATION(0x01)>0" and DB x/y/z ≈ (-82515,241221,-3728)
sudo mysql -u root gameserver -N -e "SELECT x,y,z FROM characters WHERE char_name='CombatBot_01';"
```

