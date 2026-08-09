# 🖥 AI Web Dashboard

Real-time statistics for the L2JM AI players — **zero dependencies**, pure Python 3 stdlib, no engine changes.

## What it shows (auto-refresh every 2s)
- **Online status** per bot (live DB truth)
- **Level / EXP / HP%**
- **Position (x, y, z)** — live
- **Current target** objectId and **latest action** (ATTACK / DEAD / USE_SKILL / idle), parsed from the live driver log

Data sources:
1. MySQL `gameserver.characters` (query every 2s, read-only)
2. latest `AIStatusLogs/multiplayer_run_*/mpsession.out` (tail last 1.5MB)

## Run
```bash
bash start_dashboard.sh start     # → http://<host>:8199/
bash start_dashboard.sh status
bash start_dashboard.sh stop
```
(already running as of 2026-08-09; log: `/tmp/dash.log`)

## Reaching it from your browser
The server binds to `0.0.0.0:8199`; `ufw` and iptables are open. **The only possible gate is the DigitalOcean cloud firewall** (configured in the DO console, not on the box) — if the port is blocked there:

**Option A (simplest, uses your existing webssh setup):** SSH tunnel to the droplet
```
ssh -L 8199:127.0.0.1:8199 volodro@<droplet-ip>
```
then open `http://127.0.0.1:8199/` locally.

**Option B:** Open port `8199` for your IP in the DO firewall rules (Networking → Firewalls), then use `http://<droplet-public-ip>:8199/`.

## API
- `GET /` → HTML page
- `GET /api/stats` → JSON
  ```json
  {"ts": 1786292898, "online": 12, "total": 25, "run": "multiplayer_run_...",
   "players":[{"account":"ai_combat_01","name":"CombatBot_01","online":true,
               "level":"3","exp":"364","x":"-84932","y":"251217","z":"-3592",
               "hp":"163","maxhp":"163","hp_pct":"100%","target":"268451547",
               "action":"ATTACK","hostiles":20}]}
  ```