# 31 — L2J Interlude Login Protocol Handshake (reverse-engineered from source)

> Authoritative spec for the AIPlayerEngine to authenticate as a real client against the L2JMobius
> **LoginServer** (:2106). Captured 2026-08-03 from `loginserver/` source (B2 investigation).
> This is the missing detail the audit docs (01-commons / 04-gameserver-network) only summarized.

## Crypto parameters (from `LoginController.java:90-92`)
- **RSA**: `KeyPairGenerator("RSA")` + `RSAKeyGenParameterSpec(1024, F4)` → **1024-bit modulus, public exponent = F4 = 65537 (0x10001)**.
- **RSA cipher**: `RSA/ECB/nopadding` (raw, NO padding) — `RequestAuthLogin.java:78`.
- **Blowfish**: ECB, NoPadding, 8-byte block. Session key = 16 random bytes (`LoginController.generateBlowFishKeys`). JDK `Cipher.getInstance("Blowfish/ECB/NoPadding")` works.
- **Static bootstrap blowfish key** (first client packet only): `{0x6b,0x60,0xcb,0x5b,0x82,0xce,0x90,0xb1,0xcc,0x2b,0x6c,0x55,0x6c,0x6c,0x6c,0x6c}` (`LoginEncryption.java:42-60`).
- **Passwords** stored as `Base64(SHA1(plaintext))` (`LoginController.retriveAccountInfo:216-218`), compared via `AccountInfo.checkPassHash`.

## Packet framing (login)
Every packet = `[2-byte size LE][payload]`. Payload framing depends on phase (`LoginEncryption`):
- **1st client packet (AuthGameGuard)**: static blowfish key + **XOR pass** (no checksum). `encryptedSize` header = 8.
- **Later client packets**: session blowfish key + **checksum** appended. Header = 4.
- Server→client packets: the Init is plaintext; later server packets are blowfish-encrypted (session key) + checksum.

`NewCrypt` helpers: `appendChecksum(data,off,size)` (XOR of 4-byte words written to last 4 bytes), `encXORPass(data,off,size,xorKey)` (progressive XOR, key stored in last 4 bytes), `verifyChecksum`, `decrypt`/`crypt` (Blowfish ECB 8-byte blocks).

## Handshake (client side — what the engine must do)

### 1. Connect → receive Init (S→C, opcode 0x00)  [`serverpackets/Init.java`]
```
0x00 | sessionId(4 LE) | protoRev=0x0000c621(4) | scrambledRSAmodulus(0x80=128 bytes)
     | GG: 0x29DD954E 0x77C39CFC 0x97ADB620 0x07BDE0F7 (4×4=16 bytes)
     | blowfishKey(null-terminated string bytes)
```
- Save `sessionId` + `blowfishKey`.
- **Unscramble** the 128-byte modulus (reverse `ScrambledKeyPair.scrambleModulus`):
  1. step4 inverse: `for i in 0..0x3f: m[0x40+i] ^= m[i]`
  2. step3 inverse: `for i in 0..3: m[0x0d+i] ^= m[0x34+i]`
  3. step2 inverse: `for i in 0..0x3f: m[i] ^= m[0x40+i]`
  4. step1 inverse: `for i in 0..3: swap(m[i], m[0x4d+i])`
  → real 128-byte modulus. Build `RSAPublicKeySpec(modulus, 65537)`.

### 2. Send AuthGameGuard (C→S, opcode 0x07)  [`clientpackets/AuthGameGuard.java`] — state CONNECTED→AUTHED_GG
Payload = `0x07 | sessionId(4) | 0(4) | 0(4) | 0(4) | 0(4)` (sessionId + 4 reserved ints = 21 bytes).
Encrypt with **static blowfish key + XOR pass**. Expect **GGAuth (0x0b)** back: `0x0b | response(4) | 0×4`.

