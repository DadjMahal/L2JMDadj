# RuntimeLog — 2026-08-22 S3-T03 — Turn-in + reward-receipt increment (still BLOCKED on live proof)

## Task
S3-T03 — Live-prove quest TURN-IN + reward receipt (exp/adena/item).

## Honest boundary
This task officially needs a LIVE turn-in run with DB/sudo creds to assert reward receipt
against `character_quests` / char inventory deltas (c7-style). This non-interactive shell has no
passwordless DB access, so that live proof is NOT claimed. Contributed instead the engine's
missing turn-in + receipt DETECTION so the live proof can be performed and observed truthfully.

## What was done (engine side)
Signals the server already sends (engine parses both today):
- TURN-IN page = the giver's reward-presentation html (real datapack 30283-06.htm:
  "I will now present you with this sword ... accept it").
- Reward items = the SystemMessage params: PacketLogger decodes SM_TYPE_ITEM_NAME (type 3) to
  `Arg(type=3, rendered="<ItemName>")` and exposes it via `getLastSystemMessage()`.

1. **`behavior/quest/QuestTurnRewardParser.java`** (new, pure):
   - `isTurnInDialog(html)` — matches reward-presentation language (present you with / accept
     this / take ... as a reward / is finished / reward you). Never guesses on blank html.
   - `itemReceipts(params)` — maps the real ITEM_NAME args to `RewardReceipt(item, 1)` lines;
     ignores non-item args + blank names; empty params → empty (no fabrication).
   - `ItemArg` minimal shape so the pure parser never couples to PacketLogger.
2. **`core/BotInfo.questReward`** (new) — dashboard field.
3. **`core/BotSession`** — inside the S3-T02 journal/dialog feed: when the on-screen dialog IS a
   turn-in page and the last SystemMessage carries item-name args, it joins them onto
   `questReward` (e.g. "Sword of Solidarity, Lesser Healing Potion"). Windowed + honest: only
   fires while that dialog is actually up.
4. `QuestTurnRewardParserTest` (7) — REAL 30283-06 reward html vs REAL 30008-03/04 deliver step
   (sanity: a deliver is NOT a turn-in), item-receipt mapping, multi-item, non-item ignored,
   empty/blank guard, toString.

## Evidence / gate
- **GATE GREEN — 525/525 tests** (was 518), style 0, secret-lint clean (exit=0).
- Board row stays **BLOCKED (on S3-T02 + live proof)** — annotated with the contribution.
- Remaining live boundary (play-builder with DB): run the q6 char through a real turn-in, then
  observe (a) `info.questReward` populated from the live SYSMSG, (b) the quest leaves the
  journal (QUEST_LIST 0x80 row gone), (c) exp/adena delta > 0 — the proof is then complete.