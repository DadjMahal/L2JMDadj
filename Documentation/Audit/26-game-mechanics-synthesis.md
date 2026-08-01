# Game Mechanics Synthesis Audit (Iteration 26)

## Purpose
Synthesis of core combat formulas, skill execution, movement, timing, and status effect mechanics across `gameserver/model/stats/`, `gameserver/model/skill/`, `gameserver/model/actor/`, and `gameserver/model/effects/`.

## Core Stats System

### Stat Enum (`model/stats/Stat.java`)
Defines all game statistics as enum constants with string aliases. Categories:
| Category | Key Stats |
|---|---|
| HP/MP/CP | `MAX_HP`, `MAX_MP`, `MAX_CP`, `REGENERATE_HP_RATE`, `REGENERATE_CP_RATE`, `REGENERATE_MP_RATE`, `HEAL_EFFECT` |
| Attack & Defense | `POWER_DEFENCE` (pDef), `MAGIC_DEFENCE` (mDef), `POWER_ATTACK` (pAtk), `MAGIC_ATTACK` (mAtk), `POWER_ATTACK_SPEED` (pAtkSpd), `MAGIC_ATTACK_SPEED` (mAtkSpd) |
| Critical | `CRITICAL_DAMAGE` (critDmg), `CRITICAL_DAMAGE_POS` (critDmgPos), `CRITICAL_DAMAGE_ADD` (critDmgAdd), `MAGIC_CRIT_DMG` (mCritPower), `CRITICAL_RATE` (critRate) |
| Rates | `EVASION_RATE` (rEvas), `ACCURACY_COMBAT` (accCombat), `SHIELD_RATE` (rShld), `BLOW_RATE` (blowRate), `MCRITICAL_RATE` (mCritRate) |
| PvP/PvE | `PVP_PHYSICAL_DMG`, `PVP_MAGICAL_DMG`, `PVP_PHYS_SKILL_DMG`, `PVP_PHYSICAL_DEF`, `PVP_MAGICAL_DEF`, `PVP_PHYS_SKILL_DEF`; `PVE_PHYSICAL_DMG`, `PVE_PHYS_SKILL_DMG`, `PVE_BOW_DMG`, `PVE_MAGICAL_DMG` |
| Resistances | `FIRE_RES`, `WIND_RES`, `WATER_RES`, `EARTH_RES`, `HOLY_RES`, `DARK_RES`, `MAGIC_SUCCESS_RES` |
| Element Power | `FIRE_POWER`, `WATER_POWER`, `WIND_POWER`, `EARTH_POWER`, `HOLY_POWER`, `DARK_POWER` |
| Movement | `MOVE_SPEED` (runSpd) |
| Basic Stats | `STAT_STR`, `STAT_CON`, `STAT_DEX`, `STAT_INT`, `STAT_WIT`, `STAT_MEN` |
| Reuse | `ATK_REUSE`, `P_REUSE`, `MAGIC_REUSE_RATE`, `DANCE_REUSE` |
| Proficiencies | `CANCEL_PROF`, `SKILL_CRITICAL`, `SKILL_CRITICAL_PROBABILITY` |
| Limits | `WEIGHT_LIMIT`, `WEIGHT_PENALTY`, `INV_LIM`, `WH_LIM`, `FREIGHT_LIM`, `P_SELL_LIM`, `P_BUY_LIM`, `REC_D_LIM`, `REC_C_LIM` |
| MP Consumption | `PHYSICAL_MP_CONSUME_RATE`, `MAGICAL_MP_CONSUME_RATE`, `DANCE_MP_CONSUME_RATE`, `BOW_MP_CONSUME_RATE`, `MP_CONSUME` |
| Vitality/Death | `VITALITY_CONSUME_RATE`, `REDUCE_EXP_LOST_BY_PVP`, `REDUCE_EXP_LOST_BY_MOB`, `REDUCE_EXP_LOST_BY_RAID`, `REDUCE_DEATH_PENALTY_BY_PVP`, `REDUCE_DEATH_PENALTY_BY_MOB`, `REDUCE_DEATH_PENALTY_BY_RAID` |
| Shield | `SHIELD_DEFENCE` (sDef), `SHIELD_DEFENCE_ANGLE` (shieldDefAngle) |
| Other | `BREATH`, `FALL`, `BUFF_VULN`, `DEBUFF_VULN`, `CANCEL_VULN`, `MOVEMENT_VULN`, `DAMAGE_ZONE_VULN`, `FISHING_EXPERTISE` |

