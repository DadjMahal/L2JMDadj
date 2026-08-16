# STEP 5 live verify — Fleet-wide despawned-target lifecycle (4/5 stall recovery)

> **Date:** 2026-08-16 · **Board:** STEP 5 (claimed `IN_PROGRESS`` `3d97fe53`)
> **Goal (board follow-up from STEP 3, `TASKS.md` line 78):** after a target `DeleteObject`
> (despawn), bots must drop it and re-acquire a farmable target instead of stalling at spawn —
> "fleet-wide farming consistency" across all 5 bots.
> **Build under test:** `06906195` (P0/P1/P2 farming fix set) + `b558d4f6` (P2 doc), no engine
> changes during this run.

---

## Run configuration
- Server stack (from P2): JDK25 Login `:2106` + Game `:7777` (pids 18332/21369), MySQL up.
- Single-bot FleetPlay session stopped first (pid 10714 killed) to free accounts/dashboard port.
- Fleet: `FleetPlay 5 127.0.0.1 7777 2106 8200 movement` (phase0.movement FORCED ON),
  log `/tmp/fleet5.log`, dash `:8200`, per-30s watch `/tmp/fleet5-watch.log`.
- Pre-run DB exp (charId 100000..100004): `14941 4438 3844 3665 4170` (pre-autosave snapshot;
  DB refreshes on the 15-min `CharacterDataStoreInterval` tick or logout, as verified in P2).

---

## Live evidence (mid-run ~06-07 min in)

Per-30s watch rows (live `/api/players` exp + action, server-acked position) + telemetry totals
(the full series is in the watch log; excerpt below):

| bot | exp start → mid | Δ | state (actions seen) |
|---|---|---|---|
| ai_combat_01 (L7) | 14941 → 15034 | +93 | ATTACK/LEAVE_COMBAT, positions moving 500+ u |
| ai_combat_02 (L5) | 4438 → 4829 | +391 | ATTACK/LEAVE_COMBAT, positions moving |
| ai_combat_03 (L5) | 3844 → 3973 | +129 | ATTACK, positions moving |
| ai_combat_04 (L5) | 3660 → 4149 | +489 | ATTACK/LEAVE_COMBAT, positions moving |
| ai_combat_05 (L5) | 4170 → 4420 | +250 | ATTACK, positions moving |

- **44 `[EVIDENCE-H5] EXP +…` per-kill XP-gain events** (packet-level StatusUpdate receipts) in the
  first ~6 min; per-bot spread 8/8/9/8/11 — **no bot at 0**.
- **46 `RE_TARGET: old=… new=…`** transitions — bots hopping off despawned mobs to the next
  hostile (the exact lifecycle that previously stalled).
- **serverMoved deltas** for all 5 in an early 60s window: 4245 / 1702 / 554 / 4105 / 1908 u.
- Zero server Exception/Caused-by lines.

---

## Stale-target watchdog behavior (by design, not a stall)

`ai_combat_01` produced the only 2 `STALE-TARGET: abandoning …` lines (objId 268461916 / 268461909)
right after the target gave no XP within the 15 000 ms budget — each was followed by a fresh
engagement and the bot's EXP kept rising (14941 → 15034). This is the watchdog from the STEP 3 fix
set working as designed (abandon an un-advancing target, re-acquire), NOT the 4/5 zero-farm stall
this STEP set out to eliminate.

---

## FAR-farm verdict (1h50m soak + T+200s / T+390s snapshots + tight 15-min watch)

### Cumulative totals (full run, `/tmp/fleet5.log`, uptime 6613 s ≈ 1h50m at close)
| metric | value |
|---|---|
| `[EVIDENCE-H5] EXP +…` kill receipts | **1468** |
| `RE_TARGET:` despawn-hop transitions | **1606** |
| server `Exception`/`Caused-by`/`SEVERE` lines | **0** |

### Per-bot EXP / XP-event count / far-travel behavior (live `/api/players` at 1h50m)
| bot | lvl | live EXP | XP events (run) | far-travel route abandons | HOP-to-dest sends | live `movedLast60` |
|---|---|---|---|---|---|---|
| ai_combat_01 | 7 | 17880 | 187 | 32 | 119 | **648 (farming)** |
| ai_combat_02 | 8 | 27647 | 271 | 44 | 192 | **0 — FROZEN** |
| ai_combat_03 | 8 | 25191 | 373 | 41 | 163 | **2654 (farming)** |
| ai_combat_04 | 8 | 27918 | 277 | 42 | 186 | **0 — FROZEN** |
| ai_combat_05 | 8 | 23343 | 360 | 37 | 169 | **2611 (farming)** |

Pre-run → live EXP: 01 `14941→17880`, 02 `4438→27647`, 03 `3844→25191`, 04 `3665→27918`,
05 `4170→23343` — all 5 gained substantial, persisted XP (DB flush confirms `17266/27647/23460/27918/21871`),
02/03/04/05 reached level 8.

### STEP 5 target — the despawned-target (`DeleteObject`) lifecycle — VERIFIED FIXED
- **1606 `RE_TARGET: old=… new=…`** transitions: every bot immediately hops off a despawned mob and
  re-acquires a fresh hostile. This is *exactly* the lifecycle that hard-stalled the fleet at STEP 3.
- Stale-target watchdog (`STALE_TARGET_BUDGET_MS=15000`) fires only when a target shows no XP within budget,
  then re-arms a fresh engagement — **no bot is stuck attacking a corpse** (the 4/5 STALE-chase corpse that
  STEP 3 left behind is gone).
- **0 server exceptions** over ~110 min. **1468 organic kill-XP receipts**; tight-watch shows 01/03/05 with
  `movedLast60` 543–3132 and changing targets each 20 s poll — sustained, consistent farming.
- Primary deliverable (kill the despawned-target corpse-chase / 0-farm hard stop) is met.

### Residual defect (parent goal "fleet-wide consistency" NOT met) — NEW root, tracked as STEP 6
At ~1h50m, **ai_combat_02 and ai_combat_04 are permanently frozen**: `action=AUTO_PLAY`, `target=null`,
`movedLast60=0`, position frozen at `(-95544,246398)` / `(-97264,244673)` — unchanged across the entire
15-min tight watch. Decisive evidence of the actual mechanism:
- Client **keeps** sending idle-relocation hops: concurrent, time-stamped `HOP -> far-point …` log lines for
  02/04 while `movesSent` slowly climbs (02 `823→824`, 04 `938→939`) — yet `movedLast60` (server-acked movement)
  stays **0** and the position does not change.
- Every far-point route is then logged `hop unreachable after 2 timeouts -> abandoning route` (196 total across
  the fleet; 44 / 42 for 02/04) and re-planned — a self-sustaining `HOP → slow abandon → re-plan` churn with
  **zero server-side displacement**.
- `ZoneRouter`/`buildHops` split is correct (each hop ≤ `MAX_HOP_DIST=4800`, under the server's ~9900 single-move
  cap; the far-points here were single ~4.5 k hops). So the freeze is **not** a too-long-hop bug.
- Distinguishing factor: 02/04 found themselves with **no hostile in engage range** and in a spot where idle
  `MoveToLocation` far-travel does **not persist server movement**; 01/03/05 farm because they still drive
  combat/AttackRequest + chase movement. This is an **idle-relocation empty-zone dead-end**, distinct from the
  despawned-target lifecycle this STEP verified.

### Verdict
1. **Despawned-target (`DeleteObject`) lifecycle: FIXED / verified.** No bot chases a corpse; the fleet no longer
   collectively hard-stops on 0-farm; 3/5 farm consistently and all 5 accumulated large, persisted XP.
2. **Fleet-wide farming consistency: NOT met — blocked by a NEW, separate root cause** (idle far-relocation that
   doesn't persist server movement → an out-of-hostile bot freezes in place). Required as **STEP 6** below.

---
## Follow-up → STEP 6 (new task, prefigured by this STEP-5 soak)
- **Defect:** a bot that exhausts local hostiles cannot self-relocate — idle far-travel hops are sent but the
  server never moves the char (`movedLast60=0`), it times out, abandons, re-plans, and loops frozen in place.
- **Candidate remedy (validate live):** when idle relocation shows zero server movement, route back toward the
  bot's **last XP-earning position** (guaranteed-farmable anchor) or toward the **nearest fleet mate's position**
  instead of a random far point; add a consecutive-abandon escape gate so relocation targets shrink toward reachable
  ground before giving up. Keep the despawned-target lifecycle fixes in place.
- **Acceptance:** a 5-bot `FleetPlay 5 … movement` soak with NONE of the 5 frozen at `movedLast60=0` for >60 s
  by the T+20 min mark.