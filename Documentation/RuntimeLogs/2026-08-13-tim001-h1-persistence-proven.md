# RuntimeLog — 2026-08-13 TIM-001 evidence run #3 (H1 persistence PROVEN with a real DB delta)

Date: 2026-08-13 · Lane: lead (Cline) · Type: live evidence run (positive, paste-worthy).

## What was fixed since run #2
1. **`ZoneRouter.isRouteStuck()` / `MAX_HOP_TIMEOUTS=2` (stuck-hop recovery)** — `FleetPlay`'s hop
   loop was stalling on a single unreachable waypoint: a hop was sent once and only re-sent on a 45s
   timeout, so a geo-blocked/unwalked waypoint produced the "movesSent=2 in 2 min" throttling from run #2.
   Now a hop that the server never walks the char toward is abandoned after 2 consecutive timeouts and the
   route is re-planned (no more indefinite 45s stall).
2. **`Phase0Wiring.moveTo()` now emits the standard mouse-click move** (`moveType=1`) for proactive
   travel instead of the cursor-key path (`moveType=0`). The Interlude `MoveToLocation` handler branches
   on this: `1` is the plain "walk to point" path every real client uses and ends with the server's
   `onActionRequest()`; `0` is a special keyboard-movement path.
3. **Probe measurement fix (`scripts/tim001_move_probe.sh`)** — the AFTER DB snapshot was taken BEFORE
   the fleet was stopped, while bots were still connected and no save had fired → it compared a stale
   pre-run row (the "DB identical before/after" false-negative). Now the probe stops the fleet first,
   waits `DB_FLUSH_SEC` (12s) for the server's asynchronous **on-disconnect save**, THEN snapshots AFTER.
   (Server `CharacterDataStoreInterval = 15` min — a short run only persists via the disconnect save.)
4. Engine rebuilt on JDK25; **full suite 219/219 green** (ZoneRouterTest +1 stuck-hop case).

## Honest outcome (paste-worthy evidence, `bash scripts/tim001_move_probe.sh <engine> 1`)
- MoveTelemetry `/telemetry` (FleetPlay, 5 bots, movement FORCED ON, 1-min run):
  - `ai_combat_04: movesSent=2, degraded<500u=0, samples=172, [EVIDENCE-H1] serverMoved=4569 u`
    `[EVIDENCE-H2] degenerateDestinations=0/2`
  - `ai_combat_01/02/03/05: movesSent=0, serverMoved=0 u` (these 4 were mid-combat vs Orc Fighters the
    whole short run — never idle long enough to route; H3 travel fires when a bot is genuinely idle).
  - Fleet log `USER_INFO ... pos=(-107198,246865,-3600)` exactly matches a planned far-point HOP — the
    bot physically arrived at its **first 4800u-acceptable hop**, then the next one when ack'd.
- `gameserver.characters` (disconnect-save flushed AFTER snapshot):
  - **BEFORE** `CombatBot_04: exp=1400000 x=-109393 y=245900 z=-3600`
  - **AFTER**  `CombatBot_04: exp=1400000 x=-116158 y=242929 z=-3600`
  - → **nonzero persisted DB delta ≈ 7390u** (Δx −6765, Δy −2971). H1 persistence is PROVEN.

## Verdict
- **H1 server-move persistence: PROVEN** — the ≤4800u ack-gated hop sequence now moves the char
  in-world (`serverMoved=4569u`, `USER_INFO` pos matching hop targets) AND persists to
  `gameserver.characters` once the disconnect-save is allowed to flush. Run #2's "identical before/after"
  was a probe timing bug, not a movement failure.
- **H2:** clean (0 degenerate destinations).
- **H3 proactive travel:** shown for an idle bot (`serverMoved>0` for ai_combat_04); the other four
  stayed in combat for the whole 1-min window (expected fighter behavior, not a travel defect).
- **H5 organic XP:** still not shown (`expGained=0`) — a 1-min run with bots grinding has not produced a
  measurable organic exp increase yet. **H5 remains open**, out of scope for this hop-persistence lane.

## Remaining for TIM-001 DONE (do not fabricate)
- **H5 organic XP** — needs a run where a bot actually lands kills (verify the mobs grant XP and the exp
  delta appears in `/telemetry`), or a dedicated XP probe.
- Optionally: widen travel so more than one bot is idle-routeable (e.g. route after a short combat lull),
  to strengthen the H3 fleet-wide picture.