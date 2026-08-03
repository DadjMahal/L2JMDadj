# SESSION IN PROGRESS — B6: live quest proof

> If this file exists on resume: the last turn was cut off — resume "Current step". On clean completion
> fold into the final RuntimeLog + `git rm` this file.

## Goal
Prove an AI player interacts with the live quest system: enter world → Tutorial Q00255 auto-starts (DB row)
→ `RequestQuestList`(0x63) → parse `QuestList`(0x80) → confirm quest id 255 + `character_quests` row.
Spec: `Documentation/Audit/37-b6-live-quest.md`.

## Idempotent checklist
- [x] Audit-first: RequestQuestList(0x63) opcode-only; QuestList server = 0x80 (short count + [id,status]);
      character_quests schema; Tutorial auto-start via EnterWorld.loadTutorial; charId 2=CombatBot_01.
- [x] Document before code: wrote `Audit/37`.
- [ ] Implement `QuestProbe.java` (enter-world + RequestQuestList + parse QuestList + DB assert).
- [ ] `mvn clean compile -f AIPlayerEngine/pom.xml` → BUILD SUCCESS.
- [ ] Write `scripts/b6_quest_prove.sh`.
- [ ] Run live; paste QuestList parse + DB character_quests.
- [ ] RuntimeLog + sync KB (START_HERE, STATUS, SESSION_HANDOFF, TASKS quest rows, ai_progress_report); `git rm` this; commit.

## Current step
Audit + spec done. Next: code `QuestProbe.java`, build, run.

## If resuming: do this next
1. Finish `QuestProbe.java` (model on CombatProbe single-connection enter-world; send RequestQuestList 0x63;
   parse QuestList 0x80: short count + [int id][int status]; find 255).
2. Build, restart LS, run via setsid (`ai_combat_01 ... 127.0.0.1 7777`); verify id 255 in QuestList + DB row.
