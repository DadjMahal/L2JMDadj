# 10 — model/actor instances

Resume checkpoint
- Read files:
  - instance tree listing
  - Merchant.java 1-180
  - Teleporter.java 1-180
  - Warehouse.java 1-180
- Still to read:
  - Trainer, Fisherman, Doorman, ClanHallManager, auction/clan/gatekeeper variants, Decoy, Cubic, Pet, Monster.
- Key findings so far:
  - Instances implement concrete NPC gameplay types via Npc/Folk/Door subclassing.
  - Chat/bypass handlers are per-instance with HTML path conventions.
- Next: template-output direct iteration 10 docs.

---

## Instance inventory

- Folk leaf subclasses: Merchant, Teleporter, Warehouse, Trainer, Fisherman, Doorman, ClanHallManager, Auctioneer, RaceManager, Adventurer, SignsPriest, DawnPriest, DuskPriest, SchemeBuffer.
- Siege/utility: Door, CastleDoorman, ClanHallDoorman, DungeonGatekeeper, ControlTower, FlameTower, BroadcastingTower, SiegeFlag, Fence, StaticObject, QuestGuard, Guard, VillageMaster*, EffectPoint.
- Combat/quest/misc: Monster, FriendlyMob, FeedableBeast, TamedBeast, Decoy, Pet, BabyPet, Servitor, Summon, Cubic, Chest, Boat, RaidBoss, FestivalGuide.

## Merchant
Purpose: trade/buy/sell handler NPC.
Fields/State: leads to inventory list/price-restricted store controller; may keep price base lookup references in CastleManager.
Public API Surface: buy/sell handlers; shop-specific bypass hooks.
Control Flow: opens first buy/sell html windows; answer trade server packet resolves buy.sell packets to merchant actions.
I/O: network packets; Database-backed shop lists via castle/price managers; DB alter price settings.
Gotchas: Uses generic `Npc.isNpcInstanceType()/isMerchantInstanceType()`; via template name to enable buyType category, ensure checkout buy list; many if-blocks chain NPCId checks inline for sugar enumeration.

## Teleporter
Purpose: location teleportation NPC.
Fields/State: teleport target map/schedule; castle teleporter check cache.
Public API Surface: onBypassFeedback initiates teleport tasks/asks; showChatWindow resolved by castle ownership and siege state.
I/O: sends NpcHtmlMessage; schedules teleport tasks to ThreadPool; CastleManager checks owner.

## Warehouse
Purpose: warehouse/storage interface.
Fields: inherits from Folk without active state.
Public API: warehouse path id lookup; isWarehouse marker interface; invokes Npc base chat window for default if missing.
I/O: appends warehouse template path; no further DB hooks here.
Gotchas: nothing else front-end blocking; care that warehouse package files choose path exact id enumeration for family multi types.

## Where to change X
- Add new merchant-style or teleport behavior? subclass Merchant/Teleporter/Folk and hook bypass/chat.
- Change shop item lists? Price tables should remain managed; use zone/castle links to choose price table rather than hard-coding in `merchant.getMerchantPriceConfig()`.
- Add questfighting gatekeeping NPC? prefer Door/DungeonGatekeeper or Folks; if complex use Doorman pattern to unify logic.

---
