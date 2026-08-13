# 06 — model/actor template layer

Resume checkpoint
- Read files:
  - CreatureTemplate.java 1-620
  - NpcTemplate.java 1-220
  - PlayerTemplate.java 1-220
  - DoorTemplate.java 1-220
- Still to read:
  - Remaining NpcTemplate read ranges; stat/status classes.
- Key findings: template inheritance carries immutable base stats; subpackages enrich behavior and calculations separate from runtime state.
- Next: write structured template summary and proceed to stat/status.

---

## CreatureTemplate.java
Purpose: Base immutable data link containing every creature's default stat profile and geometry.
Fields/State: base STR/CON/DEX/INT/WIT/MEN; HP/MP/CP max/reg; PAtk/MAtk/PDef/MDef speeds, crit; attack range/shield values; element attacks resists; collision dims; move speeds by type; race.
Public API Surface: getters/setters for base stats, colliders, speeds; applies from StatSet.
I/O: none; StatSet loader fills fields; cached by data loaders.

## NpcTemplate.java
Purpose: concrete NPC runtime definitions including skills/drops/aggro/soulshots/fake players.
Fields/State: id/displayId, name/title, type/race/sex, weapon slots, exp/sp, aggression/ability flags, AI type/aggro, drop groups/seed manors, soul/spirit shots/champion/special siege parameters, npc variables, timers, multiplayer support.
Public API Surface: set/apply from StatSet, helpers for weapon/armor/critical AA.; synthetic drops/skills/hp regen; override enforcers like undying; name lookups; conditional quest flags.
Gotchas: FakePlayerHolder conflicts resolved by name lookup into CharInfoTable; undying default logic is now data-configurable; aggro capped by NpcConfig.MAX_AGGRO_RANGE.

## PlayerTemplate.java
Purpose: player class base data and level-curve arrays for HP/MP/CP per class.
Fields/State: class/creation points, per level arrays, slot-based defense maps, female collision values, safe fall height.
Public API Surface: class lookup, creation spawn point, set level upgain hp/mp/cp/regen; base slot defense values for empty slots; class-specific collision.
I/O: depends on ExperienceData max level; holds array sized by max level for fast lookup.

## DoorTemplate.java
Purpose: door structure configs and optional schedule/group relationships.
Fields/State: id/name/nodes/position, master child door events, targetable/default open state, open/time keys, clanhall linkage, openness state tracking, attackable/stealth flags.
Public API Surface: location getters, master event control, group/child relations, open/state accessors, clanhall association, stealth/attackable/wall flags.
Gotchas: _openTime/_randomTime only apply when OPEN_BY_TIME bit set; _openTime int defaults conditional; master event strings mapped to byte tri-state.

---
