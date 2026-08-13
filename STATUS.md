# 📊 Status — L2JM

> Single live snapshot. Mirrored by `START_HERE.md`. Overwritten every session.
## 📚 Docs consolidated 2026-08-12: root `TASKS.md` (110-task roadmap, 110/110 done) + stale `Documentation/README.md` + `Documentation/TODO_LIST.md` archived -> `Documentation/_archive_superseded/` (see `_ARCHIVE_INDEX.md`). ONLY live board = `Documentation/TASKS.md`; single canonical README = root `README.md`.
> If `SESSION_IN_PROGRESS.md` exists at repo root, resume it (rate-limited mid-work).

## Phase: 5 — WPT web-panel telemetry COMPLETE + integrated (2026-08-12). **33/33 WPT tasks DONE-PUSHED**; API + frontend + telemetry shipped. **TIM-001 (H1–H5) ✅ RESOLVED 2026-08-13 with live DB evidence**; **223/223 tests green**.

## ✅ Reviewed-task registry (anti-redo) — `Documentation/REVIEWED_TASKS.md`
> Consolidated record of every task + status + commit. **Check it before starting anything.** Live board = `Documentation/TASKS.md` (§6 TIM-001 open; §12 changelog).
## ✅ 33/33 WPT DONE-PUSHED:
## ✅ Phase A (API+infra) WPT-01..08: `/api/v1/{bots,entities,landmarks,events,history,health,config,stream}` + SSE + live config POST + bearer-token gate; EventRing/HistoryRing/FleetMetrics.
## ✅ Phase B (UX) WPT-09..15/17/18/19/20/31: world map data + pan/zoom renderer, movement trails (WPT-11), playback (WPT-12), grid v2 + search/follow/pin (WPT-19), detail drawer + sparklines (WPT-15), control panel (WPT-17), event feed, alerts (WPT-18), hotkeys/theme, modular SPA build.
## ✅ Phase C (telemetry) WPT-21..30: movemeter + STAGNANT badge (`ops.html`), SystemMessage/chat parser (+ WPT-28 chat view), StatusUpdate attr map, inventory v2 + datapack names, combat KPIs, skill metering, entity name resolution, quest telemetry (WPT-27), `position_crosscheck.sh`.
## ✅ Phase D (ops) WPT-32..34: `ops.html`, README/favicon/i18n/e2e, `server_health.sh`.
## ▶ Live-verified 2026-08-12/13: Login :2106 / GS :9014 / Game :7777 UP (JDK25); `/api/v1/health`+`/api/v1/bots`+`/api/v1/events` serve real JSON + live sysmsg/chat. **Marked DOWN at session open, brought back UP 2026-08-13** (`~/.jdk/jdk-25.0.4+7` on PATH; system java is JDK21 but server JARs are JDK25).
## ✅ TIM-001 (HIGH) RESOLVED — 2026-08-13, all H1–H5 PROVEN by live `gameserver.characters` evidence. H1/H3 (movement persistence + proactive travel): all 5 bots moved and their positions persisted (~3.4–5k u DB deltas). H2 (destinations degenerate?): 0/5, NO. H4 (DB vs live): positions track live movement. H5 (organic XP — the 2-session blocker): fixed two live combat-engagement bugs (stale `AIPlayer` position in `detectNearbyEnemy`; missing attack `targetObjId` → planner emitted no frames) and all 5 bots then gained real kill XP: **+210 / +437 / +141 / +175 / +465** from the 2884 L5 baseline. Full evidence: `RuntimeLogs/2026-08-13-tim001-h1-h5-resolved.md` + `REVIEWED_TASKS.md §B`. Suite **223/223 green**.

## Resumed & finished 2026-08-12/13 (on `master`)
- All 33 WPT tasks DONE-PUSHED (see REVIEWED_TASKS §A for the commit table).
- 2026-08-13: engine rebuilt with ZoneRouter multi-hop; suite green **218/218**; TIM-001 run #2
  executed (honest-negative for persistence); STATUS/START_HERE/TASKS/REVIEWED_TASKS synced.
- Task board = source of truth: `Documentation/TASKS.md`. TIM-001 active focus.

## Historical (superseded — kept for traceability)
- **Phase 4 (2026-08-05):** Streams A–G + 110-roadmap ALL DONE (117/117 tests; independent AI review
  verified 8/8 claims; check_style + no-dead-code verified). `DONE_SUMMARY.md` / `AUDIT_ORIENTATION.md`.
- **Phase 5 (2026-08-10..12):** WPT 33-task panel build — see `REVIEWED_TASKS.md §A` for the full
  commit-per-task table and `TASKS.md §12` for the changelog.
- **Slides of the live engine (proven, 2026-08-03..05):** real PvE/PvP/quest via real external sockets;
  packet-feedback combat loop (death-gated, 59/59 tests); C5 combat proof (`c5_live_combat_proof.sh`).

## Recent RuntimeLogs (most recent first)
- 2026-08-03-streamC-packet-feedback.md
- 2026-08-03-streamC-live-driver.md
- 2026-08-03-streamC-gs-client.md
- 2026-08-03-streamC-decision-to-send.md
- 2026-08-03-streamC-combat-decisions.md
- 2026-08-03-streamC-npc-info-fix.md
- 2026-08-03-122945-b6-quest.md
- 2026-08-03-114841-b5-pvp.md
- 2026-08-03-102759-b4-combat.md
