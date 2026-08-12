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
