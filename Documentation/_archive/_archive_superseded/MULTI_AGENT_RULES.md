# 🤝 Multi-Agent Rules

## Agent Naming Convention

Use distinct names to prevent collisions:
- `Laguna` — Lead agent (default)
- `Alpha` — First concurrent agent
- `Bravo` — Second concurrent agent
- `Charlie`, `Delta`, `Echo` — As needed

Always include your name in:
- TASKS.md `Owner` column
- Git commit author
- RuntimeLog session info

---

## Lock Protocol for TASKS.md

### Claiming a Task
```markdown
| 5 | Write SESSION_PROTOCOL.md | ... | in_progress | Laguna |
```

### Why This Works
- Single source of truth at repo root
- Plain text table — no merge conflicts on different tasks
- Agents can `git pull` before starting to sync

### Collision Prevention
- **Never** edit a task line marked `in_progress` by another agent
- If blocked, mark `blocked` with reason
- For multi-file changes, coordinate via git comments first

---

## Token Budget Awareness

| Task Type | Expected Tokens | Notes |
|-----------|-----------------|-------|
| Docs only | 500-1,500 | AGENT_ONBOARDING, STATUS, STYLEGUIDE |
| Code + docs | 2,000-5,000 | Small feature implementation |
| Full feature | 5,000-10,000 | Complex system, multiple files |
| Audit deep dive | 1,500-3,000 | Reading 5-10 files, writing audit |

Before starting, estimate your task's complexity and ensure token budget.

---

## Merge Conflict Resolution

If two agents edit same file:

1. **First agent pulls**
2. **Second agent adds to merge queue:**
   ```bash
   git add .
   git commit -m "WIP: task in progress for X"
   git push  # Will queue
   ```
3. **First agent pulls queue**
4. **Both re-sync after merge**

---

## Shared RuntimeLog Convention

Each agent creates:
```
Documentation/RuntimeLogs/YYYY-MM-DD-HHMMSS-<agent>-task<N>.md
```

Contains:
- Original prompt
- Objective
- Files modified
- Problems & solutions
- Verification output
- Next steps

---

## Minimum Viable Session

Fresh agent workflow (Task 1-3 validation):
1. Read AGENT_ONBOARDING.md (~400 tokens)
2. Read STATUS.md (~200 tokens)  
3. Read STYLEGUIDE.md (~600 tokens)
4. Complete one real task
5. Update STATUS.md
6. Git commit

**Target: ~1,200 tokens** for bootup + one complete task cycle.