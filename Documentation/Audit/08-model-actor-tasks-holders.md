# 08 — model/actor tasks & holders

Resume checkpoint
- Read files:
  - gameserver/model/actor/status top-level file list
  - gameserver/model/actor/tasks top-level file tree
  - gameserver/model/actor/holders top-level file tree
- Still to read:
  - Representative classes from each group if needed: CreatureStatus, AttackableStatus, PlayerStatus, tasks/player, holders/npc, holders/player.
- Key findings so far:
  - Status/tasks/holders form a modular secondary system split away from heavy Creature/Player code.
  - Tasks implement scheduled subtasks; holders store persisted/derived state; status centralizes HP/MP/battle effects.
- Next: write template map and move on.

---

## status/*
Purpose: per-subtype lifecycle bridge between raw values and visible effects, plus packet updates.
Public API Surface: reduceHp/reduceMp/recoverHp/recoverMp/cp updates, sendStatusUpdates, reset/reinit, death/fight hooks.
Control Flow: Creature constructs typed status; Action packets invoked on reductions; Death tie-ins to task_status end.
I/O: packets send every status update using listeners; event dispatches broadcast Effects.
Gotchas/Refactor Candidates: CreatureStatus -> PlayableStatus -> PlayerStatus includes duplicated death methods around stores/stands; might need unification or centralize death handler.

## tasks/*
Purpose: scheduled subtriggers on behalf actors; periodic pet feeding, fishing, sit/stand, illegal actions, cubic magic, notify AI, attack command channel timer, etc.
Public API Surface: schedule/cancel tasks via ThreadPool; hook execution from actor after effects; may mutate actor state on task exit.
Control Flow: Registration via constructors/handlers; periodic timing controlled via task scheduling, sometimes dependent conditions in run/tasks.
I/O: time-based event, packet sends when transitions triggered.
Gotchas/Refactor Candidates: File tree indicates heavy split; add a task? Reuse Generic ScheduledTask frameworks rather than per-task miniature classes.

## holders/*
Purpose: auxiliary state entities co-located with owner or references otherwise like summon request, macros, block list, duels, class info, recovery bonuses, cursed weapon, shortcuts, etc.
Public API Surface: save-load/map per holder; accessors from owner; DB persistence helpers elsewhere e.g data/sql; Event-backed invalidation.
Control Flow: Owner creates/loads holder references on init; DB-backed holders may be lazy loaded at playtime / login.
I/O: heavy IO via holder loaders; map references to owner object.
Gotchas/Refactor Candidates: Holders in model layer too easily reference managers or networking coupon pointers; make sure to separate pure data from gameplay action.

## Where to change X
- Adjust death handling? Status classes + Creature/Player doDie chain; prefer not new state subclass but centralized orchestrator hook.
- Add new scheduled behavior for actor? use holder or task depending whether persistent state; but avoid communal state/access on singleton holder across actors.
- Extend PvP/PK state? Use PlayerStat/PlayerStatus methods together with holders like CursedWeapon class-type holders.

---
