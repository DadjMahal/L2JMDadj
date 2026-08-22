# RuntimeLog — 2026-08-22 EB-03 configurable goal-ladder per profile

**Task:** EB-03 — BotPlayController goals/priorities configurable per profile (not hardcoded
order). Commit `3dd20c2e`.

## What changed
1. **`BotPlayController.Rung` enum** (new): the ladder steps — `SURVIVE, QUEST_TALK, RESTOCK,
   COMBAT, HUNT, QUEST` (the historical ladder decomposed into named rungs).
2. **`BotPlayConfig.priority` (List<Rung>)**: configurable per profile; `DEFAULT_LADDER` =
   historical order (SURVIVE, QUEST_TALK, RESTOCK, COMBAT, HUNT, QUEST) so default behavior is
   unchanged. All existing constructors chain to a full one with `DEFAULT_LADDER`; new
   `withLadder(...)` copy + full ctor let profiles reorder or DROP rungs.
3. **`decide()` refactored from a hardcoded if-chain** into a ladder walk:
   - SURVIVE is evaluated FIRST regardless of profile order (hard safety rung) when the profile
     includes it; a profile can drop it entirely.
   - Each rung = a pure private decision method (`rungSurvive/rungQuestTalk/rungRestock/
     rungCombat/rungHunt/rungQuest`) returning null when it declines this tick.
   - REST remains the implicit fallback (never a NONE/idle).
4. **Per-race ladder profiles**: `BotPlayConfig.ladderForRace(race)` — ORC melee-first (COMBAT
   before QUEST), ELF/DARK_ELF caster-first (QUEST_TALK before COMBAT), DWARF merchant-first
   (RESTOCK early), HUMAN/HUMAN+null = DEFAULT.
5. **Wired into the fleet** (`core/BotSession` builds cfg with the per-race ladder).

## Tests
- New in `BotPlayControllerTest` (26 total, +5): DEFAULT order preserved; a profile dropping all
  quest rungs really fights (FARM) while the default ladder talks to the giver first (BYPASS);
  ladderForRace ORC/ELF ordering; null → DEFAULT.
- Gate: **444/444 green** (was 439), style 0 violations, secret-lint clean.

## Notes
- Behavior for DEFAULT is identical to pre-EP-03 (all 21 pre-existing controller tests pass
  unchanged). Only refactor of the ladder + new per-profile knob.