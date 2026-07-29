# 07 — model/actor stat

Resume checkpoint
- Read files:
  - gameserver/model/actor/stat/* tree
  - CreatureStat.java 1-220+
  - PlayerStat.java 1-220
- Still to read:
  - Remaining NpcStat/PlayableStat/SummonStat and all status classes.
- Key findings so far:
  - Stat layer operates as per-actor calculator set composed from template/base + item/class/ racial/class-balance/zone modifications.
  - PlayerStatus applies PvP/duel/summon damage transfer, mana shield, balance, abnormal reduction logic.
- Next: write structured stat summary; state-hold log already saved.

---

## CreatureStat.java
Purpose: Base stat controller for living actors; exposes `calcStat` and many helper computation methods on top of Calculators.
Fields/State: element magic constants; references to configs/Formulas/Calculator cache.
Public API Surface: elemental attack/defense queries, accuracy/evasion, critical/magic crit, run/walk/swim/fly speeds, skill-specific calc helpers, absorb/reflect traits, damage modifier methods.
I/O: indirect via Calculator execution path; Zone influence.
Gotchas/Refactor Candidates: large delegated class; many calc shortcut methods that mirror Stat enum; easy to miss cross-effects without careful review.

## PlayerStat.java
Purpose: Player-specific stat computation and PvP economy modifiers.
Fields/State: references to PvpConfig, PvpRewardItemConfig, PvpTitleColorConfig, GeneralConfig, FactionSystemConfig.
Public API Surface: calcPvPBonus, signets/reputation/reward/title handlers, adjustPvPCount, clan-specific bonuses, boss rewards, PvP death/murder kill streak logic.
I/O: stats subsystem for player-only calculations; triggers broadcast status updates.
Gotchas/Refactor Candidates: PvP logic intertwined tightly in stat; stat-tuning modifications upgrade turn.

## Status classes

### CreatureStatus.java
Purpose: base lifecycle for HP/MP values refresh and death logic for all creatures.
Fields/State: hp/mp/cp counters/from template; broadcast watchers; timers; battle resurrection logic.
Public API Surface: reduce/damage/block/heal/mp reduce, send packets to observing listeners, death methods overloaded by subtypes.
I/O: packet sends StatusUpdate to listeners; broad event notifications.

### PlayerStatus.java
Purpose: player-specific death/damage reception effects plus interaction flows.
Fields/State: current/max health/mana/cp; duel state; store/sitting status; MP/TRANSFER_DAMAGE_PERCENT computations; event listeners.
Public API Surface: reduceHp with PvP/duel/summon/internal transfer/mp shield logic; send damage effects; stand up; reset/update CP/MP/HP; close stores on damage.
I/O: network status messages; invokes other stat computations; Manager dispatches to summons.
Gotchas/Refactor Candidates: death/damage specialists nested inside status; many config branches may stack damage effects incorrectly under complex synchronization issues if not carefully reviewed.

## Where to change X
- Scale combat formulas? -- adjust Stat calculators + CreatureStat calc methods.
- Add class-specific or faction-specific combat boosts? -- PlayerStat or polymorphic formulas therein.
- Modify how HP/MP are drained or transfer to summon? -- CreatureStatus abstract methods + PlayerStatus specific reduceHp branch.
- Modify death behavior for playable/nonplayable? -- CreatureStatus/doDie chain; avoid injecting too much UI physics into stat.
- Modify boss/raid specific behavior flags? -- templates/undying from NpcTemplate; rather than stat injection.
---
