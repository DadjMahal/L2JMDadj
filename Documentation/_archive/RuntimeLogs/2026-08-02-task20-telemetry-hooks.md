# 2026-08-02-task20-telemetry-hooks.md

**Agent:** System  
**Task:** 20 - Add telemetry hooks to all 4 AI modules

---

## Objective

Integrate the PacketLogger (from Task 19) into all 4 AI modules, giving each one
a per-player PacketLogger instance to track server packets for decision making.

---

## Files Modified

| File | Action | Change |
|------|--------|--------|
| `CombatAI.java` | MODIFIED | Added PacketLogger field + constructor init |
| `QuestAI.java` | MODIFIED | Added PacketLogger field + constructor init |
| `MerchantAI.java` | MODIFIED | Added PacketLogger field + constructor init |
| `SocialAI.java` | MODIFIED | Added PacketLogger field + constructor init |

Each module now has:
- `import com.aiplayer.protocol.PacketLogger;`
- `private final PacketLogger packetLogger;`
- `this.packetLogger = new PacketLogger(aiPlayer.getName());`

---

## Verification

```bash
$ mvn compile
[INFO] BUILD SUCCESS

$ grep -rn "packetLogger" CombatAI.java QuestAI.java MerchantAI.java SocialAI.java
4 files, 8 matches (field + constructor in each)
```

---

## Next Step

Task 21: Add telemetry counter increments in AI decision methods