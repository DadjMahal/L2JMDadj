# 🤝 AUDIT 07 — Integration architecture: Server API ⇄ Middleware ⇄ Player API

> Owner directive: *"Maybe we need API from both sides (Server side API in SourceCode) and
> Players Side API, and middleware to shake hands between AIs & L2Server. Flexible, smart,
> automated. Available from outside but absolutely secured."*

## 1. The architectural decision (read this twice)

**Do NOT fork the server Java core.** The owner's instinct for a server-side API is right — but
L2JMobius already ships an official plugin mechanism: **runtime-compiled datapack scripts**
(`dist/game/data/scripts/` — 89 AI scripts + 380 handlers live there, admin commands are
scriptable per SOURCE_CODE_MAP). A script gets the full server internals (World, players,
managers) with **zero core edits** — upstream updates keep applying, no merge hell.

So the three planes are:

```
                    Internet (TLS only via reverse proxy)
                      │ https://dash.<server>
        real players  │        browser
             :2106/:7777          │
                 │        ┌───────▼────────┐
                 │        │  ControlPlane  │  ← dashboard backend (the "middleware")
                 │        │  auth, audit,  │
                 │        │  orchestration │
                 │        └───┬────────┬───┘
                 │   EngineAPI│        │ServerBridge API
┌────────────────▼────────────▼──┐  ┌─▼──────────────────────────────┐
│ AIPlayerEngine (own JVM)       │  │ GameServer JVM (VANILLA CORE)  │
│ • behavior/* = PlayerAPI       │  │ scripts/custom/ServerBridge.*  │
│   (bots speak REAL client      │  │  = runtime-compiled datapack   │
│    packets — the ONLY gameplay │  │    script: server truth +      │
│    path, indistinguishable)    │◄─┤    admin relay, loopback-only) │
│ • knowledge/KnowledgeService   │  │ MariaDB: game schemas +        │
│ • learning/* (bandit+memory)   │  │  ai_souls schema (engine-owned)│
└────────────────────────────────┘  └────────────────────────────────┘
```

**Four non-negotiable principles**
1. **Client-parity gameplay only.** Bots play exactly like players (packets). The bridge NEVER
   moves a bot, kills a mob, or grants items for gameplay — that would be botting infrastructure
   turned cheat server; it also breaks the "smart engine" premise.
2. **ServerBridge is read-mostly + admin relay.** It exposes server *truth* (who's online,
   boss windows, auctions) and relays *whitelisted admin operations* (kick, announce, spawn)
   with an audit log for every command. Fail-closed: unknown command → refuse.
3. **ControlPlane is the single brain** the human admin talks to; it merges both APIs, owns
   auth, and can kill anything (per-bot disconnect, fleet stop).
4. **Zero-trust boundaries.** Every inter-process hop = loopback + bearer token; the only
   external door = reverse proxy with TLS + auth; game ports public as on any L2 server
   (that's how real players join); DB never exposed.

## 2. API surfaces (contracts)

**PlayerAPI (engine, Java — the bot capability contract after EP-* refactor):**
```
BotSession: connect(identity) · disconnect() · tick()
PlayerApi:  move(target) · attack(target) · talk(npc, dialogPath) · trade(...)
            chat(channel, text) · party(...) · store(sellList|buyList)
            inventory ops · skill(cast) · duel(accept/request) · fish() …
```
One façade, javadoc'd, stable — this is also the seam where future engines (or an LLM agent
experiment) could drive bots differently.

