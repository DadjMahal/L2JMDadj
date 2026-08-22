# RuntimeLog — 2026-08-22 S3-T02 — "Next objective from live server" parser increment

## Task
S3-T02 — Live-prove quest objective progress (kill/collect counters via QUEST_LIST; quest
persists [[6,1]], dialog re-click works — next objective from live server).

## Context (what was already proven by play-builder)
- Quest dialog re-click + journal persistence proven: active journal = `[[6,1]]` (quest 6,
  state 1) fed by real `QUEST_LIST (0x80)` in the fleet loop; quest accepts done via
  validated bypass links (quest_accept_q6.log etc.).

## This increment — surface the CURRENT objective from the LIVE server
The engine captured the quest journal + dialog HTML but had NO parser turning the live
objective text into a structured value. Added:

1. **`behavior/quest/QuestObjectiveParser.java`** (new, pure/deterministic):
   - `parse(questId, state, html)` → `Parsed {questId, state, text, step}` or `null` for blank
     html (never fabricate).
   - HTML tag/entity strip + plain-text compacting; step flavor inferred from the SAME
     vocabulary the datapack quest classes write: `TALK` (deliver/take this to/talk to),
     `KILL` (kill/defeat/slay), `COLLECT` (collect/bring back/find the/search for).
   - Precedence KILL > COLLECT > TALK; prose without an action hint stays `UNKNOWN`
     (honest — never invent an objective).
2. **`core/BotInfo.questObjective`** (new) — dashboard field.
3. **`core/BotSession`** — in the journal feed: when the parser gets live dialog HTML + the
   first active quest id/state, it set the objective on the row (`"q6[s1] KILL: Kill Keltirs (3/10)"`).
4. `QuestObjectiveParserTest` (9) — fixtures from the REAL datapack
   `Q00101_SwordOfSolidarity` html (30008-03/04/05, 30283-02) + live-format count strings.
   Reality of "TALK-deliver" vs "find him" ambiguity fixed by item-y COLLECT hints.

## Evidence / gate
- **GATE GREEN — 518/518 tests** (was 509), style 0, secret-lint clean (exit=0).
- Board row stays `IN_PROGRESS (play-builder)` (owner unchanged; this is a contributed
  increment). Remaining live boundary (play-builder): run the q6 char probe with DB creds/sudo
  to capture the RAW objective counter + a Kill-driven QUEST_LIST state flip (0x80), then
  assert the objective string changes on the live fleet. Non-interactive shell has no
  passwordless DB creds — truthfully blocked there, not faked.