### Calculator System (`model/stats/Calculator.java`)
- Each Stat has a `Calculator` instance with a list of `AbstractFunction` objects.
- Functions are applied in priority order: `FuncDiv`, `FuncMul`, `FuncAdd`, `FuncSub`, `FuncSet`, `FuncEnchant`, `FuncEnchantHp`, `FuncShare`.
- `FuncShare` allows stats to be shared from pet to owner.
- Formulas are computed via `Calculator.calc(Creature, Double baseValue)` which iterates all functions.

## Combat Formulas (`model/stats/Formulas.java`)

### Physical Combat
| Method | Purpose |
|---|---|
| `calcPhysDam(attacker, target, skill, shld, crit, ss)` | Calculates physical attack damage. PAtk, PDef, crit multiplier, shield defense, skill modifiers. |
| `calcBackstabDamage(attacker, target, skill, shld, ss)` | Backstab damage with position bonus. |
| `calcBlowDamage(attacker, target, skill, shld, ss)` | Blow skill damage (Back Blow, etc.). |
| `calcHitMiss(attacker, target)` | Hit/miss based on accuracy vs. evasion. |
| `calcCrit(attacker, target)` / `calcCrit(attacker, target, skill)` | Physical critical hit chance. |
| `calcShldUse(attacker, target, skill)` | Shield defense. Returns FAILED/SUCCEED/PERFECT_BLOCK. |
| `calcPhysicalSkillEvasion(creature, target, skill)` | Evasion for physical skills. |
| `calcPAtkSpd(attacker, target, rate)` | Attack speed calculation. |
| `calcAtkSpd(attacker, skill, skillTime)` | Attack speed with skill time modifier. |
| `calcAtkBreak(target, dmg)` | Chance to break (aggro reset) on hit. |

### Magic Combat
| Method | Purpose |
|---|---|
| `calcMagicDam(attacker, target, skill, shld, sps, bss, mcrit)` | Magic damage. MAtk, MDef, element, shield, crit. |
| `calcMCrit(mRate)` | Magic critical hit chance. |
| `calcMagicAffected(actor, target, skill)` | Whether a magic skill affects the target. |
| `calcMagicSuccess(attacker, target, skill)` | Magic skill success chance (resistance-based). |
| `calcSkillMastery(actor, skill)` | Chance for skill mastery (no MP consumption). |
| `calcManaDam(attacker, target, skill, shld, sps, bss, mcrit)` | Mana damage calculation. |

### Utility Formulas
| Method | Purpose |
|---|---|
| `calcFallDam(creature, fallHeight)` | Fall damage calculation. |
| `calcAttributeBonus(attacker, target, skill)` | Elemental bonus (2x for weak, 0.5x for strong). |
| `calcEffectSuccess(attacker, target, skill)` | Status effect success chance. |
| `calcBuffDebuffReflection(target, skill)` | Chance to reflect buffs/debuffs. |
| `calcDamageReflected(attacker, target, skill, crit)` | Damage reflection calculation. |
| `calculateSkillResurrectRestorePercent(baseRestorePercent, caster)` | Resurrection HP restore percentage. |
| `calcLvlBonusMod(attacker, target, skill)` | Level difference bonus modifier. |
| `calcFestivalRegenModifier(player)` | Seven Signs festival regen modifier. |
| `calcSiegeRegenModifier(player)` | Siege-based regen modifier. |

