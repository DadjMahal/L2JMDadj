# Audit 45b - TIM-001 server-side movement & persistence findings (analysis only)

Date: 2026-08-12 . Lane: task_0018 . Type: analysis, NO runtime changes deployed.

## 1. The far-move rejection (why single big hops fail)
- SourceCode/java/org/l2jmobius/gameserver/network/clientpackets/MoveToLocation.java:156-159
- The handler rejects any single move whose squared distance exceeds 98,010,000 (9900*9900), i.e. max ~9900u.   
  ```java
  // Can't move if character is trying to move a huge distance.
  if (((dx * dx) + (dy * dy)) GT 98010000) // 9900*9900
  ```
- Consequence: the bot's old single MoveToLocation to a ~21k u far-point was dropped server-side, so no
  position change persisted. This is the cap the engine's <=4800u ZoneRouter.buildHops() avoids.

## 2. Position persistence path (char save)
- Movement sink is Object Position - Player getX/getY/getZ, flushed on Player store (interval + logout) to
  gameserver.characters (x,y,z,heading) in SourceCode/.../model/player/Player.java.
- Live run evidence: gameserver.characters x/y/z were IDENTICAL before/after the 2-min window because no
  accepted far move ever landed (each candidate exceeded the cap). Short hops <=4800u DO land (Audit/44 H1
  proved +400u) -- that is the fix path.

## 3. Recommendation (server deploy would go to /home/volodro/L2JM, out of this repo)
1. No server change required for far-travel: the client was sending un-split far moves; engine-side
   hop-routing (shipped) is the correct fix. A server change would be a safety-valve only.
2. Optional hardening (server): hoist the magic 98010000 at MoveToLocation.java:159 into a named constant
   and log on silent reject instead of silent drop.
3. The real open item is PROVING hops persist (Audit 45a proof doc / live run), not server code.
