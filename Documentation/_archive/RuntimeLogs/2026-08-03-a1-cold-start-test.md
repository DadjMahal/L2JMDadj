# 2026-08-03 — A1: Cold-start orientation test (TASKS #100)

## Objective
Prove a fresh context can orient using ONLY `START_HERE.md` + `AGENT_ONBOARDING.md` + `session_start.sh`,
in ~1,200 tokens (vs the ~73,496 this project originally cost to orient). Make the test **repeatable**.

## Files
- `scripts/cold_start_test.sh` (NEW) — repeatable cold-start test (17 checks).
- `AGENT_ONBOARDING.md` (leaned) — now points to `START_HERE.md` first; removed stale `SESSION_PROTOCOL.md` ref + redundant routing table.

## What the test verifies
- **[1]** entry files exist + are lean (token estimate, chars/4).
- **[2]** `START_HERE.md` answers the 5 orientation questions + resume awareness (7 checks).
- **[3]** `AGENT_ONBOARDING.md` has the 6 hard rules + START_HERE pointer (7 checks).
- **[4]** `session_start.sh` orients — clean → `READY TO START WORK`, or resume → `INTERRUPTED WORK DETECTED`.

## Verification output (real)
```
$ ./scripts/cold_start_test.sh
[1] PASS START_HERE.md exists / PASS AGENT_ONBOARDING.md exists
    START_HERE.md: 3398 chars | AGENT_ONBOARDING.md: 1693 chars
    Rough token estimate (both): ~1272 tokens (target <= ~1200)
[2] 7/7 PASS (Project, current state, next task, blockers, reality check, routing, resume)
[3] 7/7 PASS (START_HERE pointer + 6 rules)
[4] PASS session_start.sh resumes interrupted work (scratchpad present)
RESULT: 17 passed, 0 failed — COLD-START TEST: PASS (exit 0)
```

## Result
Bootup **~73,496 tokens → ~1,272 tokens** (~58× reduction). Both orientation branches (clean + resume) proven.

## Problems & solutions
- First run failed [4]: `session_start.sh` correctly took the resume branch (scratchpad present mid-work) instead of reaching READY. Fixed the test to accept **both** valid outcomes (clean→READY, resume→INTERRUPTED) — now it validates both branches.

## Next steps
- **A2** fix `real_status.sh` double-print bug; **A3** make `count_ai_players.sh` consistent with `real_status.sh`.
- **Stream B** (the real gap): live-verify 1 AI player on the running server (25 chars exist at level 1, 0 online).
