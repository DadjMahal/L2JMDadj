# 33 — Phase 1: full LoginServer auth works (Init → PlayOk) — 2026-08-03

> Phase 1 of the B3 unblock, following the Init-decode fix in Audit/32. The AI engine now
> completes the ENTIRE login-server handshake as a real client: `connectAndLogin()` reaches
> PlayOk and captures the full SessionKey. GameServer enter-world (online=1) is Phase 2.

## What changed (external AIPlayerEngine only; no L2JM server source)
`protocol/L2JProtocol.java` rewritten with the CORRECT client framing/encryption. Root causes
that were wrong in the B2 code:
1. **Client packets must use the SESSION blowfish key + checksum, NOT static+XOR.**
   The server's `LoginEncryption.decrypt` runs `sessionCrypt.decrypt` + `verifyChecksum` on every
   incoming client packet (ReadHandler.parseAndExecutePacket → client.decrypt(buffer,0,size)).
   The static key + XOR is used by the SERVER only for the first OUTGOING packet (Init).
2. **The 2-byte size header is SELF-INCLUSIVE.** Client must write `size = encryptedLen + 2`
   (ReadHandler: `dataSize = size - HEADER_SIZE`).
3. The Init frame must be decrypted (STATIC + reverseXOR, LE blowfish) before parsing — the older
   code parsed the encrypted bytes.

## Verified live flow (ServerBuild LoginServer, 2026-08-03, ShowLicence=True)
1. connect → read Init (decrypt STATIC+XOR) → sessionId + unscrambled RSA + session blowfish key.
2. AuthGameGuard (0x07) `[07][sessionId][0×16]` SES key+checksum → GGAuth (0x0b).
3. RequestAuthLogin (0x00) `[00][RSA-128]` SES → LoginOk (0x03): loginOk1@1, loginOk2@5.
4. RequestServerList (0x05) `[05][loginOk1][loginOk2]` → ServerList (0x04) → serverId (this server: 2, "Sieghardt").
5. RequestServerLogin (0x02) `[02][loginOk1][loginOk2][serverId]` → PlayOk (0x07): playOk1@1, playOk2@5.
Result (pasted): SessionKey(loginOk1, loginOk2, playOk1, playOk2) fully captured; connectAndLogin=true.

## Operational notes
- The 6 account-in-use (LoginFail 0x07) came from the LS in-memory `_loginServerClients` map: after
  PlayOk the client is `joinedGS=true`, so LoginClient.onDisconnection does NOT remove the account.
  A redundant probe therefore ends "account in use" until the LS is restarted (watchdog
  `LoginServerTask.sh` restarts it in ~10s; it clears the map). Real in-game clients are expected to
  stay in-use for the duration of play — this is normal.
- `scripts/b3_login_probe.sh` runs the probe and asserts `RESULT connectAndLogin=true`.
