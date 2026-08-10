# Audit 43 — Phase0 runtime integration (Tasks 1-11 now reachable from the engine)

**Date:** 2026-08-10 · **Branch:** master (on top of d2cd7607) · **Suite:** 141/141 green

## Why this pass existed
After the build-level finish (Audit/42, commit d2cd7607) the phase0 package
compiled and was unit-tested, but only THREE classes were reachable at runtime
(`BotSnapshot`, `Phase0Wiring`, `TargetSelector` — from `examples/Phase0Driver`).
Everything else was dormant. This pass gives the whole upgrade a real, gated
runtime seam without changing default behavior.

## What was wired (additive, config-gated, default OFF)

| New file | Role |
|---|---|
| `com.aiplayer.engine.Phase0Config` | One switchboard, all flags OFF by default (`config/ai-player.properties` `phase0.*`) |
| `com.aiplayer.engine.Phase0Integration` | The single runtime seam; lazy-instantiates phase0 modules per subsystem |

| Touched file | Gated hook |
|---|---|
| `CombatAI` | `phase0` built only when enabled; rotation in `shouldUseSkill()`→`useOffensiveSkill()` (via `pendingPhase0Skill`, one rotation consultation per decision), rotation kiting + flee threshold in `manageActiveCombat()`/`shouldHeal()`, shots on cast, `getPhase0Integration()` |
| `examples/Phase0Driver` | Targeting via seam when enabled, death-recovery seam message, inventory advice (1/min), humanized reaction delay on the tick |
| `phase0/combat/SkillDatabase` | **H5 realignment:** skill 3 (Power Strike) mp10/cooldown13000/range40/lvl3, skill 16 (Mortal Blow) mp9/cooldown11000/range40/lvl3 (cross-checked against `ServerBuild/game/data/stats/skills/00000-00099.xml`); removed wrong C4-era ids 36/70 ("Power Shot"/"Iron Punch" are NOT those H5 skills — they are Whirlwind/Drain Health) instead of re-mapping with guessed numbers |

## Status per subsystem (honest)
- **REAL (callers + tests):** Task 1 combat rotation/cooldown/shots, Task 2 targeting/aggro, Task 8 humanized reaction, Task 5 inventory advice (read-only).
- **SEAM (caller exists, explicit SKIP-… string, no fake):** Task 4 death/respawn (respawn opcode not live-proven), Task 6 social/chat (no incoming-chat packet source in PacketLogger; `sendSay()` is a stub), Tasks 9/11 quest/farm (blocked on real `currentXp`/quest parsing).
- Everything ANDs with `phase0.enabled`; each subsystem has its own flag. **Default build = previous behavior byte-for-byte.**

## Tests added (135 → 141)
- `Phase0IntegrationTest` (4): disabled seam is a no-op; enabled Human-Fighter rotation picks Power Strike(3) then honors the 13000ms cooldown; targeting/aggro/humanize work; seams report SKIP strings.
- `CombatAIPhase0Test` (2): no phase0 seam by default; seam present when enabled and `makeDecision()` still works.

## Next (for the Claude/Kimi upgrade prompt)
1. Implement the SEAMs behind their stated blockers: real `sendSay()`/item-use/respawn opcodes (live-prove each like 0x2F), then wire ChatResponder/ConsumableManager/DeathHandler.
2. Real `PacketLogger.getCurrentXp()` parser; then farm/quest scoring.
3. Migrate the ~34 `GameStateMirror` readers → `BotSnapshot`.
4. Full `SkillDatabase` alignment with the H5 datapack/class trees (Gladiator/Warlord).
5. Live run of `Phase0Driver` with the phase0 flags on; histogram target `MAGIC_SKILL_USE(0x48)` > 0 (approach-into-`castRange`-40 first).