# Session In Progress (rate-limit-safe)
Started 2026-08-03 · Goal: **B2 — implement the real L2J RequestAuthLogin protocol (RSA/blowfish) so a live login passes**
Last updated: 2026-08-03

## Checklist (idempotent; refined as the protocol is understood)
- [x] B2.1 AUDIT-FIRST: read 01-commons + 04-gameserver-network + real loginserver packet/crypt source
- [x] B2.2 Document handshake → Documentation/Audit/31-login-protocol-handshake.md (Init→AuthGameGuard→RequestAuthLogin→LoginOk→ServerList→PlayOk→GS)
- [x] B2.3/4 Crypto helper LoginCrypt.java written (unscramble, RSA-encrypt, blowfish, checksum, XOR) — needs compile
- [x] B2.5 Rewrite L2JProtocol.connectAndLogin to use LoginCrypt (real handshake) ← DONE
- [x] B2.6 mvn compile (BUILD SUCCESS, exit 0)
- [x] B2.7 RuntimeLog (2026-08-03-b2-login-handshake.md) + STATUS; fold scratchpad + commit — COMPLETE
- [ ] (B3) live-connect probe → online=1 (framing verification + GS enter-world)

## Current step
Reading audit docs + locating/reading real loginserver packet & crypt source.

## If resuming
Do the first unchecked item; WIP-commit after each; keep steps idempotent. This task is large — resume from the checklist; read START_HERE first.
