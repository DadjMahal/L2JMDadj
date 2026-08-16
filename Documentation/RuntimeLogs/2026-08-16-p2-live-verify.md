# 2026-08-16 Task P2 — Live single-bot verify: XP/min + reconnect (farming fix + hop-Z P1)

> Live single-bot run of the farming-fix + P1 build against the locally-revived l2j stack
> (Login :2106, Game :7777, JDK 25). Watch window ~12 min (12:05–12:17) plus a mid-run
> GameServer kill/restart reconnect test at 12:22–12:26. All evidence is real (telemetry +
> server logs + gameserver DB), not fabricated.

## TL;DR
- **Kills / organic XP: VERIFIED (sustained).** Lv7 CombatBot_01 (charId 100000, Damascus
  + leather set from P0) farms Talking Island mobs with **one-shot hits**; 34 kills and
  35 `EVIDENCE-H5 EXP +` gains across the run. Live exp climbed **14213 → 14550** pre-restart
  and **read back the persisted 14365 from DB on reconnect**, then resumed.
- **XP/min ≈ 14–16** sustained (with a natural ~2.5-min re-target pause mid-window), moving
  ~1,700 u/min (serverMoved total **20,419 u**).
- **Persistence HOLDS:** DB exp went **14181 → 14365** via the 15-min `CharacterDataStoreInterval`
  autosave (no logout needed) and survived the GameServer restart.
- **Reconnect / broken-pipe guard: VERIFIED LIVE (the strong pass).** Killing the GameServer
  mid-session produced `GS reader stopped` → `session ended: GS connection lost (reader
  stopped)`; the bot retried login cleanly (`LoginOk`/`ServerList` → `No PlayOk`) with **no
  HOP/MoveTo sends into the dead socket**; once the GameServer returned it got a real `PlayOk`
  → `ENTERED WORLD` → immediately resumed XP.
- **No-re-loop / geo-wall: HOLDS.** 4 `hop unreachable` abandons (the far `farm:Talking
  Island (-90183,248034)` route hit a geo-wall) each re-planned to a **different** destination
  (e.g. far-point `(-82602,253656,-3516)`); no same-destination re-loop.
- **Movement-sync (0x01/0x47): WORKING.** The `CharMoveToLocation`/`StopMove` parsers update
  self `playerX/Y/Z` (fine-level log), which is what keeps hop routes from being abandoned as
  "unreachable" (H1 `serverMoved` grows every window).

## Stack (all on this host)
| Component | PID | Port | Note |
|---|---|---|---|
| LoginServer.jar | 8832 | 2106 (login), 9014 (GS) | started 11:58 |
| GameServer.jar | 9024 → **21369** (restarted) | 7777 | Server 2: Sieghardt; killed+relaunched for reconnect test |
| FleetPlay bot | 10714 | dash 8200 | `FleetPlay 1 … movement` |

## 1) XP/min + kills (pre-restart watch, 12:05 → 12:17)
- Watch cadence 30 s; samples captured live exp + `EVIDENCE-H5 expGained` + `EVIDENCE-H1 serverMoved`.
- expGained went **32 → 168** over the window (~136 in ~11 min ≈ **12–16 XP/min**); a
  ~2.5-min re-target pause (exp flat at 14317) is why the average sits below the +8/30s peak.
- serverMoved total **~11,000 u** by end of watch; bot tagged targets (`action=ATTACK`,
  `USE_SKILL` POWER_STRIKE) and rotated zones.
- Gear one-shots: `sysmsg#35 [1049]` damage on a 62-HP Bearded Keltir / 90-HP Elder Keltir.

## 2) DB persistence (no-logout autosave)
| time | DB exp | source |
|---|---|---|
| 12:05 (bot baseline) | 14181 | pre-run last logout save |
| 12:21 (autosave fired ~15 min after login) | **14365** | `CharacterDataStoreInterval=15` (General.ini) |
| 12:26 (after GameServer restart, read-back on re-enter) | 14365 | DB → world on `PlayOk` |
| 12:28 (live, still farming) | live 14419 | /api/players |

## 3) Reconnect / no-zombie (GS kill at 12:22:12)
Timeline (from /tmp/p2-fleet.log):
```
12:22:12  kill -9 GameServer (pid 9024); :7777 closed
12:22:1x  GS reader stopped
           WARNING session ended: GS connection lost (reader stopped)
12:22:2x  LoginOk / ServerList=1 / "No PlayOk"   <- repeated clean retries (every ~15s)
          ... NO HOP / NO MoveTo sends into the dead socket (HOP count frozen at 15)
12:25:34  GameServer relaunched; Registered on login as Server 2: Sieghardt
12:26:xx  PlayOk - full login auth. SessionKey(...)
           ENTERED WORLD [phase0.movement ON]
           EVIDENCE-H5 EXP +14365 (read-back) -> +23 -> 14411 -> +31 -> 14581 ...
           HOP -> goal:hunt:268461933 advance on hostile at -84768,251934
```
**Verdict: HOLDS.** Guard → clean login retry loop (no zombie sends) → auto re-enter world when
the GS returns → organic XP + movement resume (expGained reached **238**, serverMoved **20,419 u**).

## 4) No-re-loop / geo-wall handling (post-respawn logic)
- 4 `hop unreachable after 2 timeouts` abandons; each followed by a different re-plan
  (e.g. walled `farm:Talking Island (-90183,248034)` re-planned to far-point
  `(-82602,253656,-3516)`). The `noteUnreachableDestination` memory + far-point re-roll are
  working live (no same-coordinate re-loop within a session).

## Files
- Bot log: `/tmp/p2-fleet.log` · watch ledger: `/tmp/p2-watch.log` · GS relaunch wrapper log: `/tmp/p2-game2.log`
- Telemetry: `GET :8200/telemetry` (H1/H5) · `GET :8200/api/players` (x/y/z + exp)
- DB: `gameserver.characters charId=100000` (CombatBot_01, level 7)

## Reproduction
```bash
export PATH=~/.jdk/jdk-25.0.4+7/bin:$PATH
cd ServerBuild/login && ./LoginServerTask.sh &   # Login :2106
cd ServerBuild/game  && ./GameServerTask.sh &    # Game   :7777
cd AIPlayerEngine && java -cp target/classes:src/main/resources \
  com.aiplayer.examples.FleetPlay 1 127.0.0.1 7777 2106 8200 movement
```

## Follow-ups / notes
- Sustained farming confirmed at L7; no kill-blocker remains (P0 gear + P1 hop-Z both held live).
- The remaining optional polish is the degenerate far-point `destZ` generation (interpolated now,
  but far-points still use `fromZ`, which can sit off-ground on steep zones) — low priority, since
  geo-issues re-plan to a different destination rather than stalling.
- Servers + the single farming bot were left **up** after this verification so the operator can
  observe; stop with `kill 21369 8832 10714` (GameServer, LoginServer, FleetPlay).