**EngineAPI (HTTP, loopback, token — extends today's DashboardApi):**
`GET /api/v1/*` (telemetry, frozen contract) **+** `POST /api/v1/admin/{spawn|disconnect|connect|setPolicy|stopFleet}`
(auth-gated; every call audited). External dashboard talks to this via the proxy.

**ServerBridge API (loopback:9300, token, from the datapack script):**
`GET /bridge/v1/status|players|raidbosses|auction|castles` · `POST /bridge/v1/admin`
`{cmd: kick|announce|...}` → executes through the server's own AdminCommandHandler so all
normal permission checks + logs apply. Bridge script < 300 lines, hot-reloadable like any
datapack script.

**Handshake protocol (ControlPlane ↔ both sides):** health ping + version + capability list on
connect; commands carry id+ts+token, responses carry id; every admin command appended to
`logs/control/audit.jsonl` (who/what/whom/result). A ControlPlane with a dead Engine or dead
Bridge degrades to read-only and says so loudly on the dashboard.

## 3. Security model ("available from outside, absolutely secured")

| Door | Exposure | Protection |
|---|---|---|
| 2106 / 7777 game ports | public (players) | server's own flood protectors; normal L2 ops |
| Dashboard/ControlPlane | public via **TLS reverse proxy only** (Caddy) | strong password/bearer + optional IP allowlist; brute-force ban |
| EngineAPI :8210 | loopback only | token required for admin routes |
| ServerBridge :9300 | loopback only | token + command whitelist + audit |
| MariaDB | loopback only | separate low-priv user for bridge reads |
| Bot accounts | – | unique passwords, no GM rights ever, rate-limited login |

Rules: secrets via env files (`secrets.env`, gitignored) — never in repo; `grep`-clean policy
from EP-6 extended to bridge/proxy configs; a SECURITY.md documents the matrix and the
incident kill-switches (stop fleet, revoke tokens, close proxy).

## 4. Task prompts for Deepseek-v4-flash

| ID | Task | Effort | Depends | Status |
|---|---|---|---|---|
| BR-1 | ServerBridge script v1 — server truth (read-only) | M | – | TODO |
| BR-2 | ServerBridge admin relay (whitelist + audit) | M | BR-1 | TODO |
| BR-3 | PlayerAPI façade + API.md (engine capability contract) | M | EP-4 | TODO |
| BR-4 | ControlPlane: merge engine+bridge, admin actions, audit trail | L | BR-2, DA-1 | TODO |
| BR-5 | External exposure: TLS reverse proxy + SECURITY.md + firewall matrix | S | BR-4 | TODO |
| BR-6 | LLM advisory integration (free tier, off hot path) | M | RS-4 | TODO |

### PROMPT BR-1
```
TASK BR-1: ServerBridge v1 — the server-side API (as a datapack script)

CONTEXT
- Audit 07 §1-2. GameServer core stays vanilla; scripts/custom/ is the sanctioned extension
  point (89 AI scripts already live there). Before coding, READ (read-only):
  scripts/custom examples + how scripts boot (ScriptEngine.xml registration) + World /
  WorldObject access patterns from 2 existing scripts; document in RuntimeLog.

GOAL
ServerBridge.java script: on load, starts a loopback-only HTTP server (com.sun.net.httpserver,
port 9300, 127.0.0.1, token from a server-side env/config file) serving:
/v1/status {uptime, onlineHumans, onlineBots, playerCap}, /v1/players[{name,level,class,race,
x,y,z,online,isAiGuess}], /v1/raidbosses[{id,alive,nextWindowEstimate}], /v1/castles[{id,owner,
siegeDate}], /v1/auction items count. Read-only. Register in Scripts.xml. No gameplay writes.

ACCEPTANCE
- Server boots clean with script loaded (paste GS log line); curl from engine box:
  /v1/status + /v1/players JSON pasted with a live human+bot visible; wrong-token → 401;
  non-loopback connect → refused (ss -tlnp paste); RuntimeLog.
```

### PROMPT BR-2
```
TASK BR-2: ServerBridge admin relay

CONTEXT
- BR-1 running. Relay = whitelisted admin ops through the server's OWN AdminCommandHandler so
  permission checks + server logs stay authoritative.

GOAL
POST /v1/admin {cmd, target, token}: whitelist ONLY [announce(kick=n), kick(player),
reload_scripts] — each mapped to its admin command string; every call appended to
ServerBuild/game/log/bridge_audit.log (ts, cmd, target, source-token-hash, result); unknown
cmd → 403 + audit entry. Rate limit 10/min. Add /v1/admin/whitelist GET for the ControlPlane.

ACCEPTANCE
- curl kick on a test char works via admin path (paste GS log showing the admin command ran);
  unknown cmd refused + audited (paste); no other write surface exists (code review note);
  RuntimeLog.
```

### PROMPT BR-3
```
TASK BR-3: PlayerAPI — the engine capability contract

CONTEXT
- Post EP-3/EP-4 the behavior stack is clean; external drivers (control plane, LLM experiments)
  need ONE documented door. Today FleetPlay drives internals directly.

GOAL
com.aiplayer.api.PlayerApi facade (thin, final): session lifecycle + all capability methods of
Audit 07 §2, implemented by delegating to BotSession/behavior packages. Documentation/
UpgradePlan/API.md: every method, semantics, failure modes, "stable since" version. Deprecate
direct internal calls from cli/ (FleetLauncher uses PlayerApi only). Add ApiContractTest that
reflection-asserts the façade surface matches API.md listing (drift = test failure).

ACCEPTANCE
- Tests green (+contract test); FleetLauncher contains zero behavior-package imports besides
  PlayerApi (grep paste); API.md ≤200 lines; RuntimeLog.
```

### PROMPT BR-4
```
TASK BR-4: ControlPlane — the handshake middleware

CONTEXT
- EngineAPI admin routes (DA-1) + ServerBridge (BR-2) exist; today's DashboardApi is telemetry-
  only. The ControlPlane is the merged backend the public dashboard uses.

GOAL
web/ControlPlane: composes EngineAPI + Bridge clients; endpoints: /api/v1/fleet (telemetry,
today's contract unchanged), /api/v1/ops/summary (server truth + fleet in one view),
POST /api/v1/admin/bots/{spawn,connect,disconnect,policy} → PlayerApi/EngineAPI,
POST /api/v1/admin/server/{announce,kick} → bridge relay; token auth on ALL admin; audit.jsonl
append per command; degradation: if bridge down → ops/summary shows engine-only + red banner
data; if engine down → server view only. Idempotent command ids (retry-safe).

ACCEPTANCE
- Tests green (+auth, +degradation, +idempotency tests); live: from a PROXIED external client,
  spawn 1 bot and disconnect it, then announce via server relay — all four audit lines pasted;
  RuntimeLog.
```

### PROMPT BR-5
```
TASK BR-5: Public-but-locked: TLS + SECURITY.md

CONTEXT
- Laptop host will be reachable from outside; only the dashboard may be public.

GOAL
1. Caddy (or nginx) config: dash.<host> → ControlPlane with TLS (self-signed ok for LAN,
   Let's Encrypt if public), bearer/basic auth, 5 fails/hour → ban 1h.
2. SECURITY.md: the §3 matrix as a table (door/exposure/protection), kill-switches (stop fleet,
   revoke token, close proxy, per-bot disconnect), incident checklist, secret-rotation steps.
3. Harden: EngineAPI binds 127.0.0.1 (remove old --bind 0.0.0.0 usage in scripts), bridge
   already loopback; ufw rules documented; secrets.env pattern + .gitignore entry.

ACCEPTANCE
- External device reaches https dashboard, auth works, wrong password bans (paste);
  direct :8210/:9300 from external refused (paste); SECURITY.md reviewed; RuntimeLog.
```

### PROMPT BR-6
```
TASK BR-6: LLM advisory integration (free tier, OFF the hot path)

CONTEXT
- RS-4 built the offline coach. CO-4 needs chat assist. Laptop budget: LLM = rare, batched,
  cached; free tiers (Gemini/OpenAI-compatible) or local Ollama.

GOAL
com.aiplayer.llm.LlmAdvisor: one interface, env-configured provider (none|ollama|openai-
compatible), strict budget (N calls/hour, token cap, 4s timeout, hard fallback to canned
responses); uses: (1) nightly coach report (RS-4), (2) chat reply DRAFTING inside CO-4 filters,
(3) weekly "soul reflection": 20-line summary per bot from its memories → adjusts persona
traits ±10% (bounded drift). NOTHING in combat/perception paths.

ACCEPTANCE
- Tests green (+budget/timeout/fallback tests with a mock server); live: bot answers a whisper
  through provider OR falls back cleanly with provider=none (both transcripts pasted);
  RuntimeLog.
```
