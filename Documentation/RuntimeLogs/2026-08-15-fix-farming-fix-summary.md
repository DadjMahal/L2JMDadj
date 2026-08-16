# 2026-08-15 AI-Player Farming Fix — Implementation + Live Verification

## TL;DR
The AI bot that was stuck re-acquiring an unreachable ocean quest (Wolf Hunt giver at Gludio,
~148k units away from Talking Island; 0 kills / 0 XP over 36+ min) now **farms wolves on
Talking Island**: live run shows kills, XP gains, server-confirmed movement, and **zero**
ACQUIRE-loop repetition.

## Root cause (as previously diagnosed, now fixed in code)
1. `QuestGoalPlanner.pickQuestToAcquire()` always returned the FIRST "available" quest
   (Wolf Hunt) regardless of distance → every idle tick re-drove a route to a giver the bot
   could never reach (ocean/long-haul). The `null → plain farming` fallback was dead code.
2. FleetPlay had no cooldown: "hop unreachable" aborts immediately re-planned the **same**
   acquire goal → infinite loop (58 repeated quest decisions / 19 hop-unreachable timeouts in
   the 30-min run).
3. **Second, decisive discovery during re-verify:** even a *reachable* hunt hop was abandoned
   because the engine never parsed the server's `CharMoveToLocation` (0x01) / `StopMove`
   (0x47) broadcasts — the server *walks* the char between those packets, but the bot's
   `playerX/Y/Z` stayed frozen at the last `ValidateLocation`, so HopGate believed it never
   arrived and abandoned the route 45s later. Server DB proved the char WAS moved to the exact
   hunt target while the engine still thought it was 1.7k away.

## Changes (AIPlayerEngine)
| File | Change |
|---|---|
| `phase0/play/QuestGoalPlanner.java` | Added `MAX_ACQUIRE_DIST = 20_000`. `pickQuestToAcquire` now skips givers farther than that from the player, picks the NEAREST reachable quest (tie-break: higher recommendedLevel), and returns `null` when none is reachable → caller falls back to plain farming. |
| `phase0/play/AcquireCooldown.java` (new) | Pure POJO: N unreachable-route aborts (default 2) arm a cooldown window (default 5 min); `isSuppressed(nowMs)`/`reset()`/`recordUnreachableAbort(nowMs)`. |
| `examples/FleetPlay.java` | Wired the cooldown: aborts on `goal:acquire:*` routes increment it; while armed, an ACQUIRE goal from the controller is nulled so `ZoneRouter.plan` runs plain farming; reset on quest-journal non-empty or dialog-driven accept. |
| `protocol/PacketLogger.java` | **NEW** `OP_CHAR_MOVE_TO_LOCATION` (0x01) + `OP_STOP_MOVE` (0x47) parsers update self/entity positions from server movement broadcasts. |
| Tests | `QuestGoalPlannerTest` (+4 reachability cases), `AcquireCooldownTest` (+9), `PacketLoggerSelfStateTest` (+3 movement-sync), `BotPlayControllerTest` repointed. |

## Verification (fresh single-bot run, movement ON, dashboard :8200)
| Metric | Pre-fix 30-min | Post-fix (12 min) |
|---|---|---|
| `goal:acquire:*` routes | 30+ repeats | **0** |
| hop-unreachable aborts | 19 | **1** (a far `farm:Talking Island` zone wall, not ACQUIRE — bot re-planned to hunt) |
| kills (DIE) | 0 | **4+ and climbing at end of watch** |
| `EVIDENCE-H5 EXP +` | 0 | **4 gains** (35/105/35/105 …) |
| `EVIDENCE-H1 serverMoved` | 0 u | **11,061 u total, climbing** |
| bot state | frozen, retry-loop | farm/hunt, attacking 10+ frames |

### Evidence artifacts
- `/tmp/fix-verify2.log` — full bot run log (fresh codebuild)
- `/tmp/fix-verify2-watch.log` — 20s-spaced snapshot ledger
- Dashboard endpoints: `/telemetry` (H1/H5 lines), `/api/players` (live x/y/z + exp)

## Tests: **295 → 298, all green**
```
TOTAL tests=298 failures=0 errors=0 skipped=0
```

## Notes for operators
- The `farm:Talking Island` far-zone route can still hit a geo-wall; that's expected and safe —
  it now aborts and re-plans to hunting/other zones instead of re-looping the same ACQUIRE.
- Run command used:
  `java -cp target/classes com.aiplayer.examples.FleetPlay 1 127.0.0.1 7777 2106 <dashPort> movement`

---

# 2026-08-15 Follow-up — Post-respawn dead-loop & broken-pipe zombie (fixes)

## TL;DR
The farming fix was **verified** in the live run (acq=0 whole run, 4 kills, 4 XP gains, movement
tracking). But that run exposed **two new defects** in the post-respawn / server-death window:

1. **Post-respawn farm-zone re-loop** — after the bot died and respawned, `ZoneRouter.plan()`
   deterministically re-selected the **same** nearest farm zone center (23,922 u away,
   geo-unreachable) on every re-plan → 54 HOPs / 27 abandons, ~47 min of zero farming.
