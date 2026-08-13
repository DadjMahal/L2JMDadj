# Scripts: Quests Part 2 Audit (Iteration 23)

## Purpose
Audit of quest scripts in `dist/game/data/scripts/quests/` (second half, approximately Q00173–Q00688). Covers quest registration, higher-level quest patterns (saga/class-change quests, repeatable farming quests, boss quests), and inter-quest dependencies.

## Quest Registration
All quests are registered via `QuestMasterHandler.java`, which maintains a static array `QUESTS` of quest class objects. The `main()` method instantiates each quest via reflection (`getDeclaredConstructor().newInstance()`), which triggers the constructor that registers NPC/event listeners. If a quest fails to load, it is logged at SEVERE level and skipped — the server continues without that quest.

### Inter-Quest Dependencies
Several quests reference other quests to check completion prerequisites:
- `Q00640_TheZeroHour` checks `Q00109_InSearchOfTheNest` completion before allowing start.
- `Q00641_AttackSailren` checks `Q00126_TheNameOfEvil2` completion before allowing start.
- This pattern is common in high-level content quests that gate behind earlier quest completion.

## Representative Quest Analysis (Second Half)

### Q00640_TheZeroHour
- **Quest ID**: 640
- **NPC**: KAHMAN (31554)
- **Prerequisite**: `Q00109_InSearchOfTheNest` must be completed.
- **Item**: FANG_OF_STAKATO (8085)
- **Mechanics**: Kill-based collection quest. 15 stakato mob types registered via `addKillId`. Uses `getRandomPartyMemberState` for party distribution. Rewards are tiered via a `REWARDS` 2D array — player selects reward by entering a numeric index as the event string.
- **Pattern**: `StringUtil.isNumeric(event)` check allows dynamic reward selection from the rewards table. Uses `exitQuest(true, true)` for repeatable completion.
- **Gotcha**: Reward selection uses numeric event strings parsed to index into the REWARDS array — no bounds checking beyond `getQuestItemsCount` validation.

### Q00641_AttackSailren
- **Quest ID**: 641
- **NPC**: STATUE (32109)
- **Prerequisite**: `Q00126_TheNameOfEvil2` must be completed.
- **Items**: GAZKH_FRAGMENT (8782), GAZKH (8784)
- **Mechanics**: Kill-based collection. 6 mob types registered. 5% drop chance for GAZKH_FRAGMENT via `getRandom(100) < 5`. Requires 30 fragments to complete. Uses `QuestSound.ITEMSOUND_QUEST_ITEMGET` for sound feedback.
- **Pattern**: Uses `broadcastPacket(new MagicSkillUse(...))` for visual effects on quest completion. Uses `takeItems(player, itemId, -1)` to take all of an item.
- **Gotcha**: Uses `player.getQuestState(getName())` instead of `getQuestState(player, false)` — both work but inconsistent style.

### Saga / Class-Change Quests (Q00070–Q00101)
- Extend `AbstractSagaQuest`, which provides the full saga quest flow: NPC spawns, mob kills, class checks, and cinematic sequences.
- Each quest subclass defines arrays for NPCs, items, mobs, class IDs, spawn coordinates, and text.
- `QUEST_CLASSES` in `AbstractSagaQuest` maps quest IDs to allowed player class IDs.
- These are the most complex quests in the codebase, involving timed NPC despawns, damage hate manipulation, and skill casting.

### Repeatable Farming Quests (Q00615–Q00663)
- Higher-level (60+) repeatable quests focused on grinding specific mobs for currency/items.
- Common patterns: kill-based collection with random drop chances, tiered reward tables, party member selection for fair distribution.
- Often gate behind completion of earlier non-repeatable quests.

### Tutorial Quest
- `Q00999_T0Tutorial` — handles the new player tutorial experience.
- Uses `onEnterWorld` hook for initial player setup.
- Simpler structure compared to other quests; focuses on teaching basic mechanics.

## Gotchas / Refactor Candidates
- `QuestMasterHandler` uses a static array — adding/removing quests requires code changes, not config.
- Reflection-based instantiation swallows errors; failed quests are silently skipped.
- Inter-quest dependencies are checked via string class names — no compile-time validation.
- `giveItemRandomly` is used inconsistently — some quests use `getRandom(100) < chance` manually instead.