# 📋 WORKFLOW RULES - The Golden Rules for All Development

## **Session Startup Protocol - READ BEFORE EVERY SESSION**

### 1. Documentation Review (Mandatory)
Before ANY coding:
- Read `/home/volodro/L2JM/REQUIREMENTS.md` - Session goals and protocols
- Read `/home/volodro/L2JM/Documentation/Audit/01-commons.md` - Network/crypto basics  
- Read `/home/volodro/L2JM/Documentation/Audit/04-gameserver-network.md` - Live protocol
- Read current session runtime logs for continuity
- Check `git status` - working tree state

### 2. State Verification
- Validate current build status: `mvn compile`
- Check if servers are running
- Review recent commit history

---

## **THE 4 GOLDEN RULES (Non-Negotiable)**

### **Rule 1: VERIFICATION BEFORE CLAIM**
❌ Never write status reports claiming something works unless you proved it
✅ Must run a command or test that proves functionality
🟡 If unproven, say "not yet tested" or "awaiting verification"

**Examples:**
- Wrong: "CombatAI is integrated and working"
- Right: "CombatAI compiled successfully (verified with mvn compile)"

### **Rule 2: LOG FILES READ-ONLY**
❌ Never write directly to `ServerBuild/**/log/**` files
✅ Log files are written only by the server process itself
✅ Use documentation/logs for analysis, not modification

### **Rule 3: USAGE VALIDATION**
❌ A class is NOT "complete" if nothing calls its public methods
✅ Must grep for usages before marking complete
✅ Either wire it in OR declare "currently unused"

**Verification:**
```bash
grep -r "ClassName" --include="*.java" src/
```

### **Rule 4: AUDIT-FIRST PROTOCOL**
For ANY code talking to L2JMobius (packets, encryption, game state):
1. Read matching file in `Documentation/Audit/` FIRST
2. The doc references OVERRIDE new code if conflict exists
3. Stop and re-read real source file before writing code

---

## **WORKFLOW INTEGRATION MAP**

| Phase | Required Actions | Rules Applied |
|-------|------------------|---------------|
| **Pre-Session** | Read audit docs | Rule #4 |
| **Planning** | Verify state, flag protocol work | All rules |
| **Implementation** | Test after each change | Rule #1 |
| **Protocol Work** | Reference audit docs first | Rule #4 |
| **Documentation** | Update only verified facts | Rule #1 |
| **Git Ops** | Smaller commits, verified | All rules |

---

## **VERIFICATION COMMANDS**

Always run these after changes:
```bash
# Build verification
mvn clean compile

# Usage verification  
grep -r "NewClass" --include="*.java" src/

# Log file check (read-only)
tail -20 /home/volodro/L2JM/ServerBuild/game/log/stdout.log
```

---

## **GIT WORKFLOW**

| Pattern | Example |
|---------|---------|
| **Smaller commits** | Fix: X, Test: Y, Feature: Z |
| **Verified markers** | "BUILD SUCCESS", "TEST PASS" |
| **Branch naming** | `feat/xyz`, `fix/abc`, `test/probe-abc` |

---

## **DOCUMENTATION RULES**

After every task:
- ✅ Keep docs short, accurate, useful
- ✅ Update outdated files immediately  
- ✅ Never delete existing docs
- ✅ Document ONLY information helping future work
- ✅ Create runtime report: `Documentation/RuntimeLogs/<timestamp>-<task>.md`

---

*This document is the master reference. When in doubt, follow these rules first.*
