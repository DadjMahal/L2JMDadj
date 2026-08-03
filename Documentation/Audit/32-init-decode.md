# 32 — Live Init decode: root cause of the B3 crypto blocker (SOLVED 2026-08-03)

> Phase 0 of the B3 unblock. This doc records the DECISIVE finding: the L2JMobius
> **LoginServer uses a *little-endian-byte-order* Blowfish that is NOT byte-compatible with
> JDK `Blowfish/ECB/NoPadding`** (which is big-endian). This is why the static-key + XOR
> hypothesis (Audit/31) "never decoded" — it was never a framing/XOR problem, it was a
> Blowfish byte-order mismatch. Fix = port the server's exact engine into the AIPlayerEngine
> (external, zero server source changes).

## Root cause (verified empirically, not assumed)
- Server `LoginEncryption.encrypt` sends the first packet (Init) encrypted with
  `STATIC_BLOWFISH_KEY` via `encXORPass` then `STATIC_CRYPT.crypt(...)` (confirmed from both
  `SourceCode` and the running `ServerBuild/libs/LoginServer.jar`, built 2026-07-29).
- The server's `BlowfishEngine` (BouncyCastle-derived, same S-boxes) reads the 8-byte block as
  **little-endian** ints (`ArrayPacketBuffer.readInt` = `b0|b1<<8|b2<<16|b3<<24`) and writes back
  little-endian (`bits32ToBytes`). This is a little-endian-byte-order Blowfish.
- **JDK `Blowfish/ECB/NoPadding` is big-endian.** Encrypting an all-zero 8-byte block with the
  static key gives:
  - server engine: `46d6a19b80854746`
  - JDK engine:   `9ba1d64646478580`
  They differ, and neither decrypts the other's output. (Verified in `CompareEngine` harness and
  `BlowfishLeParityTest`.)

## The fix (external middleware — no L2JM server source changed)
- Ported the server's `loginserver/crypt/BlowfishEngine.java` (1267 lines) verbatim into the
  engine at `AIPlayerEngine/src/main/java/com/aiplayer/protocol/crypt/BlowfishEngine.java`
  (package `com.aiplayer.protocol.crypt`), replacing the `Buffer` I/O with little-endian `byte[]`
  I/O (`leReadInt`, `bits32ToBytes`). Byte-for-byte parity confirmed.
- `LoginCrypt.blowfishEncrypt/blowfishDecrypt` now iterate the ported engine in ECB over the whole
  array, instead of JDK `Blowfish/ECB/NoPadding`. (`LoginCrypt.rsaEncrypt` remains JDK — RSA is
  independently byte-order-neutral/standard.)

## Live verification (real wire capture, 2026-08-03)
`InitDecodeProbe` connects to :2106, reads the exact frame via the 2-byte LE size header
(194 bytes; 192-byte aligned payload), then `blowfishDecrypt(STATIC_KEY)` → `reverseXORPass(0,192)`.
Result — FULL MATCH:
- opcode   = `0x00`
- sessionId= parsed (varies per connection)
- protoRev = `0x0000c621` (magic confirmed at payload offset 5..8)
- GG       = `4e95dd29 fc9cc377 20b6ad97 f7e0bd07` == LE of `0x29DD954E 0x77C39CFC 0x97ADB620 0x07BDE0F7` ✓
- blowfishKey = per-session key parsed from the Init tail.

The RSA unscramble (Audit/31), AuthGameGuard, RequestAuthLogin, LoginOk/PlayOk steps are ready to
advance with this corrected Blowfish (Phase 1).

## Regression protection
`BlowfishLeParityTest` (JUnit 5) pins the server reference vector (`46d6a19b80854746`), round-trip,
the JDK-vs-server difference, and the multiple-of-8 guard.

## Commands
```
# run the live decode probe
cd /home/volodro/L2JM/AIPlayerEngine
javac -cp target/classes -d target/classes \
  src/main/java/com/aiplayer/protocol/crypt/BlowfishEngine.java \
  src/main/java/com/aiplayer/protocol/LoginCrypt.java \
  src/main/java/com/aiplayer/examples/InitDecodeProbe.java
java -cp target/classes com.aiplayer.examples.InitDecodeProbe 127.0.0.1 2106

# regression tests
mvn -q -Dtest=BlowfishLeParityTest test
```
