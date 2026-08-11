# RuntimeLog — 2026-08-11 ops session (Cline#4): server relaunch on JDK25 + TIM-001 evidence baseline

**Branch:** `fix/tim-001-movement-review` · **Roles:** Cline#4 (ops/docs corner: WPT-32/33/34)
**Supersedes:** the stale "server UP since Jul 31" claim in START_HERE/STATUS — the server was DOWN at session start.

## 1. Server was DOWN — root cause: JDK mismatch (REAL, not assumed)

- Symptom: `ss -tln | grep -E '2106|7777'` → nothing. `pgrep java` → nothing.
- `./StartServer.sh` failed to boot LoginServer with:
  `UnsupportedClassVersionError: ... compiled by a more recent version of the Java Runtime (class file version 69.0)`,
  and the system default `java -version` = **JDK 21** (recognizes only ≤ 65.0).
- **JDK 25 (class file 69.0) is REQUIRED** by `ServerBuild/libs/GameServer.jar` and `LoginServer.jar`.
  The JARs do NOT link a private JVM; the `*ServerTask.sh` scripts just call plain `java` from PATH.
- Working JDK 25 found on this machine at `~/.jdk/jdk-25.0.4+7` (also staged at `/tmp/jdk25/jdk-25.0.4+7`).
- **Restart command that works:**
  ```bash
  cd /home/dadj/Projects/l24lude
  PATH="$HOME/.jdk/jdk-25.0.4+7/bin:$PATH" ./StartServer.sh
  ```
- Live-verified after relaunch: LoginServer :2106 + GS listener :9014 + GameServer :7777 all LISTEN;
  `game/log/java0.log` → `Server loaded in 36 seconds.` + `Registered on login as Server 2: Sieghardt`.

> Apply the same PATH wrapper for any future boot. Documented in README (WPT-33) and this log.

## 2. TIM-001 evidence baseline (H4 & H5) — captured BEFORE any fleet run today

`gameserver` DB snapshot (`scripts/server_health.sh`, aka `./scripts/server_health.sh`):
- **Total chars: 5** (all `ai_%`), **all currently offline** (`online=0`).
- **H5 evidence — levels are SEEDED, not earned:** every bot has exactly `exp=1400000` (level 20×4 / 22×1).
  Server auto-places them at L20–22 at login from that EXP. Baseline confirms: **no organic XP yet** — so any later
  EXP delta above 1,400,000 is real gameplay proof. (Later: `scripts/count_ai_players.sh` / `real_status.sh` for re-checks.)
- **H4 evidence — live positions differ from seeded spawn:** chars were DB-inserted at `(-82759,250149,-3600)`;
  current `characters.x/y/z` (all at the B4 combat zone, offline) are now:
  ```
  ai_combat_01 -> -83010, 252327, -3596 (L22)
  ai_combat_02 -> -82769, 250082, -3600 (L20)
  ai_combat_03 -> -81804, 251196, -3600 (L20)
  ai_combat_04 -> -83012, 249855, -3600 (L20)
  ai_combat_05 -> -82298, 249652, -3600 (L20)
  ```
  Interpretation: coords moved from the seed OR the seed was never their live spawn — H4 remains open pending a live
  teleport test against a fresh spawn value.
- **Baseline for H2/H3:** positions are ~500–1,200 units apart around the zone — consistent with the documented
  8s ±900-unit wander. A fleet run (FleetPlay) is needed to watch live deltas; `dashboard/ops.html` (WPT-32)
  shows movement per bot with a STAGNANT badge for verification-in-browser.

## 3. Credentials recap (used by ops tooling, kept out of docs before)
- DB user/password come from `ServerBuild/{login,game}/config/Database.ini`: `Login=l2j Password=StrongPasswordHere`.
- `mysql -u l2j -pStrongPasswordHere <db>` → works without sudo (root user needs a TTY password; existing
  `scripts/real_status.sh`/`count_ai_players.sh` still assume sudo root — noted, not touched).
- Bot account password: `ai123pass` (as used across probes/FleetPlay).

## 4. What the fleet needs next (for TIM-001 H1 + H5 live proof)
1. Start the fleet with the engine (Cline#1/#2/#3 territory). Port 8080 keeps serving the dashboard.
2. Watch `gameserver.characters.exp` over time (must rise above 1,400,000) → **H5 PROVEN**.
3. Watch live coords drift (or not) → H1/H2/H3 verdicts; ops.html = zero-edit viewer for this.

## 5. Deliverables shipped this session (branch `fix/tim-001-movement-review`)
- WPT-34: `scripts/server_health.sh` (ports+DB+chars+accounts; live EXIT=0) — commit `4ed840e1`.
- WPT-32: `AIPlayerEngine/src/main/resources/dashboard/ops.html` (health/events/config + STAGNANT detector) — commit `bf2ce3db`.
- WPT-33: README Web-Panel section, `dashboard/favicon.png` (16×16 RGBA), `dashboard/i18n/en.json`, `scripts/e2e_dashboard.sh` (E2E_EXIT=0 live) — commit `fd9581d2`.