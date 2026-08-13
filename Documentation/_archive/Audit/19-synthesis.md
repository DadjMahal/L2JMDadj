# 19 — Synthesis: how to change things across packages

Purpose: give AI users change-paths across the codebase.

## Change-map summary
- Connection/auth: packets in login + gameserver/network + configs in gameserver/config + DB in commons/database.
- Runtime gameplay: model/actor + model/skill/effects + handlers + taskmanagers + event framework.
- World data: data loaders in data/xml + data/sql + manual data folders `data/`.
- Scripted behavior: dist/game/data/scripts + gameserver/scripting engine.
- Admin/GM: handlers/admin + chat + commands + login/game config toggles.

## Where to start by feature
- New NPC behavior: model/actor/instance + AI + template/data.
- New skill/effect: model/skill + effects + handlers/EffectHandler + packet UI + system message text.
- New clan/siege/residence rule: model/clan/siege/residences + managers/data.
- New quest: dist/game/.../scripts/quests + script framework.
- New config: gameserver/config specific class + ConfigLoader.load + default `Server.ini` + use in manager/handler.
- Network issue: commons/network + gameserver/network or loginserver/network.

## Hotspots for future work
- manager init order in GameServer main; dependency graph is implicit.
- EventDispatcher listeners can terminate actions; search events/returns TerminateReturn for rule.
- IO paths prefer `datapack root` driven by ServerConfig.DATAPACK_ROOT + `data/`.
- Session/login cracking touch login + gameserver session bridge.

---
