# RuntimeLog — 2026-08-12 WPT polish + TIM-001 live evidence run

**Mode:** lead resumes after teammate sub-agent LLM hit daily 429 cap (`deepseek-v4-flash`); lead
completed the remaining work directly. Servers were DOWN → restarted (Login+Game, JDK25). Suite stays **213/213 green**.

## Objectives
1. Finish the remaining WPT dashboard tasks (frontend polish).
2. Run the HIGH-priority TIM-001 live proof.
3. Sync the board/docs.

## What landed (each DONE-PUSHED on `master`)
| Commit | Work |
|---|---|
| `66c67487`+`32266bb9` | **WPT-11** movement trails on map (last-N polylines, hotkey T) + served-bundle regen |
| `9fc361b4` | **WPT-12** state playback (play/pause/scrub/step/speed from /api/v1/history) |
| `1018fc0e` | **WPT-15(+16)** bot detail drawer + XP/HP/Level sparklines; **WPT-18** alerts (death/disconnect/level-up/server-down toasts+sound) |
| `595a59b5` | **WPT-17** fleet control (live /api/v1/config GET+POST tuner + focus); **WPT-19** filter/follow/search/highlight + pin camera (map.js `focusOn`); **WPT-28** chat manager view (real TYPE_CHAT/TYPE_SYSMSG) |

**Honest scope:** WPT-17 pause/resume + “send to landmark” and WPT-28 outbound send are NOT done —
they need backend endpoints outside the frozen v1 contract. Documented, not faked.

## TIM-001 — live evidence run (2026-08-12, `scripts/tim001_move_probe.sh`, 2-min, movement FORCED ON)
- **H2** destinations real: `[FleetPlay] ai_combat_04 HOP -> far-point (-87613,255465,-3600) ~21391u`.
- **H3** proactive travel attempted (HOPs) + real `NPC_INFO`/`DELETE_OBJECT` (kills).
- **H1/H5 NOT proven:** `gameserver.characters` x/y/z + exp **identical before/after** → far moves sent
  but **not persisted server-side** (Audit-44 far-hop / 9900u-cap / ack path still open). → **TIM-001 NOT resolved.**

## Probe gap — RESOLVED 2026-08-12
`tim001_move_probe.sh` curls `/telemetry`; the missing route was **ADDED** to `FleetPlay.startDashboard()`
(serves `MoveTelemetry.report()`, EVIDENCE-H1/H2/H5 lines). Probe defaults fixed too
(ENGINE→`/home/dadj/Projects/l24lude`, MYSQL_ARGS→`mysql -u l2j -pStrongPasswordHere gameserver`; `bash -n` OK).
**Next proof:** re-run `tim001_move_probe.sh` for a longer window and confirm a real DB position delta before
TIM-001 can be marked resolved.

## Remaining open (honest)
- **WPT-21** movement-ack/STAGNANT telemetry: backend `/telemetry` route **DONE (uncommitted)** serving `MoveTelemetry.report()`; unit-tested. Live evidence re-run still pending. **WPT-27** quest telemetry — TODO.
- **TIM-001** movement persistence — IN PROGRESS, not resolved (route now in place; needs a longer `/telemetry`-driven run to confirm a DB position delta).
- Servers still UP (Login :2106 / GS :9014 / Game :7777); fleet stopped (8080 free).

---

## LIVE VERIFIED RE-RUN — 2026-08-12 (stack relaunched on JDK25, `scripts/tim001_move_probe.sh 2`)

The `/telemetry` route was confirmed **working in production** and a fresh 2-min evidence run was completed with the stack UP (Login :2106, Game :7777, Dashboard :8080).

**From `/telemetry` (live instrument — WPT-21 backend VERIFIED):**
```
--- ai_combat_04 ---  movesSent=3  degraded<500 u=0  samples=336
  [EVIDENCE-H1] serverMoved=0 u (last 60s)  total=0 u
  [EVIDENCE-H2] degenerateDestinations=0 / 3     → H2 ✓ NO degenerate (all far)
  [EVIDENCE-H5] expGained=0
```
- Fleet log HOP proof: `ai_combat_04 HOP -> far-point (-91530,253745,-3600) ~21391 u` (3×).
- `/telemetry` returned evidence for **all 5 bots** (movesSent / degraded / H1 / H2 / H5 per bot).

**DB diff (gameserver.characters BEFORE == AFTER, all 5 chars):** exp + x/y/z **identical**
(CombatBot_01 1381342 @ -102615,240560 ; CombatBot_04 1400000 @ -87613,255465 ; CombatBot_05 1385671 @ -97314,251775 …).

**Verdict — TIM-001 STILL NOT RESOLVED (now with a working instrument, so this is tight evidence):**
- H1 (server moves char) **✗ FAIL** — 3 far HOPs sent (~21391u) but `serverMoved=0` and DB position unchanged.
- H2 (destinations degenerate) **✓ PASS** — degenerateDestinations=0/3 (all far), not stale/zero.
- H3 (proactive travel) **attempted** — far HOPs issued, but not persisted.
- H4 (DB vs live) **baseline re-captured** — no live delta yet since positions static.
- H5 (organic XP) **✗ FAIL** — expGained=0; exp unchanged over window.

**Action needed to close TIM-001:** the far-hop is not being acked/persisted by the server (Audit-44: 9900u single-move cap / ack path still open). Next step is the **zone-routed short-hop (`ZoneRouter.nextHop()` ≤4800u)** path — a single 21391u HOP exceeds the server's per-move cap and is rejected, so it never landed. Implement + verify short multi-hop travel, then re-run this probe and confirm a **real DB position delta** → only then mark TIM-001 DONE.