### Regeneration
| Method | Purpose |
|---|---|
| `getRegeneratePeriod(creature)` | HP regen task period (3 seconds). |
| `calcHpRegen(creature)` | HP regeneration per period. |
| `calcMpRegen(creature)` | MP regeneration per period. |
| `calcCpRegen(player)` | CP regeneration per period. |

### Character Creation
- `addFuncsToNewCharacter(creature)` — applies base stat formulas to newly created characters.
- `getStdNPCCalculators()` — returns standard calculator set for NPCs.
- `getStdDoorCalculators()` — returns standard calculator set for doors.

## Skill System (`model/skill/`)

### Skill Class (`model/skill/Skill.java`)
- **Constructor**: `Skill(StatSet set)` — parses skill data from XML/SQL.
- **Key Properties**: ID (`getId()`), level (`getLevel()`), display ID (`getDisplayId()`), MP consume (`getMpConsume()`), HP consume (`getHpConsume()`), cast range (`getCastRange()`), reuse delay (`getReuseDelay()`), target type (`getTargetType()`), magic flag (`isMagic()`), debuff flag (`isDebuff()`), toggle flag (`isToggle()`), hero skill flag (`isHeroSkill()`), effect point (`getEffectPoint()`).
- **Execution**: `activateSkill(Creature caster, Collection<WorldObject> targets)` — applies effects to targets.
- **Channeling**: `getChannelingSkillId()` — for channeling skills.
- **Area checks**: `checkForAreaOffensiveSkills()` — checks if area skills can be used offensively.

### BuffInfo (`model/skill/BuffInfo.java`)
- Tracks active effects on a creature: caster, skill, duration, remaining time.
- Lifecycle: created when effect applied, ticks over time, removed on expiration or dispel.

### Effect System (`model/effects/`)
- `EffectType` enum defines all effect types (e.g., `HEAL`, `DAMAGE`, `BUFF`, `DEBUFF`, `SUMMON`, `TELEPORT`, `INVISIBLE`, `CONFUSION`, `SLEEP`, etc.).
- Effects are applied via `AbstractEffect` subclasses.
- Each effect has its own `onStart`, `onExit`, and `onActionTime` (tick) methods.

## Movement & Timing

### Movement
- `MOVE_SPEED` stat calculates run/walk speed.
- Movement uses pathfinding with waypoint navigation.
- `LocationUtil` provides distance and position calculations.
- Falling uses `calcFallDam` with height-based damage.

### Timing
- Attack speed modifiers affect animation and cooldown timing.
- `calcAtkSpd` and `calcPAtkSpd` determine attack/reload intervals.
- Skill reuse delays are modified by `MAGIC_REUSE_RATE`, `P_REUSE`, `DANCE_REUSE`.
- Regeneration ticks every 3 seconds (`HP_REGENERATE_PERIOD`).

## Status Effects & Conditions
- Effects use `EffectType` enum for categorization.
- Success chance calculated via `calcEffectSuccess` and `calcMagicSuccess`.
- Critical chance via `calcCrit` (physical) and `calcMCrit` (magic).
- Shield defense via `calcShldUse` (3 outcomes: failed, normal, perfect block).
- Damage reflection via `calcDamageReflected` and `calcBuffDebuffReflection`.

## Gotchas / Refactor Candidates
- `Formulas` is a large monolithic class (~1700 lines) — difficult to maintain individual formula changes.
- Physical and magic damage calculations are separate methods with overlapping logic — could share more code.
- `Stat` enum mixes gameplay stats with config-like limits (inventory limits, MP consumption rates) — conceptual mixing.
- Some formula methods take many boolean parameters (e.g., `calcPhysDam` has 5 params) — error-prone.
- Formula constants (e.g., shield defense types) are defined as byte constants in `Formulas` — could be an enum.