# RuntimeLog — 2026-08-22 EB-01 split decision core from session I/O

**Task:** EB-01 — BotSession handles socket/lifecycle; the per-tick DECISIONS move into the
behavior engine (post-EP-4 refinement). Size L; one commit `9a439438`.

## What changed
1. **`behavior/BotSurvival.java`** (new, pure, no IO): the survival/supply decisions that
   previously lived inline in BotSession —
   - `Guard survivalGuard(...)` death-loop / regen-hold / overwhelm predicates (S6),
   - `shouldSipPotion(...)` + `HP_POTION_*` thresholds (S6-T04),
   - `FleeHop fleeHop(...)` — the FLEE/RETREAT away-hop vector,
   - `findPotion(...)` over a minimal `Item` view (no protocol dependency).
2. **`behavior/QuestDialogSession.java`** (new, pure driving state machine): the STEP-2 quest
   dialog click→read→send→stale-reclick machine that lived inline in BotSession
   (`questDialogOpen` / `questLastHtml` / `lastQuestClickMs` / `questSentLinks`). Returns
   `OPEN / CLICK_GIVER / SEND_BYPASS / WAIT`; still delegates WHICH link to `QuestDialogDriver`.
3. **BotSession** now: owns socket/lifecycle/reconnect/telemetry + the goal ladder execution
   switch (that is session I/O by design); delegates the extracted decisions to the new classes
   (potion gate, survival guard, flee vector, quest-dialog driving). Net −8 lines, removed the
   inline potion d1 + quest state fields + dead imports.

## Tests
- New: `BotSurvivalTest` (11) + `QuestDialogSessionTest` (8) — lock the extracted decisions:
  potion/vards/flee/inventory-search; open→script→accept→done, no-re-send, stale re-click reset,
  turn-in completion.
- Gate: **434/434 green** (was 415), style 0 violations, secret-lint clean.

## Notes
- Behavior decisions are identical to the pre-split impl (same constants, same predicates).
- The big goal-ladder execution switch stays in BotSession BY DESIGN — it is the session's
  socket action layer (wiring.moveTo/executeCombat/bypass), not a decision. EB-01 = decisions
  split; EB-03/EB-09 build on top.