### 3. Send RequestAuthLogin (C→S, opcode 0x00)  [`clientpackets/RequestAuthLogin.java`] — state AUTHED_GG→AUTHED_LOGIN
Build a 128-byte plaintext block, zeroed, then:
- user bytes at **offset 0x5E** (14 bytes), password bytes at **offset 0x6C** (16 bytes).
- (legacy single-block method; `_newAuthMethod` 256-byte variant exists if remaining≥256 — use the 128-byte path.)
**RSA-encrypt** the 128-byte block with the unscrambled public key (`RSA/ECB/NoPadding`, exp 65537).
Packet = `0x00 | rsaEncrypted(128)`. Encrypt the whole packet with **session blowfish key + checksum**.
Expect **LoginOk (0x03)**: `0x03 | loginOk1(4) | loginOk2(4) | 0(4) | 0(4) | 0x3ea(4) | 0×3 | zero(16)`.
(Save loginOk1/loginOk2 = the session key's LoginOk pair.)
(If `SHOW_LICENCE=false`, server sends **ServerList (0x04)** directly instead of LoginOk.)

### 4. RequestServerList (C→S, 0x05) → ServerList (0x04)  [`serverpackets/ServerList.java`]
`0x04 | count(1) | lastServer(1) | per server: id(1) ip(4) port(4) age(1) pvp(1) cur(2) max(2) status(1) type(4) brackets(1)` … Pick the server id (usually the running GameServer).

### 5. RequestServerLogin (C→S, 0x02)  [`clientpackets/RequestServerLogin.java`] → **PlayOk (0x07)**
Send: serverId + the account/session keys (loginOk1/2). Expect **PlayOk (0x07)**: `0x07 | playOk1(4) | playOk2(4)`.
SessionKey = (loginOk1, loginOk2, playOk1, playOk2).

### 6. Connect to GameServer (7777) + game-side AuthLogin
New TCP connection; send the game-server `AuthLogin` packet carrying the SessionKey (the GS verifies it with the LoginServer via `PlayerAuthRequest`). This is the boundary between B2 (login-server auth) and B3 (in-game).

## Opcodes (client→server)
`AuthGameGuard=0x07 (CONNECTED)`, `RequestAuthLogin=0x00 (AUTHED_GG)`, `RequestServerLogin=0x02 (AUTHED_LOGIN)`, `RequestServerList=0x05 (AUTHED_LOGIN)`.  [`LoginClientPackets.java`]

## AIPlayerEngine TODO (B2 implementation)
1. `protocol/LoginCrypt.java` — unscramble modulus; blowfish enc/dec (JDK `Blowfish/ECB/NoPadding`); `appendChecksum`/`encXORPass` ports of `NewCrypt`.
2. Rewrite `L2JProtocol.connectAndLogin`: parse Init → unscramble → AuthGameGuard (static key) → RequestAuthLogin (RSA + session key) → parse LoginOk/ServerList/PlayOk → return SessionKey.
3. Then B3: GS connect + game AuthLogin + enter world → prove `online=1`.

## Empirical wire data (B3 live probe, 2026-08-03)
- Connected to :2106; server sends a **194-byte** frame: first 2 bytes = LE size (194, self-inclusive per ReadHandler; dataSize = size - HEADER_SIZE), payload starts at [2] (192 bytes).
- **The Init is NOT plaintext** -- LoginEncryption.encrypt on the server encrypts *every* outgoing packet; the first (Init) uses encXORPass + the STATIC blowfish key; later ones use the session key + checksum. Confirmed: after blowfishDecrypt(STATIC_BLOWFISH_KEY, payload) then reverseXORPass, the recovered opcode = 0x00.
- RECOVERED: Decrypted[0] = 0x00 (Init opcode) -- decryption direction CONFIRMED.
- REMAINING: protoRev/GG fields at offsets 5+/137+ still misaligned by a few bytes -> the server's encXORPass offset/size likely differs from the raw 192-byte payload. Next: pass the server's real offset/size to reverseXORPass. Consult commons/network/Client.writePacket + WriteHandler (write-buffer offset), LoginEncryption.encrypt(data,offset,size), ConnectionConfig.HEADER_SIZE.
