# scripts/_probes — one-shot historical proof/verification harnesses

> Quarantined here on 2026-08-22 (F-09 scripts hygiene) so the live ops tools at the
> `scripts/` root stay obvious. These are **not** part of the live fleet/watch/ops path —
> they were one-shot live-proof harnesses (b3–b10 "prove" scripts, c5/c7 live proofs,
> tim001_* movement reviews, combat/npc/pvp live tests, cold-start orientation test).
> Keep them for traceability (project rule: never delete); run manually if needed.
> Active tooling moved to / stays at `scripts/`: `fleet_launch.sh`, `health_check.sh`,
> `rotate_logs.sh`, `keep_alive.sh`, `backup_db.sh`, `watch_fleet.*`, `gate.sh`,
> `check_style.sh`, `session_start.sh`/`session_end.sh`, `real_status.sh`, provisioning.

## Contents

```
b3_enter_world_prove.sh   b8_move_prove.sh
b3_login_probe.sh         b9_chat_prove.sh
b4_combat_prove.sh        b10_party_prove.sh
b5_pvp_prove.sh           c5_live_combat_proof.sh
b6_quest_prove.sh         c7_live_quest_proof.sh
b7_trade_prove.sh         test_combat_live.sh
tim001_h5_airtight.sh     test_npc_engagement.sh
tim001_move_probe.sh      test_pvp_combat.sh
tim001_reposition_fleet.sh
cold_start_test.sh        (pre-2026-08-22 orientation test, references archived docs)
```

Note: some may still carry stale `/home/volodro/...` repo paths from the old era — if you
revive one, prepend the same repo-root resolution `check_style.sh` / `gate.sh` now use.
The Java classes these harnesses launched moved to `AIPlayerEngine/attic/examples/` (F-10,
2026-08-22) — run these only after resurrecting the class (`git mv attic/examples/X.java
src/main/java/com/aiplayer/examples/X.java` + uncomment the `package` line).