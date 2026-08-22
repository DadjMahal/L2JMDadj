# RuntimeLog — 2026-08-22 F-10 archive probe classes

**Task:** F-10 — decommission/move the probe classes still compiled in `examples/` to attic.

## What changed
1. **Archived 15 probe classes → `AIPlayerEngine/attic/examples/`** (git mv, history kept,
   package line → `// package ...` comment per attic convention): ChatProbe, CombatLoop,
   CombatProbe, EnterWorldProbe, InitDecodeProbe, LoginProbe, MoveProbe,
   NightlyProgressReport, PartyProbe, PvPProbe, QuestFlowLoop, QuestLoop, QuestProbe,
   RawInitProbe, TradeProbe.
2. **Verified liveness first** (`grep -rlnw` across main+test): FleetPlay (fleet launcher,
   live import by behavior/core + scripts), EngineDriver (imported by `core/EngineWiring`),
   MultiPlayerSession (launched by `start_mp.sh`/`watchdog_ai_run.sh`), ExampleAIPlayer
   (documented reference impl) **stayed**.
3. **Dropped dead imports** (probe classes existed in `src/` so the imports compiled; now
   unresolvable): PacketCodec (CombatProbe, EnterWorldProbe, MoveProbe), PacketLogger
   (CombatProbe), GameServerClient (CombatProbe, EnterWorldProbe), MerchantAI (TradeProbe),
   QuestDialogDriver + CoreWiring (QuestFlowLoop); tests: PacketCodecCombatFrameTest +
   PacketCodecUseSkillTest (CombatProbe), QuestDialogTest (QuestFlowLoop).
4. Docs: `AIPlayerEngine/attic/README.md` notes the second batch + cross-ref with
   `scripts/_probes/`; `scripts/_probes/README.md` notes the resurrection step.

## Verification
```bash
scripts/gate.sh   # exit 0 — 415 tests green, style 0 violations, no secrets
git status        # 15 renames + import removals only
```
No `SourceCode/`/`ServerBuild/` touched. Commit: `9c2d11ea`.