# 2026-08-15 Task 0005 — Live re-verification of the farming fix (post-respawn re-loop + reconnect)

> Live single-bot run against the locally-built l2j server (JDK 25, GUI disabled).
> Watch window: **19:45 → 20:22** (35 min telemetry watcher) + a **mid-session GameServer
> restart** at 20:36 → watch2 20:42 → 20:52. Evidence entry covers kills/XP accrual, the
> post-respawn farm-zone re-loop, and the broken-pipe / reconnect behavior.

## TL;DR
- **Kills / XP accrual: FAILED in the observed steady-state window.** Zero XP for the whole
  pre-restart 35-min dash. Root cause is *combat effectiveness*, not the re-loop fix: the bot
  only deals **4 dmg/hit** (inherited starter+single Short Sword gear) vs a **107-HP Wolf that
  hits for 14**. The bot got a wolf down to **12/107 HP but died first** (twice).
- **Reconnect / broken-pipe fix: HOLDS (verified live, the strong pass).** Killing the GameServer
  mid-session triggered `GS connection lost (reader stopped)` → clean retry loop against the
  login server (**no HOP-sends into the dead socket**) → auto re-entered the world when the GS
  returned. Real XP + movement only resumed *after* this reconnect.
- **Post-respawn farm-zone re-loop: mostly holds.** After the respawn every replan selected a
  *different* coordinate via `noteUnreachableDestination`; the SAME destination never repeated
  until a fresh `runSession` (reconnect) / the 15-min TTL, and then only at a *different* center
  coordinate. The residual movement failure is a distinct **geo-reachability** issue (`z=-3560`
  dests vs ground `z≈-3602`), not the re-loop.

## Evidence log files
- `/tmp/fleet-run.log` — full bot run; `/tmp/fleet-watch.log` — 35-min telemetry ledger (done `20:22:21`).
- `/tmp/fleet-watch2.log` — post-restart telemetry ledger (done `20:52`).
- DB: `gameserver.characters charId=100000` (CombatBot_01, level 5).

## 1) Kills / XP accrual
| metric | value |
|---|---|
| `EVIDENCE-H5 EXP +` events (`/tmp/fleet-run.log`) | 4 lines, but only **3 are real gains** |
| real XP gains | `+105 → 4556`, `+141 → 4697`, `+35 → 4732` = **281 total** — ALL after reconnect |
| first real gain timestamp | ~20:39:50 (just after re-enter-world) |
| pre-restart window (19:45→20:36) | `expGained=0` the whole way |
| hits landed (`sysmsg#35` "You hit for X damage") | 135 total |
| hits received (`sysmsg#36` "X hit you for Y damage") | 43 total |
| DIE events (`self=true`) | 2 |
| telemetry ring at check | `EVIDENCE-H5 expGained=281` |

Wolf status trail proves the race: wolf `CUR_HP` dropped to **12/107** while bot self was at
10/202 (`STATUS_UPDATE` 19:47:08) — bot lost the DPS race and died. The stale-target watchdog
(`STALE_TARGET_BUDGET_MS`) then re-armed repeated engagements with the same wolf and no XP delta.

**Verdict:** the farming *code* accrues XP when a kill completes (confirmed post-reconnect), but
a level-5 char with 4-dmg hits cannot out-trade a 107-HP wolf, so **no kills/no XP in the
steady-state dash**. This is a combat-effectiveness gap, not a regression of the re-loop fix.

## 2) Post-respawn farm-zone re-loop
- Respawn event at 19:47:12 (`DIE...deaths=1`) → HP regenerated 131→202 by 19:48.
- Post-respawn HOP trail: destinations were **all different** each replan
  (`far-point (-91266,238276)`, `(-91308,245784)`, `(-89347,237709)`, `(-92548,244245)`, ...).
- `noteUnreachableDestination(destX,destY)` fired on every `hop unreachable after 2 timeouts`
  (33 abandons).
- 33 distinct dest coords seen across the run (70 HOPs, duplicates = the 2-attempt resend).
- Two `farm:Talking Island` hops exist: center **(-92815,240533)** pre-restart and
  **(-87634,241924)** post-reconnect — different centers, separated by a fresh `runSession`
  (reconnect), matching the designed per-session memory reset (`ABANDON_TTL_MS = 15 * 60 * 1000`).

**Verdict: HOLDS.** No same-center re-loop within a session; the re-loop fix behaves as coded.

## 3) Reconnect / broken-pipe guard (live restart test)
Timeline (all from `/tmp/fleet-run.log`):
```
20:36:41  session ended: GS connection lost (reader stopped)   <-- guard fired on server kill
20:36:xx  clean LoginOk + ServerList retries, "No PlayOk" while GS down
          (no HOP sends / no MoveTo spams into the dead socket → zombie-loop fixed)
20:38:19  GameServer relaunched -> "Registered on login as Server 2: Sieghardt"
20:40:xx  PlayOk full login auth -> ENTERED WORLD [phase0.movement ON]  (fresh runSession)
20:39:50  EXP +105 (first real kill XP of the run)
```
Movement telemetry across the restart:
| sample | serverMoved (60s) | total |
|---|---|---|
| 19:45 (start) | 6053 u | 6053 u |
| … mid dash | 0 u | 6053 u (frozen) |
| 20:40 (post-reconnect) | 4221 u | 10169 u |
| 20:41 | 5440 u | **11389 u** |
| 20:43 | 7043 u | 24513 u |
| 20:46→ | 0 u | 24513 u (froze again) |

**Verdict: HOLDS.** The broken-pipe guard turned a ~2 h CLOSE-WAIT zombie into an immediate
reconnect; movement + XP resumed after a genuine server-wide restart.

## Follow-up (new finding)
Residual failure is **far-point geo-reachability**, not the re-loop fix:
- Pre-restart HOPs used `z=-3560`; post-reconnect HOPs use `z=-3602` (ground). Movement froze
  whenever the planner gave `z=-3560` dests — the server refuses them (geo-unwalkable), so every
  hop times out and `serverMoved` stays at 0 until either a different-coordinate burst or a
  reconnect. Degenerate far-point generation (`z=-3560`) is the actionable item to fix next.

## Task 0005 status
- Post-respawn no-re-loop: **VERIFIED (holds)**.
- Reconnect / no-zombie / auto-re-enter: **VERIFIED (holds)**.
- Sustained kills/XP: **NOT ACHIEVED in steady state** (gap = bot damage vs wolf HP), re-verify
  after fixing 4-dmg hits / equipping the char or increasing iterate cadence.

## Reproduction / run command
```
java -cp target/classes:src/main/resources com.aiplayer.examples.FleetPlay 1 127.0.0.1 7777 2106 8200 movement
```
(GameServer relaunched with `java $(cat ServerBuild/game/java.cfg) -jar ../libs/GameServer.jar`.)