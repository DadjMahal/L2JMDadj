# 2026-08-03 — B2: real L2J login handshake (RSA/blowfish) implemented

## Objective
Replace the fake login stub with the real L2J Interlude login-server handshake so an AI player can authenticate as a real client.

## Deliverables
1. `Documentation/Audit/31-login-protocol-handshake.md` — authoritative reverse-engineered login-protocol spec (Init layout, RSA 1024/F4, static+session blowfish keys, opcodes, packet framing).
2. `protocol/LoginCrypt.java` (NEW) — JDK-based crypto: `unscrambleModulus`, `buildPublicKey` (RSA 1024, exp F4=65537), `rsaEncrypt` (RSA/ECB/NoPadding), `blowfishEncrypt/Decrypt`, `appendChecksum`/`verifyChecksum`, `encXORPass`, `buildAuthBlock` (user@0x5E, pass@0x6C).
3. `protocol/L2JProtocol.java` (REWRITTEN) — real handshake: parse Init → unscramble RSA modulus → AuthGameGuard (0x07, static blowfish key + XOR) → RequestAuthLogin (0x00, RSA-encrypted creds + session key + checksum) → parse LoginOk(0x03)/ServerList(0x04). Restored sendMove/sendAttack/sendChat used by AIPlayer etc.

## Verification
```
$ mvn -q compile  → BUILD SUCCESS (exit 0)
```

## Honest status (verify-before-claim)
- ✅ Protocol reverse-engineered and documented (Audit/31).
- ✅ Crypto primitives + handshake code written and compile clean.
- ⚠️ **NOT live-verified.** Whether the exact wire framing (size prefix = encrypted block size; server→client blowfish decrypt) authenticates against the running LoginServer (:2106) is UNPROVEN. Login packet framing has byte-level subtleties needing a live round-trip. That's **B3**.
- Do NOT claim "auth works" until B3 proves a live online login.

## Key protocol facts
- RSA 1024-bit, exponent F4=65537, `RSA/ECB/NoPadding`; 128-byte auth block: user@0x5E(14), pass@0x6C(16).
- First client packet = STATIC blowfish key `{0x6b,0x60,0xcb,0x5b,0x82,0xce,0x90,0xb1,0xcc,0x2b,0x6c,0x55,0x6c,0x6c,0x6c,0x6c}` + XOR pass.
- Later client packets = session blowfish key (from Init) + checksum.
- Opcodes C→S: AuthGameGuard=0x07, RequestAuthLogin=0x00, RequestServerLogin=0x02, RequestServerList=0x05.

## Next steps
- **B3**: live-connect probe — run `connectAndLogin` against :2106; iterate on framing (size header, decrypt) until LoginOk; confirm via server log + DB `online=1`; then game-server enter-world.
