# 2026-08-03 — A3: count_ai_players.sh consistent with real_status.sh

## Objective
Make `scripts/count_ai_players.sh` use the same proven DB/credential form as `real_status.sh`
(`sudo mysql -u root gameserver`) so it returns real data instead of "database not accessible".

## Files modified
- `scripts/count_ai_players.sh` — `mysql -u root gameserver` → `sudo mysql -u root gameserver` (3 calls; idempotent negative-lookbehind replace).
- (Note: a *separate*, log-based `AIPlayerEngine/AIStatusLogs/count_ai_players.sh` exists — different purpose, left untouched.)

## Verification output (real, before/after)
```
BEFORE (no sudo):
  AI players currently online: 0 (or database not accessible)
  Total registered AI players: 0 (or database not accessible)
  Database not accessible

AFTER (sudo mysql -u root gameserver):
  AI players currently online: 0
  Total registered AI players: 25
  Merchant  6
  Quest      6
  Social     6
  Combat     6
  Other      1      (the explorer)
  → 25 total, 0 online — matches real_status.sh + the 25 chars found in DB
```

## Result
`count_ai_players.sh` now returns real data (25 registered, 0 online) using the same `gameserver` DB + `sudo mysql -u root` form as `real_status.sh`. No more "database not accessible".

## Next steps
- Stream A is complete (A1 cold-start test, A2 real_status fix, A3 count fix).
- **Stream B** (the real gap): live-verify 1 AI player on the running server.
