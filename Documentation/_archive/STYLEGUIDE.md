# 🧾 Style Guide

## Package & Class Naming

| Type | Convention | Example |
|------|------------|---------|
| Package | lowercase, dot-separated | `com.aiplayer.engine` |
| Class | PascalCase, ends with `AI` for modules | `CombatAI.java`, `AIBrain.java` |
| Interface | PascalCase, ends with `er`/`or` | `ProtocolHandler` |
| Enum | PascalCase | `ActionType`, `Personality` |
| Method | camelCase | `makeDecision()`, `getInventory()` |
| Variable | camelCase | `aiPlayer`, `currentTarget` |
| Constant | UPPER_SNAKE | `MAX_PLAYERS`, `DEFAULT_PORT` |
| File | PascalCase, .java extension | `L2JProtocol.java` |

## Where AI Logic Must NOT Live

❌ **Never put logic in:**
- Packet classes (`ServerPacket.java`, `ClientPacket.java`)
- Network handlers (`GamePacketHandler.java`)
- Configuration classes (except `AIConfiguration`)
- Data model classes (unless pure data)

✅ **AI Logic belongs in:**
- `engine/` - Core decision engines
- `protocol/` - Packet construction/parsing
- `neural/` - Pattern memory, learning
- `advanced/` - Emotional, personality systems

## Logging Format

```
[LEVEL] [Component] Message
```

Examples:
```
[INFO] [CombatAI] Player entering combat state
[WARNING] [MerchantAI] Low adena, emergency sell triggered
[ERROR] [Protocol] Failed to parse packet opcode 0x42
```

Level order: `SEVERE` > `WARNING` > `INFO` > `FINE` > `FINER`

## Required Javadoc (Public API)

```java
/**
 * Main decision making method for AI players.
 * 
 * @return AIDecision with action to execute, or idle if no action needed
 * @throws AIException if decision engine fails
 */
public AIDecision makeDecision() { ... }
```

## Commit Message Format

```
type(scope): brief description

Longer description if needed. Explain what changed and why.
Separate subject from body with blank line.
Use imperative mood ("fix bug" not "fixed bug").

Fixes: #123
Refs: #456
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`

## Definition of Done Checklist

- [ ] Code compiles with `mvn compile`
- [ ] `BUILD SUCCESS` in output
- [ ] At least one real verification command run and output saved
- [ ] No new compiler warnings
- [ ] Javadocs on all public methods added/updated
- [ ] RuntimeLog created documenting the work
- [ ] STATUS.md updated
- [ ] Git commit created with proper message