2. **Broken-pipe zombie** — when the game server died, `Phase0Wiring.send()` swallowed the
   IOException (returned `false`; loop ignored it) so `runSession()` never returned → the outer
   reconnect loop never fired → the bot kept "sending" against a dead CLOSE-WAIT socket for ~2 h
   (124 failed sends). The `serverMoved` telemetry ring-buffer (CAP=4096) was overwritten by ~2.7 h
   of frozen zombie-loop samples, hence `serverMoved=0` at check time.

## Fixes (AIPlayerEngine)
| File | Change |
|---|---|
| `phase0/movement/ZoneRouter.java` | Added a bounded, TTL'd **abandoned-destination memory** (`noteUnreachableDestination(x,y)` + internal `abandonedUntilMs` map). `plan()` now **skips zone centers** recorded as unreachable (picks the next-nearest zone instead), and the far-point fallback **re-rolls up to 8×** to avoid abandoned far points. Fresh per `runSession`, so a reconnect naturally forgets stale blacklists while a single session's re-loop is broken. |
| `examples/FleetPlay.java` | (a) On ANY hop-unreachable route abort, record `activeRoute.destX/destY` via `zoneRouter.noteUnreachableDestination(...)` so the next re-plan can't re-select it. (b) Added a **broken-pipe / zombie guard** at the top of the fleet loop: `if (!gs.isOpen()) throw new IOException("GS connection lost")` — this propagates out of `runSession()` into the existing `run()` loop, which sleeps 15 s then fires a **fresh `runSession()`** (new `GameServerClient` + `Phase0Wiring` + `ZoneRouter`) → real reconnection instead of zombie-sending. |
| Tests | `ZoneRouterTest` **+2** regression cases: (1) after abandoning a farm-zone center, the next `plan` must NOT re-select the same destination; (2) the far-point fallback re-roll avoids an abandoned far point. |

## Why `gs.isOpen()` works
`GameServerClient.readLoop()` already flips `open = false` on EOF / socket reset / IOException (it
logged "GS reader stopped" when the server died). Nothing was *checking* that flag — `send()` just
silently caught the write failure. The guard turns a still-live-but-broken loop into one that
returns and reconnects. Reconnect is the existing `while(!interrupted)` loop in `BotLoop.run()`.

## Tests: **298 → 300, all green**
```
TOTAL tests=300 failures=0 errors=0 skipped=0
BUILD SUCCESS (mvn -B clean test)
```

## Verification status
- **Unit/regression:** green (300/300) — includes exact repro tests for both new defects.
- **Live re-verification:** NOT re-run — the game server (ports 7777/2106) is currently **down**
  and not built in this workspace (`SourceCode/dist/game` has no compiled classes/jars), so a fresh
  single-bot session can't run from here. The prior live run already confirmed the farming fix
  itself; these two patches address the post-respawn / server-death edge cases and are guarded by
  unit tests. Re-run the live verification (Task 0005) once the l2j login+game servers are back up.

---

# 2026-08-16 P1 Follow-up — Interpolated hop Z (avoid artificial geo-wall aborts)

## Problem
`ZoneRouter.buildHops` split a far route into server-acceptable <=4800 u hops but stamped **`destZ`
on every intermediate waypoint** instead of interpolating along the slope. Confirmed live-data case:
a TI char at `z=-3619` routing to the Ruins of Agony farm zone (`centerZ=-3000`, ZoneRecommender)
had its FIRST hop sent with `z=-3000` — 619 u above the ground under that hop's x/y. The server's
`MoveToLocation` handler geo-proofs the *target* cell
(`isCompletelyBlocked(geoX, geoY, _targetZ)`, SourceCode .../clientpackets/MoveToLocation.java:90),
so a fabricated cliff-Z on an early hop can be rejected → the route looks "hop unreachable" →
abandoned + blacklisted even though the route is actually walkable. Same risk on `goal:quest:*`
routes where the NPC z differs from the char z.

## Change (AIPlayerEngine)
| File | Change |
|---|---|
| `phase0/movement/ZoneRouter.java` | `buildHops` now interpolates Z linearly with the same `t = i/n` used for X/Y (`fromZ + round(dz * t)`); the last hop still lands exactly on `destZ`, and flat routes (`dz == 0`) are byte-for-byte unchanged. Added docs pointing at MoveToLocation.java:90. |
| `phase0/movement/ZoneRouterTest.java` | **+1** regression: `slopedRouteInterpolatesZAcrossHopsAndLandsExactlyAtDestZ` — realistic TI→Ruins-of-Agony slope; asserts the first hop Z is partway, monotonic climb toward dest, exact destZ landing, and that flat routes keep the origin Z. |

## Verification
- Unit suite re-run: `mvn -B test` in `AIPlayerEngine` → full suite green (see log
  `/tmp/l24lude-mvn-test.log`).
- Purely additive to the routing rig used by Framework routes (`FleetPlay`/`Phase0Driver`); no hop
  count, cap, or landing-coordinate semantics changed.

## Notes for operators
- Live re-verification (Task 0005) remains pending the login/game servers being back up; the fix
  itself is fully unit-guarded and the previous live run already proved the movement-on path works.
