# 📊 Status — L2JM

> Single live snapshot. Mirrored by `START_HERE.md`. Overwritten every session.
> If `SESSION_IN_PROGRESS.md` exists at repo root, resume it (rate-limited mid-work).

## Phase: 2 — Combat AI (scaffolding done; **live verification is the real gap**)
## Last completed: B2 — real L2J login handshake (LoginCrypt + L2JProtocol) compiles; B3 live-login BLOCKED (see SESSION_HANDOFF.md)
## Next task: **B3** — live-login proof (connect 1 AI player, online=1). BLOCKED on Init-decrypt crypto. B4–B10 gated on B3.
## Blockers: B3 live-login crypto (custom BlowfishEngine vs JDK); B4–B10 depend on B3; fake-test tasks 54/63 (Stream C); ~145 unwired stub classes (Stream G).

## Honest state (source: ai_progress_report.txt + real_status.sh)
Engine compiles (155 files); Combat/Quest/Merchant/Social AI use **mock data**, not connected to real gameplay.
25 AI chars exist in DB at level 1, 0 online. Server UP (LoginServer :2106, GameServer :7777).
Bootup cost: ~73k → ~1,272 tokens (cold-start test PASS).

## Recent RuntimeLogs (most recent first)
- 2026-08-03-a1-cold-start-test.md
- 2026-08-02-doc-gap-fix.md
- 2026-08-02-task63-pvp-enhancements.md
