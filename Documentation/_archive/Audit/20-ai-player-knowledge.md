# 20 — AI player knowledge base

Resume checkpoint
- Read files:
  - representative network, world, client packet, manager, and data classes
- Still to read:
  - full gameserver/ai intent/action tree if deeper AI behavior needed
- Next:
  - write concrete observable/action lists for AI players and mark advance.

---

## Observable state from a connected client perspective
- Connection: login server handshake -> session keys -> server selection -> char select -> enter world -> IN_GAME state.
- World state: nearby players/NPCs/objects via region visibility; packets include CharInfo/NpcInfo/SpacedObject/Attack/Move/etc.
- Character state: HP/MP/CP, buffs/effects, inventory, quest state, skills cooldowns, target, location/heading, speed, weight, karma/PK status, aggression state, zone effects.
- Social: party/command channel/clan/alliance/friend/block lists, chat channels, multiplayer stores.
- GM/access: access level permissions restrict commands, visibility, admin menu entries from AdminData/ClientPacket checks.

## Possible actions from client packets/commands
- Movement: MoveToLocation; actions/attack/use item/skill/chat/trade/store/craft/recipe book/henna quest/macro shortcuts.
- System: logout/disconnect/reconnect; NPC interaction via action click/shift/bypass; community board; user commands/admin commands.
- Gameplay: attack/use skill on target; pick up/consume/drop/sell/buy items; sit/stand/run/wait; teleport via GM; uncancel effects.

## Packet sequences for common actions
- Login: ProtocolVersion -> AuthLogin -> ServerList -> ServerLogin -> CharSelect/CharCreate/CharRestore/CharDelete -> EnterWorld.
- Attack: AttackRequest -> MagicSkillUse/AutoAttackStart -> StatusUpdate/MagicSkillCanceled/Damage packets.
- Move: MoveToLocation -> StopMove or ValidateLocation -> follow Region handshake broadcast.
- NPC shop: NpcHtmlMessage with bypass/button -> RequestBuyItem/RequestSellItem -> ItemList/InventoryUpdate.

## World query APIs usable by AI
- World.java: getPlayers/getPlayer(objectId)/findObject/getNpc/getAllGoodPlayers/getAllEvilPlayers.
- Zone/manager classes: zone checks by id/name/type; clan/siege/town state via managers.
- CharInfoTable: id by name.

---
