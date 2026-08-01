# Scripts: Quests Part 1 Audit (Iteration 22)

## Purpose
Audit of quest scripts in `dist/game/data/scripts/quests/` (first half, approximately Q00001–Q00172). This document covers the quest scripting framework, common patterns, and representative quest analysis.

## Quest Scripting Framework

### Base Classes
| Class | Purpose |
|---|---|
| `Quest` (`gameserver/model/script/Quest.java`) | Base class for all quests. Provides event registration, state management, timer support, item manipulation, and DB persistence. |
| `QuestState` (`gameserver/model/script/QuestState.java`) | Per-player quest state. Tracks quest variables, cond, state (CREATED/STARTED/COMPLETED), and persists to DB. |
| `AbstractSagaQuest` (`scripts/quests/AbstractSagaQuest.java`) | Abstract base for 3rd-class change quests (saga system). Handles NPC spawns, mob kills, class checks, and cinematic sequences. |
| `State` (`gameserver/model/script/State.java`) | Enum: `CREATED`, `STARTED`, `COMPLETED`. |

### Quest Lifecycle
1. **Construction**: `Quest(questId, description)` — registers quest items via `registerQuestItems()`, registers NPC events via `addStartNpc()`, `addTalkId()`, `addKillId()`, etc.
2. **Player Interaction**: `onEvent(event, npc, player)` — handles NPC dialog button clicks (HTML file references like `"30048-06.htm"`).
3. **State Management**: `QuestState` tracks `cond` (condition integer) and arbitrary string variables. `st.setCond(int, boolean playSound)` advances quest. `st.getState()` returns CREATED/STARTED/CPLETED.
4. **Completion**: `st.exitQuest(boolean repeatable, boolean playExitQuest)` — marks quest as completed; optionally repeatable.
5. **Persistence**: Quest state and variables are saved to DB via `createQuestInDb()`, `updateQuestInDb()`, `deleteQuestInDb()`.

### Key API Methods (Quest class)
| Method | Purpose |
|---|---|
| `addStartNpc(int...)` | Registers NPCs that can start this quest. |
| `addTalkId(int...)` | Registers NPCs that can be spoken to during this quest. |
| `addKillId(int...)` | Registers NPCs whose death triggers `onKill`. |
| `addAttackId(int...)` | Registers NPCs whose attack triggers `onAttack`. |
| `addFirstTalkId(int...)` | Registers NPCs for `onFirstTalk` (first interaction). |
| `addSkillSeeId(int...)` | Registers NPCs for `onSkillSee` (when a skill is used on them). |
| `addEnterWorldId(int...)` | Registers players for `onEnterWorld` (login). |
| `registerQuestItems(int...)` | Registers item IDs as quest items. |
| `giveItems(Player, int itemId, int count)` | Gives items to player. |
| `giveItems(Player, int itemId, int count, int enchantLevel)` | Gives enchanted items. |
| `takeItems(Player, int itemId, int amount)` | Takes items from player. |
| `rewardItems(Player, int itemId, int count)` | Gives reward items (with sound). |
| `giveItemRandomly(Player, int itemId, ...)` | Random drop with chance. |
| `addExpAndSp(Player, long exp, int sp)` | Grants experience and SP. |
| `playSound(Player, String sound)` | Plays a quest sound. |
| `startQuestTimer(String name, long time, Npc npc, Player player)` | Starts a quest-specific timer. |
| `startQuestTimer(String name, long time, Npc npc, Player player, boolean repeating)` | Repeating timer. |
| `cancelQuestTimer(String name, Npc npc, Player player)` | Cancels a specific quest timer. |
| `getRandom(int min, int max)` | Returns random int in range. |
| `getRandomEntry(T... array)` | Returns random entry from array. |
| `getQuestState(Player, boolean initIfNone)` | Gets or creates QuestState for player. |
| `getNoQuestMsg(Player)` | Returns default "no quest" HTML message. |
| `getAlreadyCompletedMsg(Player)` | Returns "already completed" HTML message. |

### QuestState API
| Method | Purpose |
|---|---|
| `getState()` / `setState(byte state)` | Gets/sets quest state (CREATED/STARTED/COMPLETED). |
| `getCond()` / `setCond(int value, boolean playSound)` | Gets/sets the current condition (quest step). |
| `setMemoState(int value)` / `getMemoState()` | Memo state for quest tracking. |
| `set(String var, String value)` / `getVar(String var)` | Arbitrary string variable storage. |
| `unset(String var)` | Removes a variable. |
| `startQuest()` | Transitions to STARTED state. |
| `exitQuest(boolean repeatable, boolean playExitQuest)` | Transitions to COMPLETED state. |

