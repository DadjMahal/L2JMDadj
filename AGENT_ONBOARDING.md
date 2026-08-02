# 🤖 AI Player Agent Onboarding

## Project Summary
L2JMobius Interlude server + AI Player Engine. Build autonomous AI players that connect externally to the game server via standard protocols (no server code modifications). AI players perform combat, questing, trading, and social interactions.

## The 6 Hard Rules
1. **Verify before claim** - Never say "working" without real command output proof
2. **Zero fake logs** - All status reports from real database queries + log greps
3. **Usage validation** - Verify code is actually called by testing
4. **Audit-first** - Read Documentation/Audit/*.md before modifying protocol/network code
5. **Document before code** - Write the doc, then implement the code
6. **Leave cleaner** - Remove dead code, update docs, make repo better than found

## Routing Table: What to Read When

| Task Touches | Read These First |
|--------------|------------------|
| Combat AI | `CombatAI.java`, `CombatDecision.java`, `04-gameserver-network.md` |
| Network/Protocol | `01-commons.md`, `04-gameserver-network.md`, `L2JProtocol.java` |
| Player Model | `05-model-actor-core.md`, `07-model-actor-stat.md` |
| Quest System | `30-quest-progression.md`, `QuestAI.java` |
| Docs Only | `STYLEGUIDE.md` + `SESSION_PROTOCOL.md` |

## Quick Start Commands
```bash
cd /home/volodro/L2JM
git status                    # Check repo state
mvn compile -f AIPlayerEngine # Build AI Engine
./StartServer.sh              # Start L2JM server
```