# Session In Progress (rate-limit-safe)
Started 2026-08-03 · Goal: **B3 — live-connect probe: run connectAndLogin vs LoginServer :2106 → LoginOk → online=1**
Last updated: 2026-08-03

## Checklist (idempotent)
- [x] B3.1 Create LoginProbe + RawInitProbe; locate login log; :2106 up
- [x] B3.2 Probe connected live; **Init decrypted with static blowfish key + reverse XOR → opcode 0x00 recovered** (decryption scheme CONFIRMED)
- [ ] B3.3 Fix reverseXOR alignment in `connectAndLogin` (server encXORPass offset/size) → fully decode Init (sessionId/protoRev/GG) ← IN PROGRESS
- [ ] B3.4 Then AuthGameGuard (static key) → RequestAuthLogin (RSA+session key) → LoginOk
- [ ] B3.5 Verify via LoginServer log + DB online=1
- [ ] B3.6 RuntimeLog + STATUS; fold scratchpad + commit (when B3 complete)

## Current step
Decrypt Init fully — need server's encXORPass offset/size (read Client.writePacket/WriteHandler + LoginEncryption.encrypt offsets).

## Current step
Building the probe + locating the login log.

## Notes
- Careful: LoginServer bans IP after ~5 failed logins (temporary). Iterate deliberately.
- Account to use: ai_combat_01 / ai123pass (char exists, password now hashed correctly per B1).

## If resuming
Do the first unchecked item; WIP-commit after each.
