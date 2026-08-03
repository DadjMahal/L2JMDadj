# 2026-08-02 — Documentation Gap Fix + Rate-limit-safe Sessions

## Objective
Fix the documentation gap that cost a fresh session ~73k tokens just to orient (fragmented,
contradictory, partly-fabricated task/status docs). Establish ONE source of truth + make
sessions rate-limit-safe so the next context orients in ~800 tokens.

## Files created
- `START_HERE.md` — single entry point (honest state + routing table + reality-check).
- `Documentation/WORKFLOW.md` — merged SESSION_PROTOCOL + MULTI_AGENT_RULES + WORKFLOW_RULES.
- `AIPlayerEngine/README.md` — truthful engine readme (replaces fabricated ones).
- `Documentation/_archive_fabricated/_ARCHIVE_INDEX.md` — index of 7 quarantined fabricated docs.
- `Documentation/_archive_superseded/_ARCHIVE_INDEX.md` — index of 6 superseded docs.
- `SESSION_IN_PROGRESS.md` — rate-limit-safe scratchpad (folded into this handoff at end).

## Files modified
- `TASKS.md` — fixed the 59–63 collision (renumbered to 103 unique tasks), added an Evidence
  column, downgraded tasks 54 & 63 (fake `assertTrue(true)` tests) to `in_progress`.
- `STATUS.md` — slimmed to a terse live snapshot mirroring START_HERE.
- `REQUIREMENTS.md` — trimmed; defers to START_HERE/WORKFLOW; removed the "read all docs" mandate.
- `scripts/session_start.sh` — resume-aware (detects the scratchpad first, prints resume state).
- `scripts/session_end.sh` — non-interactive; WIP-capable; folds the scratchpad on clean end.

## Problems & solutions
1. Six competing "read-first" files → ONE `START_HERE.md`.
2. Duplicate task numbers 59–63 (Part 3 vs Part 4) → Part 4 renumbered to start at 64 (103 unique).
3. Fake "✅ COMPLETE" claims contradicting `ai_progress_report.txt` → quarantined + honest README.
4. Fake tests (`assertTrue(true)`) → tasks 54 & 63 downgraded to `in_progress`.
5. Rate-limit risk mid-work → `SESSION_IN_PROGRESS.md` scratchpad + WIP commit per phase + resume-aware `session_start.sh`.

## Verification output (real)
```
$ mvn -q compile  (AIPlayerEngine)   → BUILD SUCCESS (only guava/Maven Unsafe warnings, not our code)
$ AIPlayerEngine/AIStatusLogs/real_status.sh:
    AI players online: 0
    Combat/Quest/Trade/LevelUp/Chat actions: 0   (confirms "mock, not live" — fabricated docs were false)
    LoginServer (2106): LISTENING YES
    GameServer (7777): LISTENING YES
```

## Remaining issues
- The real gap: AI is compile + unit-test only, NOT live-verified. Next milestone = actually connect AI players and prove combat/quest/trade against the running server (tasks 59–63 need real live proof).
- `real_status.sh` prints each activity count twice ("0\n0") — minor pre-existing cosmetic bug (`grep -c` returns 0 then `|| echo 0`). Low priority.
- ~100 advanced/social/economy/neural classes are unwired stubs; audit tasks 70–75 (Part 4) cover them.

## Next steps
1. Close the live-verification gap: run `AIPlayerEngine --spawn-all` against the live server and capture real combat/trade in `real_status.sh` (tasks 59–63, currently "done" only on unit tests).
2. Then proceed to Part 4 goals (task 64+).
3. Every future multi-step task: use the `SESSION_IN_PROGRESS.md` scratchpad + WIP-commit pattern.
