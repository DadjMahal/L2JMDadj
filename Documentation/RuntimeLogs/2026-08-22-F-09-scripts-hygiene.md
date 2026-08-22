# RuntimeLog — 2026-08-22 F-09 scripts hygiene

**Task:** F-09 — quarantine one-shot historical probes; live ops stay at the `scripts/` root.

## What changed
1. Created `scripts/_probes/` and `git mv`'d **18 one-shot harnesses** (history preserved):
   - b3–b10 prove scripts (b3_enter_world_prove, b3_login_probe, b4_combat_prove,
     b5_pvp_prove, b6_quest_prove, b7_trade_prove, b8_move_prove, b9_chat_prove,
     b10_party_prove)
   - c5_live_combat_proof, c7_live_quest_proof
   - tim001_h5_airtight, tim001_move_probe, tim001_reposition_fleet
   - test_combat_live, test_npc_engagement, test_pvp_combat, cold_start_test
2. **`scripts/_probes/README.md`** — documents the set, why they're quarantined, and that
   live ops remain at root (fleet_launch, health_check, rotate_logs, keep_alive, backup_db,
   watch_fleet.*, gate, check_style, session_start/end, real_status, provisioning).
3. Updated the **Javadoc/comment path refs** in 4 Java files so they still point at the
   moved scripts: `CombatProbe` (b4), `MoveProbe` (×2: b8), `QuestProbe` (b6),
   `QuestDialogDriver` (c7) → `scripts/_probes/...`.

## Why these and not others
- Audit 02 §26 names "b3–b10, tim001_*, c5/c7" as the one-shot historical pile.
- Dry-run grep confirmed **no live script calls any probe** (all refs were doc-only).
- `cold_start_test.sh` references archived docs (AGENT_ONBOARDING.md) + a stale
  `/home/volodro/...` path → same quarantine class.
- Live ops/utilities stay at root: analyze/collect/baseline/count/position_crosscheck,
  provision*, watch_fleet*, e2e_dashboard, build_dashboard, gate/check_style/style_sweep.

## Verification
```bash
scripts/gate.sh   # exit 0 — 415 tests green, style 0 violations, no hardcoded secrets
git status        # 18 renames (tracked as R), no external references left in live code
```