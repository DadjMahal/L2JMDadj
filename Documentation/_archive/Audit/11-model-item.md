# 11 — model/item system

Resume checkpoint
- Read files:
  - item tree listing
  - ItemTemplate.java 1-200
  - Weapon.java 1-200
  - Armor.java 1-200
- Still to read:
  - EtcItem, Henna, recipe, holders, instance, enchant, type enums.
- Key findings so far:
  - ItemTemplate is central static descriptor loaded from XML; Weapon/Armor add type-driven behavior.
- Next: structural map and dependency chains for 11.

---

## ItemTemplate.java
Purpose: Central static item definition loaded once from data XML.
Fields/State: id/name/type1/type2, weight/price, elemental params, reuse/cd, grade/drop animation flags, base stats and skill holders by action type.
Public API Surface: set(StatSet), getters for type/body/equip/reuse, skill/crystal/quest state, default prices; categorized by wearable/pet/ammo.
I/O: parsed from XML via StatSet; data holders/element/weapon listeners load from Same stat tree DB mapping equivalent.
Gotchas/Refactor Candidates: backbone for all items; several helper methods overlap helper templates; any change broad reaching.

## Weapon/Armor.java
Purpose: Weapon/Armor specializations; apply baseAttackType/shield stats/attack/bow behavior and armor bonuses.
Fields/State: weapon type/body/attack scope, psv/magic critical/soulshot reuse, ranged params; shield-related PvP immune flags, armor elemental stats and formulas hooks, enchant-at-4 skill hook for Armor.
Public API Surface: getItemType/mask, weapon category, critical, mpConsume/pss range bonuses; attack/set item grade from bodySee; equip methods chain to player inventory; include skills actions/equip configs.
I/O: skill parse from XML; grade search; itembased enumerations via ItemData caching.

## type enums
Purpose: Canonical type taxonomy for items/packets/managers.
Public API Surface: Grade/ArmorType/WeaponType/ActionType/CrystalType/MaterialType; itemLocation granularity; ItemProcessType for IO/DEVE trade action types; BodyPart + ShotType.
Gotchas: changing type enums change many switch blocks in data packets/managers/handlers; need cross-ref.

## holders/enchant/recipe/instance summary
Purpose: instance Item represents runtime equipped/world item; holders store drop/enchant/extract/unique/restore dataclasses; enchant system maps scroll/groups by grade; recipe items/manufacturing support crafting.
Public API Surface: casting rentals/restoration drop bag/item respect; instance support timers; listeners for uniqueness and drop protection.
Control Flow: item load -> ItemTemplate link -> instance -> inventory adaptation; slot checks trigger stat attentions/item skills; DB updates follow persistence paths.
Gotchas: many system couples to type enums; changing tiers require skilled tool-aware action.

## Where to change X
- Invent/crafting item behavior? Weapon/Armor/EtcItem type additions + holders cache.
- Balance weapon values? template XML + parser base stat constant, plus patched itemType-based calculations.
- New enchant behavior? enchant package scaffold; make sure to patch grade/skill registration.
- Change drop unique/restore? DropProtection/RestorationItemHolder + data loader.
- Adjust equipped state/durability? instance/player inventory links; bi-fold on inventory/action systems.

---
