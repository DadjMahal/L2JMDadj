# ⚠️ PRIORITY TASK — Bots appear static: no real movement / quest / combat loop (DEEP REVIEW REQUIRED)

**Priority: HIGH** · **Status: OPEN — investigation deferred, do NOT resolve today**
**Created:** 2026-08-10 · **Reported by:** operator (suspicion after watching the fleet dashboard)

---

## Symptom (as reported)
- Players (bots) are **not moving — their coordinates look static** on the map.
- They show **level 20/22** and XP, yet "without movement and quest passing and combat it's
  not possible" — i.e., the levels/gameplay do not explain what a player should be doing.

## Known facts to carry into the review (gathered before deferring — do NOT re-derive blindly)
1. **Levels are NOT earned — they were seeded.** All 5 chars were inserted into
   `gameserver.characters` with `exp = 1400000` (that is a level-20/22 char in the Interlude
   curve). The server auto-placed them at 20/22 at login. So **level presence alone proves
   nothing about movement/quest/combat activity** — this likely explains the whole confusion.
2. Live packet logs *did* show: `USER_INFO pos=(-82759,250149,-3600)` then later the dashboard
   reported `x=-82634, y=249894, heading=55000` (ValidateLocation) → the coord parse IS real and
   the bots *can* move **a little**. `DELETE_OBJECT` events (wolves dying) show auto-attack
   kills happened. So: some movement + combat occurred, but it's **short-range and looks static**.
3. **There is NO proactive gameplay loop:**
   - Quest-NPC navigation ("running to quest NPCs", reaching towns) is **not implemented**
     (it's Planned Workstream **W5** — blocked on W1 data + W4 protocol stubs; quest packs +
     RequestBypass are partial).
   - The ONLY proactive movement today is the FleetPlay `wander` (one random point every 8s,
     ±900 units) plus the server's auto-follow into melee range during `Action/Attack`.
   - The brain (`CombatAI`) issues attack/target decisions, not movement goals.
4. The dashboard **map view auto-fits** to player+entity bounds, so small/local motion can look
   like nothing is happening.

## Hypotheses the deep review MUST verify (with evidence, not assumptions)
- **H1 — Movement frames don't actually move the char server-side**: does the sender's
  `MoveToLocation(0x01)` frame (dest validated, geo-legality, spawn distance) result in a real
  server position change over e.g. 60s? Capture before/after `ValidateLocation`/`CharInfo`.
- **H2 — Destinations are degenerate**: wander offsets computed from stale/zero coords, or the
  map auto-fit hides ≥900-unit moves (screen-space looks pixel-static).
- **H3 — The bots only ever chase nearby hostiles** (auto-follow), i.e. they never "travel".
- **H4 — DB spawn position vs live position mismatch** (chars inserted at (-82759,250149) but a
  different spawn applied server-side).
- **H5 — Level/Xp claims**: confirm *organic* level-ups occur during play (XP gain over time),
  not just the seeded 1.4M.

## Deep-review scope for the next session (deferred by design)
1. Instrument the fleet: log every `MoveToLocation` sent + server-ack position every 5s; run for
   2–3 min; verify deltas.
2. Test one bot with a **real far destination** (e.g., Talking Island village center
   `(-71338,258271)` ~13k units away): does it arrive? Map coordinates update?
3. Decide movement ownership: wire `phase0.movement` (MovementController/HumanizedPath) behind
   its flag, or give the driver a proper zone-routing goal (nearest town / farm zone), per
   `Documentation/Claude_upgrade_brief.md` **(W5/W6)**.
4. Validate organic XP/leveling after releasing the seeded exp questions (optionally reset the 5
   chars to real level-1 stats for a clean gameplay test).
5. Confirm whether the map's auto-zoom is misleading and snap-to-grid / world-pan is needed.

## Owner / cadence
- Owner: lead (Cline) + next Claude session (**give this priority over other upgrade items**).
- Deferral is intentional: this note exists so no one forgets; the actual investigation starts
  next working session with the evidence-first checklist above.

---
*Related:* `Documentation/Audit/43-phase0-runtime-integration.md` (what is REAL vs SEAM),
`Documentation/Claude_upgrade_brief.md` (W1-W8), `Documentation/upgrade/INTEGRATION_GAPS.md`.