# TIM-001 — H5 organic-XP now PROVEN with committed raw evidence (post-hoc audit closure)

**Date:** 2026-08-13
**Git:** FleetPlay `EVIDENCE-H5` hooks + `scripts/tim001_h5_airtight.sh` + evidence artifacts
**Supersedes:** the "high confidence but not independently verifiable" caveat in `2026-08-13-tim001-h1-h5-resolved.md`

## Why this was reopened
The earlier H5 claim rested on a DB `exp` delta alone with **no persisted per-kill
server record**. This session closed that gap by running an instrumented,
reproducible farm probe whose **raw outputs are committed** as evidence.

## What we built / changed
1. **FleetPlay evidence hooks** (`examples/FleetPlay.java`, example code, suite
   unaffected): echo every server SystemMessage to the run log
   (`[EVIDENCE-H5] <acct> SYSMSG <id> ...`) and emit a per-kill line whenever the
   server's **StatusUpdate (0x0E STAT_EXP)** EXP for a bot rises
   (`[EVIDENCE-H5] <acct> EXP +N (now M, level=L)`).
2. **`scripts/tim001_h5_airtight.sh`** — self-contained probe: DB BEFORE snapshot →
   launch 5-bot FleetPlay → capture `/telemetry` + `/json` → stop fleet → flush grace →
   DB AFTER snapshot → per-bot EXP events + authoritative `db_diff.txt` → `H5_SUMMARY.txt`.
3. **`scripts/tim001_reposition_fleet.sh`** — move bots to the TI Keltir/Wolf farm
   field `(-82759,250149,-3600)` (zone `gludio32_1725_03`) and heal, so a fresh login
   lands them in real combat terrain.

## Result — H5 PROVEN (3 independent sources agree)
Positive run `Documentation/Evidence/2026-08-13_191903-h5-airtight/` (10 min, all 5 bots on the Keltir/Wolf field):

| Source | Evidence |
|---|---|
| **Run log** `fleet_run.log` | **160 real-time server StatusUpdate EXP-gain events** (33/32/33/31/31 per bot), interleaved with server sysmsgs (`#34` attack, `#43`, `#1983` weak-prot lifted) |
| **Live telemetry** `telemetry_end.txt` | `expGained = 634 / 586 / 592 / 589 / 607` (MoveTelemetry, StatusUpdate-derived) |
| **Requested unavoidable gap — authoritative DB** `db_diff.txt`/`characters_{before,after}.txt` | `exp` 3094→3712 **+618**, 3321→3979 **+658**, 3025→3628 **+603**, 3059→3665 **+606**, 3349→3973 **+624** |

Final DB flush also persisted position: spawn `(-82759,250149,-3600)` → `(-83498,250457,-3596)`.

## Negative control (proves no false positive)
Sibling run `Documentation/Evidence/2026-08-13_190729-h5-airtight/`: bots parked next
to the Talking Island Guard in the town **peace-zone** → exactly **0 EXP events** and a
**+0 DB delta**. The harness cleanly discriminates real organic farming from idle.

## Honest call-outs / H3 status
- **H5: now PROVEN, not merely "single self-reported run".** It is still
  *conditional on spawn placement* — bots repositioned onto the field farm; bots that
  spawn in the town peace-zone do not get XP (=== the negative control). This
  conditional is now explicit and controlled via `tim001_reposition_fleet.sh`.
- **H3 (proactive travel) is explicitly DOWNGRADED, not over-claimed.** This farm run
  exercised H1 (serverMoved ≈ 19–20k u/bot across the run) and per-kill combat, but the
  bots had standing engagement targets so it does **not** re-prove the fleet-wide
  idle→far-travel proactive path. H3 stands as: **demonstrated for 1 of 5 bots only**
  (prior session), confirmed mechanism (`ZoneRouter.hop` routing) — **fleet-wide H3
  remains OPEN / not claimed**. A dedicated idle/no-mob travel probe is a follow-up
  (not run this session).
- **Tooling caveat fixed mid-run:** the probe's immediate AFTER snapshot could lag the
  real persisted EXP (disconnect save lags the 15-min `CharacterDataStoreInterval` —
  the exact original false-negative), and the first `db_diff.txt` compared `level`
  (both 5) instead of `exp`. Both corrected; the authoritative refreshed diff is
  committed in the evidence dir and the script now re-snapshots after a flush grace.