# Session In Progress (rate-limit-safe)
Started 2026-08-03 · Goal: **B3 — live-connect probe: run connectAndLogin vs LoginServer :2106 → LoginOk → online=1**
Last updated: 2026-08-03

## Checklist (idempotent)
- [x] B3.1 Create LoginProbe + RawInitProbe; locate login log; :2106 up
- [x] B3.2 Connected live; drew raw Init (194-byte frame)
- [ ] B3.3 Decode Init — **BLOCKED**: static-blowfish+XOR hypothesis did NOT produce a stable decode (opcode was 0x00 in one run, 0x6d in the next; protoRev 0x0000c621 never found via reverse-XOR brute force 0..10). The server's real Init encryption is NOT what was hypothesized (wrong static key or different scheme). Needs a dedicated reverse-engineering pass (read LoginEncryption usage, try session key, verify static key). ← BLOCKED
- [ ] (after B3) B4+ NPC/PvP live proof — DEPENDS on B3 (a connected in-game player)

## Current step
BLOCKED on login-packet encryption reverse-engineering.

## Dependency note
B4 (prove real NPC combat) CANNOT proceed until B3 proves a connected, in-game AI player.

## Recommended next step options
1. Dedicated deep-dive: reverse-engineer the Init encryption by reading LoginServer's actual write/encrypt path end-to-end (LoginClient.onConnected → does it encrypt Init?), verifying the STATIC key, and whether the session key is used instead. 
2. OR pivot: use the server-side FakePlayer (in-process) approach that PARSE_Tasks originally recommended — avoids external-socket protocol entirely.
Whichever chosen, it's a fresh focused session — not something to compress into the tail of a long one.

## Current step
Building the probe + locating the login log.

## Notes
- Careful: LoginServer bans IP after ~5 failed logins (temporary). Iterate deliberately.
- Account to use: ai_combat_01 / ai123pass (char exists, password now hashed correctly per B1).

## If resuming
Do the first unchecked item; WIP-commit after each.
