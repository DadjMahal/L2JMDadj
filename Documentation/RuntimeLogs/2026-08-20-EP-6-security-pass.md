# EP-6 RuntimeLog — Security pass

**Date**: 2026-08-20
**Commit**: 8bacb021
**Task**: EP-6 (AUDIT_01) — password purge, dashboard auth, script lint, secret sweep

## S1 — password purge (30 → 0 literals)
- `FleetConfig.accountPassword()` now public + fail-fast: config `ai.account.password` → env
  `AI_ACCOUNT_PASSWORD` → IllegalStateException with setup instructions. No hardcoded default.
- Patched 15 files: 13 example probes/loops (LoginProbe, MoveProbe, EnterWorldProbe, QuestProbe,
  QuestLoop, CombatProbe, CombatLoop, PvPProbe, TradeProbe, QuestFlowLoop, ChatProbe,
  PartyProbe, MultiPlayerSession), core/AIPlayerManager (2 sites), FleetConfig itself.
- MultiPlayerSession ROSTER dropped its password column (indices shifted in runPlayer — fixed).
- `grep -rn "ai123pass" AIPlayerEngine/src --include='*.java'` → **0**

## S2 — dashboard auth (F6)
- DashboardBoot: bind = `DASH_BIND` env (default 127.0.0.1 — was binding ALL interfaces!);
  token = `DASH_TOKEN` env → cfg.token+tokenAuth. **Guard**: non-loopback bind without token →
  boot REFUSED unless `DASH_INSECURE_ACK=1` (documented acknowledgment).
- DashboardApi: when token set, ALL /api/v1/* require it (Bearer header OR `?token=`) — reads
  included, not just mutating POSTs. DashboardBoot's /json,/report,/telemetry guarded the same.
- SPA (dashboard/index.html): `api()` wrapper forwards `?token=` from the page URL — ops open
  `http://host:8210/?token=<DASH_TOKEN>` and the UI authenticates.
- Ops plumbing: `scripts/fleet_env.local.example` (gitignored real file via .gitignore);
  fleet_launch.sh sources it; `scripts/_dash_curl.sh` provides `durl()`; token-aware:
  health_check.sh, watch_fleet.py, e2e_dashboard.sh, position_crosscheck.sh,
  tim001_move_probe.sh, tim001_h5_airtight.sh.

## S3 — script lint (shellcheck NOT installable — no sudo in env; manual pass)
- `StrongPasswordHere` (the REAL DB password, L2J default) purged from 8 scripts →
  `DB_USER/DB_PASS` required via fleet_env.local with fail-fast `:?` guards.
- keep_alive.sh: `eval "$LAUNCH"` → `bash -c "$LAUNCH"` (operator arg, less quote mangling).
- provision_fleet.sh: /tmp/provision.sql fixed-name → mktemp + trap rm (SQL has account hashes).
- All patched scripts `bash -n` clean. Remaining fixed-name /tmp telemetry outputs left as-is
  (single-operator box, inert data) — noted, not fixed.

## S4 — secret history sweep
- RuntimeLogs: 0 hits. AIStatusLogs: dir absent. scripts/: 11 prove scripts carried the
  password as arg defaults/inline literals → all now resolve `AI_ACCOUNT_PASSWORD` via
  fleet_env.local; 3 needed quoting surgery inside `bash -c '...'` strings.
- Bonus: prove scripts' stale `/home/volodro/L2JM` path fixed → `/home/dadj/Projects/l24lude`.
- `grep -rn "ai123pass\|StrongPasswordHere" scripts/ Documentation/RuntimeLogs AIPlayerEngine/src` → **0**

## Verification (curl matrix, port 8210)
- LAN bind (DASH_BIND=0.0.0.0) without token → **boot refused**: `EP-6 guard: refusing to serve
  the dashboard on '0.0.0.0' without a token…`
- With DASH_TOKEN: `/api/v1/health` no-token → **401**; `?token=` → **200**; Bearer → **200**;
  `/json` no-token → **401**; `?token=` → **200**; health body served
  (`{"status":"ok","uptimeSec":3,…,"botCount":0…}`)
- Watcher line: `DASH_TOKEN=… scripts/health_check.sh 0` → `OK: 0/0 bots online` (exit 0)
- `mvn -o -f AIPlayerEngine/pom.xml test` → **414 tests, 0 failures, 0 errors**

## Notes
- Probes with no password now fail fast with a clear message instead of sending a wrong default.
- DB username default `l2j` kept (not a secret); only the password became required.
- ServerBuild's own Database.ini still holds the real password — untouchable per hard rule 1
  (never edit ServerBuild/); scripts no longer mirror it.