## Representative Quest Analysis

### Q00001_LettersOfLove
- **Quest ID**: 1
- **NPCs**: DARIN (30048), ROXXY (30006), BAULRO (30033)
- **Items**: DARIN_LETTER (687), ROXXY_KERCHIEF (688), DARIN_RECEIPT (1079), BAULRO_POTION (1080)
- **Reward**: NECKLACE (906)
- **Structure**: Linear progression through 4 conditions (cond 1→4). Player starts with DARIN, delivers letter to ROXXY, returns receipt to DARIN, delivers potion to BAULRO, returns for necklace.
- **Pattern**: Uses `onEvent` for dialog button handling, `onTalk` for NPC conversations with cond-based branching.

### Q00010_IntoTheWorld
- **Quest ID**: 10
- **NPCs**: REED (30520), BALANKI (30533), GERALD (30650)
- **Requirements**: Level ≥ 3, race DWARF
- **Items**: VERY_EXPENSIVE_NECKLACE (7574)
- **Rewards**: SOE_GIRAN (7559), MARK_OF_TRAVELER (7570)
- **Pattern**: Uses `Race.DWARF` check for class requirement. Linear cond progression (1→4). Uses `rewardItems` for final reward.

### AbstractSagaQuest
- **Purpose**: Base class for all 3rd-class change quests (quests 67–101).
- **Structure**: Uses arrays for NPCs (`_npc`), items (`_items`), mobs (`_mob`), class IDs (`_classId`), spawn coordinates (`_x`, `_y`, `_z`), and text (`_text`).
- **Mechanics**: Spawns special NPCs (Archon Hellisha), manages timed despawns (10-minute timer), handles class-specific checks via `QUEST_CLASSES` mapping.
- **Events**: Registers `addStartNpc`, `addAttackId`, `addSkillSeeId`, `addFirstTalkId`, `addTalkId`, `addKillId`.

## Common Patterns
1. **HTML-based dialog**: NPCs return HTML files (e.g., `"30048-06.htm"`) from `data/html/quests/`.
2. **Cond-based progression**: `st.setCond(int, true)` advances the quest; `st.getCond()` checks current step.
3. **Item exchange chains**: Quests commonly follow a pattern of give item → NPC transforms it → give next item.
4. **Level/race/class requirements**: Checked in `onTalk` under `State.CREATED`.
5. **Repeatable quests**: Use `exitQuest(true, ...)` for repeatable completion.
6. **Party quests**: Use `getRandomPartyMemberState()` for party-based quest progression.

## Gotchas / Refactor Candidates
- Quest scripts use hardcoded NPC/item IDs as integer constants — no type safety.
- HTML dialog files are referenced as raw strings — no compile-time validation.
- `QuestState` variables are untyped strings — prone to naming errors.
- `AbstractSagaQuest` uses parallel arrays for NPC/item/mob data — error-prone if arrays get out of sync.
- No centralized quest validation; broken quests may only surface at runtime.
- `giveItemRandomly` overloads have confusing parameter ordering (amount vs. min/max).
- Quest timers use string names — no type safety for timer identification.

## Notes (Resume Checkpoint)
- Read files:
  - `dist/game/data/scripts/quests/Q00001_LettersOfLove/Q00001_LettersOfLove.java`
  - `dist/game/data/scripts/quests/Q00010_IntoTheWorld/Q00010_IntoTheWorld.java`
  - `dist/game/data/scripts/quests/AbstractSagaQuest.java`
  - `java/org/l2jmobius/gameserver/model/script/Quest.java` (key method signatures)
  - `java/org/l2jmobius/gameserver/model/script/QuestState.java` (key method signatures)
- Still to read: individual quest scripts Q00002–Q00172 for pattern variations (combat quests, collection quests, event quests, subclass quests).
- Key structural findings: Quest system uses a base `Quest` class with event-driven callbacks. State is managed per-player via `QuestState`. Common patterns are consistent across quests.
- Next step: read a sample of quests from each category (combat, collection, subclass, event) to document pattern variations.