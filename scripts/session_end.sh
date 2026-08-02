#!/bin/bash
# Session End Script
# Post-session cleanup and commit

echo "=========================================="
echo "  SESSION END"
echo "=========================================="
echo ""

# Step 1: Verify changes
echo "[1] Staging changes..."
git -C /home/volodro/L2JM add -A

# Step 2: Check if there are changes to commit
if git -C /home/volodro/L2JM diff --cached --quiet 2>/dev/null; then
    echo "  No changes to commit"
else
    # Step 3: Commit with task number
    echo ""
    echo "[2] Committing changes..."
    read -p "  Enter commit message: " -r MESSAGE
    if [ -z "$MESSAGE" ]; then
        MESSAGE="feat: completed task"
    fi
    git -C /home/volodro/L2JM commit -m "$MESSAGE"
    git -C /home/volodro/L2JM push 2>/dev/null || echo "  (push skipped - no remote)"
fi

# Step 4: Update STATUS.md
echo ""
echo "[3] SESSION COMPLETE"
echo "  Remember to update TASKS.md status after commit"
echo "  Create RuntimeLog for this session"

echo ""
echo "=========================================="
echo "  NEXT SESSION"
echo "=========================================="
echo "  1. Start fresh with ./scripts/session_start.sh"
echo "  2. Read Onboarding + Status"
echo "  3. Claim next task in TASKS.md"
