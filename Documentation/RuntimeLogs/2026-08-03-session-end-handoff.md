# Session End Handoff

# Session In Progress (rate-limit-safe)
Started 2026-08-03 · Goal: **B1 — verify AI account passwords/auth so connectToServer can authenticate**
Last updated: 2026-08-03

## Checklist (idempotent)
- [x] B1.1 Investigate: account/password model; hashing algo; accounts table location ← DONE
- [x] B1.2 Determine gap — accounts had PLAINTEXT pw (login server needs Base64(SHA1)); connectPlayer double-prefix bug
- [x] B1.3 Establish correct credentials — DB pw → Base64(SHA1('ai123pass')) for 25 accounts; fixed connectPlayer account-name bug; build OK
- [x] B1.4 RuntimeLog (2026-08-03-b1-ai-account-auth.md) + STATUS
- [x] B1.5 Fold scratchpad + commit — COMPLETE

## Current step
Reading AIPlayerManager/AIPlayer + querying accounts table + finding login hashing algo.

## If resuming
Do the first unchecked item; WIP-commit after each; keep steps idempotent.
