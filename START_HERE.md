# 🚀 START HERE — L2JM (read this first, every session)

> 📖 **For the FULL project history & near-context, read `Documentation/SESSION_HANDOFF.md`** (once per session, ~2–3k tokens). This file (below) is the fast orientation; the handoff is the depth.
> Rules live in `AGENT_ONBOARDING.md`; deep context is read **on demand** via the routing table.

## ⚠️ Resume check (do this first)
If `SESSION_IN_PROGRESS.md` exists at repo root → the last session was **rate-limited mid-work**.
Read it FIRST and resume the "Current step" (don't pick a new task). If absent, continue below.


## Project (one line)
L2JMobius **Interlude** server (`/home/volodro/L2JM`) + external-socket **AI Player Engine** (`AIPlayerEngine/`)
that connects as real client sockets — **no server code modifications**.

## Honest current state *(source: `AIPlayerEngine/AIStatusLogs/ai_progress_report.txt`)*
- ✅ Server **UP**: LoginServer :2106, GameServer :7777 (since Jul 31).
- ✅ Source-code **audit complete** — iterations 1–30 in `Documentation/Audit/`.
- ✅ AIPlayerEngine **compiles** (155 files). Bootstrap + telemetry + perception scaffolding done.
- 🔴 AI is **NOT live-verified**: "CombatAI/QuestAI/MerchantAI/SocialAI implemented with **mock data**,
  not connected to real gameplay. Awaiting protocol packet parsing to complete." **No AI players proven online.**
- 🔴 Fabricated status docs were quarantined (see Blockers).

## Current phase / next task
- **Current phase:** **B4 DONE — live NPC combat PROVEN (2026-08-03).** An AI player attacked a real Wolf/Keltir
  monster (18 server `ATTACK` packets) and **gained 105 exp / leveled 1→2**. PvP / quest / trade proofs (B5+) are next.
- **Stream A DONE (2026-08-03):** A1 cold-start test (17/17 PASS; bootup ~73k→~1.3k tokens); A2 `real_status.sh` fix; A3 `count_ai_players.sh` fix (real DB: 25 registered, 0 online).
- **B1 DONE:** AI credentials valid (DB pw → Base64(SHA1); connectPlayer bug fixed).
- **B2 DONE (compiles, NOT live-proven):** real L2J login handshake — `LoginCrypt` + `L2JProtocol` rewrite; spec in `Audit/31-login-protocol-handshake.md`.
- **B3 — LIVE-PLAYER PROOF DONE (2026-08-03):** 1 AI player (`CombatBot_01`/`ai_combat_01`) proven **online=1** in the `gameserver` DB via the full external socket flow.
  - **Phase 0 (done):** Init decodes — root cause was **little-endian Blowfish ≠ JDK Blowfish**; ported server engine → `protocol/crypt/BlowfishEngine.java`; `Audit/32-init-decode.md`.
  - **Phase 1 (done):** login-server auth: Init → AuthGameGuard → RequestAuthLogin → **LoginOk** → ServerList → RequestServerLogin → **PlayOk**; SessionKey captured. Client packets use **session key + checksum** + **self-inclusive size**. `Audit/33-phase1-login-auth.md` + `scripts/b3_login_probe.sh`.
  - **Phase 2 (done):** GameServer enter-world: ProtocolVersion(746) → KeyPacket → **AuthLogin** → CharSelectInfo → **CharacterSelect** → CharSelected → `setOnlineStatus(true,true)` → **online=1** live-verified. `Audit/34-phase2-enter-world.md` + `scripts/b3_enter_world_prove.sh`.
- **B4 — LIVE NPC COMBAT PROVEN (2026-08-03):** `CombatProbe` (login → enter-world → **EnterWorld(0x03)** →
  NPC_INFO scan → **Action**(0x04)+**AttackRequest**(0x0A)) attacked a real Talking Island Wolf/Keltir monster:
  server broadcast **18 `ATTACK`(0x05)** hits; DB `exp` **0→105**, **level 1→2**, curHp 126→145. Spec + evidence:
  `Audit/35-b4-live-npc-combat.md`; reproduce: `scripts/b4_combat_prove.sh`.
  - Key discoveries: client MUST send **EnterWorld** to spawn; NIO SocketChannel ignores setSoTimeout (hang) —
    CombatProbe uses classic Socket; **PacketLogger.parseNpcInfo is off-by-one** (needs fix, Stream C);
    the 25 `ai_%` chars spawn in the **void** at (16600,17000,434) and die — relocated CombatBot_01 to the Wolf zone.
- **Next tasks (B5+):** live **PvP**, quest, trade proofs — CombatAI/QuestAI/MerchantAI/SocialAI still run on
  mock data; now they can be wired to the real packets demonstrated by `CombatProbe`.

## Blockers / open issues
1. **✅ B3 RESOLVED (2026-08-03) — 1 AI player online.** Full external socket flow proven (no L2JM server source changed):
   - Phase 0: server **Blowfish is little-endian**, not JDK-compatible → ported `protocol/crypt/BlowfishEngine.java` (`Audit/32`).
   - Phase 1: client login packets use **session key + checksum** + **self-inclusive size** (`Audit/33`).
   - Phase 2: GS ProtocolVersion(746) → KeyPacket → AuthLogin → CharacterSelect → **online=1** live-verified (`Audit/34`, `scripts/b3_enter_world_prove.sh`).
2. **✅ B4 RESOLVED (2026-08-03) — live NPC combat PROVEN** (`CombatProbe`, `Audit/35`): AI player attacked a
   real Wolf/Keltir monster → 18 `ATTACK`(0x05) hits + **exp 0→105, level 1→2**. **B5–B10 (live PvP, quest,
   trade proof) next** — the AI engines still run on mock data and need wiring to the real packets now proven.
3. **Fabricated docs quarantined** in `Documentation/_archive_fabricated/` (`PHASE2_COMPLETE.md`, `README-MAGIC.md`, `REFACTORED_ROADMAP.md` (333-task), `WorkLog/SMARTPROJECT.md`, 2 fake reports). **Trust only** `ai_progress_report.txt`, `MORNING_REPORT_*.txt`, `real_status.sh`.
4. **DB names:** accounts are in the **`loginserver`** DB; characters in **`gameserver`**. `real_status.sh` uses `sudo mysql -u root gameserver`.
5. Tasks 54 & 63 downgraded to `in_progress` — their tests contain `assertTrue(true)` (fake); need real assertions (Stream C).

## Reality check — run these and paste the output
```bash
git -C /home/volodro/L2JM status --short | head
ss -tlnp 2>/dev/null | grep -E '2106|7777'   # expect both LISTEN
/home/volodro/L2JM/AIPlayerEngine/AIStatusLogs/real_status.sh
```

## Routing table (read ONLY the file your task touches)
| Task touches | Read first |
|---|---|
| Combat AI | `engine/CombatAI.java`, `Audit/15-combat-ai.md`, `Audit/04-gameserver-network.md` |
| Network / protocol | `Audit/01-commons.md`, `Audit/04-gameserver-network.md`, `protocol/L2JProtocol.java` |
| Perception | `Audit/PART2-01-perception-systems.md`, `protocol/PacketLogger.java` |
| Quest system | `Audit/30-quest-progression.md`, `engine/QuestAI.java` |
| Docs / workflow only | `AGENT_ONBOARDING.md`, `Documentation/WORKFLOW.md` |
| Anything L2JMobius protocol | **audit-first** — read the matching `Audit/*.md` before writing code |

## Rules (6 hard rules — full text in `AGENT_ONBOARDING.md`)
1. Verify before claim (no "working" without pasted output). 2. No fake logs. 3. Usage validation (grep for callers).
4. Audit-first. 5. Document before code. 6. Leave cleaner than you found it.
