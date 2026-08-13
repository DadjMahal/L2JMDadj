# RuntimeLog — 2026-08-13 TIM-001 evidence run #2 (fresh, 5-bot, movement FORCED ON)

Date: 2026-08-13 · Lane: lead (Cline) · Type: live evidence run (honest outcome).

## What was done
1. Brought the stack up from DOWN (system `java` is JDK21; server JARs are JDK25) using
   `/home/dadj/.jdk/jdk-25.0.4+7/bin` on PATH:
   - LoginServer :2106 / :9014 — UP ("Server loaded").
   - GameServer :7777 — UP ("Server loaded in 16 seconds", "Registered on login as Server 2: Sieghardt").
   - MariaDB already on :3306.
2. Rebuilt the engine with the **ZoneRouter short-multi-hop** WIP (`buildHops()` split; each hop
   ≤4800u) — `mvn -o compile` OK. Full suite: **218/218 tests green** (up from 215; ZoneRouterTest 7).
3. Ran `bash scripts/tim001_move_probe.sh /home/dadj/Projects/l24lude 2` (FleetPlay, movement FORCED ON).

## Honest outcome (paste-worthy evidence)
- `gameserver.characters` (CombatBot_01..05): **BEFORE == AFTER exactly** — x/y/z + exp unchanged
  (e.g. CombatBot_01 exp=1381342, CombatBot_04 exp=1400000 L20; identical to start).
- MoveTelemetry `/telemetry`:
  - ai_combat_04: `movesSent=2, degraded<500u=0, serverMoved=0 u, degenerateDestinations=0/2, expGained=0`
  - ai_combat_01/02/03/05: `movesSent=0, serverMoved=0 u, expGained=0`
- `/json` parse failed on the probe's inline parser (line 1 col 1198 delimiter bug — the raw dump was
  truncated mid-JSON; telemetry block itself parsed fine). Not an engine fault; cosmetic to the harness.

## Verdict
- **H1 persistence: NOT PROVEN** — barely any moves emitted (2 across 5 bots in 2 min) and `serverMoved=0`; DB identical → the fleet is **not yet driving the ack-gated hop sequence** as its primary idle behavior. The ≤4800u hop logic is shipped + unit-tested, but the live loop isn't sending enough accepted hops to move/persist.
- **H2:** clean (0 degenerate destinations).
- **H5 organic XP:** not shown (`expGained=0`).

## Next concrete action (carries into next session, see REVIEWED_TASKS §B)
Drive the hop-gate in `FleetPlay` so an idle bot **walks the ≤4800u hop sequence end-to-end** (not just
wander/combat), then re-run `scripts/tim001_move_probe.sh` and require a **nonzero DB x/y/z or exp delta**
before flipping TIM-001 to RESOLVED. Do not fabricate evidence.
