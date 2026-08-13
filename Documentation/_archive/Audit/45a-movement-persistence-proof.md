# Audit 45a - TIM-001 hop-by-hop persistence proof (procedure + expected evidence)

Date: 2026-08-12 . Lane: task_0017 . Ships tooling; live run to be executed on the box.

## Run procedure
1. Ensure LoginServer (2106) + GameServer (7777) are up; engine built.
2. bash scripts/tim001_move_probe.sh /home/dadj/Projects/l24lude 3
   (FleetPlay launched with phase0.movement forced ON; default remains OFF in properties.)
3. The script snapshots DB BEFORE, runs 3 min, curls /telemetry + /json, snapshots AFTER,
   prints the NEW hop-by-hop verdict block.

## Expected evidence lines (paste into TASKS.md TIM-001)
- For each bot: HOP-PROOF movesSent / serverMoved / degraded. Success looks like
  movesSent N, serverMoved close to N, degraded small (near 0), and multiple short hops ~4800u
  instead of one 21391u hop.
- DB-DELTA VERDICT: nonzero x/y/z (and/or exp) delta BEFORE-vs-AFTER across the hop sequence
  proves hop-by-hop persistence (H1). If serverMoved stays near 0 despite movesSent, hops are
  still being dropped server-side and TIM-001 stays open.

## Short-hops vs far-hop
- <=4800u hops are within the 9900u cap (MoveToLocation.java:156-159) and DO persist (Audit/44 H1
  proved +400u). The verdict depends on seeing per-hop server ack deltas accumulate.
