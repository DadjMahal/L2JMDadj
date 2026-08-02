# 🚀 Session Protocol

## Agent Session Workflow — Follow This Exactly

### Step 1: Read Onboarding & Status
```bash
cat /home/volodro/L2JM/AGENT_ONBOARDING.md
cat /home/volodro/L2JM/STATUS.md
cat /home/volodro/L2JM/STYLEGUIDE.md
```
**Total: ~1,800 tokens** — this is the ONLY way to get orientued fast.

### Step 2: Claim One Task
1. Open `TASKS.md`
2. Find the first `pending` task (or one assigned to you)
3. Change status to `in_progress` and add your agent name:
   ```
   | 5 | Write `Documentation/SESSION_PROTOCOL.md` | ... | in_progress | YourAgent |
   ```

### Step 3: Do The Work
- Work inside the current project only
- Modify source code only where intended
- Verify with real command output — no claim without a pasted result

### Step 4: Verify Completion
```bash
# Run verification command
<your verification command here>

# Copy AND PASTE actual output to this session
# Example:
# $ mvn compile
# [INFO] BUILD SUCCESS
```

### Step 5: Update Status
1. Return to `TASKS.md`
2. Change task status from `in_progress` to `done`
3. Add one-line Result in the right column
4. Update `STATUS.md` with next task info

### Step 6: Git Commit
```bash
git add .
git commit -m "feat: <task number> - <brief description>"
```

---

## Critical Rules

- **Never** start without reading Onboarding + Status
- **Never** claim a task marked `in_progress` by another agent
- **Never** say done without real verification output
- **Always** update STATUS.md before committing

---

## Emergency Recovery

If your session is interrupted:
1. Re-read STATUS.md to find next pending task
2. Check if your in-progress task needs completion
3. Resume from where STATUS.md says to resume