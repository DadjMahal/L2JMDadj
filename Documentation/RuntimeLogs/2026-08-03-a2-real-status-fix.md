# 2026-08-03 — A2: Fix real_status.sh double-print bug

## Objective
Fix `real_status.sh` printing each activity count twice (`0\n0`) — `grep -c` prints `0` AND exits non-zero, so `|| echo 0` also fired.

## Files modified
- `AIPlayerEngine/AIStatusLogs/real_status.sh` — replaced the 5 `grep -c ... || echo 0` lines with a `count()` helper:
  `count() { local n; n=$(grep -c "$1" "$LOG_FILE" 2>/dev/null || true); echo "${n:-0}"; }`
  Returns exactly one number, and treats a missing log file as `0`.

## Verification output (real)
```
$ bash -n real_status.sh          → OK
$ real_status.sh:
    Combat actions: 0
    Quest actions: 0
    Trade actions: 0
    Level ups: 0
    Chat messages: 0
$ real_status.sh | grep actions | cat -A
    Combat actions: 0$      ← single line end (was 0\n0 before)
    Quest actions: 0$
    Trade actions: 0$
```

## Result
Each activity count now prints exactly one number (no double-print). `real_status.sh` stays honest: 0 AI online, 0 activity, server UP.

## Next steps
- A3: make `count_ai_players.sh` consistent with `real_status.sh` (DB = `gameserver`, `sudo mysql -u root`).
- Stream B: live-verify 1 AI player on the running server.
