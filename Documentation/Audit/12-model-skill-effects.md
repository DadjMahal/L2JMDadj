# 12 — model/skill & effects

Resume checkpoint
- Read files:
  - gameserver/model/skill/Skill.java top
  - gameserver/model/skill/BuffInfo.java 1-440
  - gameserver/model/skill/AbnormalType.java full
  - gameserver/model/skill/EffectScope.java
  - gameserver/model/effects/AbstractEffect.java 1-240
  - gameserver/model/effects/EffectFlag.java full
  - gameserver/model/effects/EffectType.java full
- Still to read:
  - Remaining skill system: handlers, enchant skill learn, skill use holder, calculators.
- Key findings so far:
  - Skills are data-driven by AbnormalType/EffectType handlers via reflection.
  - BuffInfo is runtime container for one skill instance with effect list + tick tasks + stat funcs.
- Next step:
  - Write structured mapping, then start 13 clan/siege.

---

## Skill.java
Purpose: Runtime skill object bound to template and owner; represents one learned/usable skill instance.
Fields/State: id/level/owner, reuse timestamps, mp/hp/cp consume, cast/attack/reuse delays, target/effect scope, item consumption, chance/short/SS/Sp consumption, channel info, references by holder.
Public API Surface: getters for meta, reuse calculations, consume checks, cast time/hit time/attack delay computations, ownership/type checks, random/spellbook/hope flags, target scope whether self/target/area/party/clan/etc.
Control Flow: Constructed from template/stat set; aggregated via SkillHolder/SkillLearn/SkillUseHolder from data loaders.
I/O: indirect through effect handlers; mana/hp/cp changes on owner or target.
Gotchas/Refactor Candidates: large class, central bottleneck; value object with many getters; reuse/consumption logic leaked into skill class vs separate calculators.

## BuffInfo.java
Purpose: Hold one active skill effect session on a target creature.
Fields/State: effector, effected, skill, list of AbstractEffect, task map, abnormal time, period ticks, finish type, in-use flag.
Public API: initializeEffects schedules tick tasks or simple finish tasks; stopAllEffects removes funcs and cancels tasks; onTick delegates to effect.onActionTime and toggle cancel; finishEffects cancels and removes stats; removeStats/e移除 abnormal effects.
Control Flow: Linked to EffectList on effected creature; added when skill lands; removed when finish/expire/dispel.
I/O: thread-pool scheduled tasks; stat add/remove on effected; packet/visible social messages when lands.
Gotchas/Refactor Candidates: mutable shared state; cancellation semantics differ between normal/removed/toggle; need clean lifecycle state machine.

## AbstractEffect.java
Purpose: Base effect implementation with lifecycle hooks and optional stat functions.
Fields/State: attach/apply condition, func templates, name, ticks.
Public API: createEffect resolves handler by name via EffectHandler using reflection; canStart/onStart/onActionTime/onExit; getStatFuncs builds functions; instant flag.
Control Flow: instant effects execute once during initializeEffects; continuous effects schedule ticks.
Gotchas: reflection-based handler lookup; condition chains per effect make behavior composition implicit.

## EffectFlag.java / EffectType.java
Purpose: EffectFlag is bitmask enum for abnormal visual/state flags; EffectType classifies effect behavior.
Public API: getMask for bitwise checks.
Control Flow: used by Creature/Status/Update packet logic and effect filtering.
Gotchas: enum ordinal stability matters for serialized packets.

## EffectTickTask / EffectTaskInfo
Purpose: Scheduled task wrapper for continuous effect ticks.
Fields/State: BuffInfo + effect reference; periodic tick logic; cancellation support.
Control Flow: run calls BuffInfo.onTick(effect); finish path stops skill.

## AbnormalType.java
Purpose: Client-facing abnormal state enum for visual effects/packets.
Public API: enum constants only; clients use string mapping.

## Where to change X
- Add new effect type? Add EffectType + AbstractEffect subclass, register in EffectHandler.
- Adjust buff timing? BuffInfo tick ratio via PlayerConfig.EFFECT_TICK_RATIO.
- Change abnormal visual? AbnormalType mapping plus server packets.
- Hook stat changes? AbstractEffect.getStatFuncs + FuncTemplate loader.
- Toggle/conditional effect? AbstractEffect condition + canStart gate.
- Track active effects? Creature.getEffectList() + BuffInfo state fields.

---
