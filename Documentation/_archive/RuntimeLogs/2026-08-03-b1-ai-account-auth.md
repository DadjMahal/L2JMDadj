# 2026-08-03 — B1: AI account credentials made valid (auth-ready)

## Objective
Verify/establish AI account passwords/auth so `AIPlayerManager.connectToServer` can authenticate against the live login server.

## Findings (investigation, real)
1. **Accounts exist** in the **`loginserver` DB** (NOT `gameserver`): 25 `ai_%` accounts (ai_combat_01..06, ai_explorer_01, ai_merchant_01..06, ai_quest_01..06, ai_social_01..06). Login names correct.
2. **🔑 Stored passwords were PLAINTEXT `"ai123pass"`** — but the login server (`LoginController.java:216-218`) computes `Base64(SHA1(password))` and compares via `checkPassHash`. So **every AI login would fail the password check** (and ban the IP after 5 attempts). Fabricated setup stored plaintext instead of the required hash.
3. **Bug in `connectPlayer`** (line 216): `account = "ai_" + name.toLowerCase()` while `name` was already `"ai_combat_01"` → built `"ai_ai_combat_01"` (non-existent). The `spawnAIPlayer` path was correct; the `--spawn-all` path (spawnCombat/Quest/Merchant/Social → connectPlayer) was broken.

## Fixes applied
- **DB**: `UPDATE loginserver.accounts SET password=Base64(SHA1('ai123pass')) WHERE login LIKE 'ai%';` (all 25).
- **Code**: `AIPlayerManager.connectPlayer` — `account = "ai_" + name.toLowerCase()` → `account = name.toLowerCase()`.

## Verification output (real)
```
Computed Base64(SHA1('ai123pass')) = CBaKoSACCN4c8lxxnen4gH2jHh8=  (28 chars)
BEFORE: ai_combat_01 | ai123pass | len=9        (plaintext — would fail hash check)
AFTER:  ai_combat_01 | CBaKoSACCN4c8lxxnen4gH2jHh8= | len=28
all 25 ai_% accounts now have a 28-char hashed password
mvn -q compile → BUILD SUCCESS (exit 0) after the code fix
```

## Scope note (honest)
B1 makes the **credentials valid** (DB password hash + correct account name). A successful *live* login ALSO requires the auth **packet format** to match what `LoginController` expects — the engine's `buildAuthLoginData` sends `name\0pass\0` (opcode 0x08), which is NOT the real L2J `RequestAuthLogin` format. That is **B2** (complete L2JProtocol packet flow). So B1 ≠ "auth works end-to-end"; B1 = "credentials are now correct so a correct-protocol login would pass the hash check."

## Next steps
- **B2**: implement the real `RequestAuthLogin` packet (RSA/blowfish session key + encrypted credentials per `01-commons.md`/`04-gameserver-network.md`).
- **B3**: connect 1 AI player live; prove `online=1` + "entered world" in logs.
