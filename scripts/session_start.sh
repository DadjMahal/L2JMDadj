#!/bin/bash
# Session Start Script
# Pre-session setup for agent work

echo "=========================================="
echo "  SESSION START"
echo "=========================================="
echo ""

# Step 1: Sync with repo
echo "[1] Syncing with repository..."
git -C /home/volodro/L2JM pull 2>/dev/null || echo "  (no remote or not a git repo)"

# Step 2: Read onboarding
echo ""
echo "[2] Reading onboarding documents..."
echo "  - AGENT_ONBOARDING.md"
echo "  - STATUS.md"
echo "  - STYLEGUIDE.md"
echo "  - TASKS.md"

# Step 3: Check build status
echo ""
echo "[3] Checking build status..."
cd /home/volodro/L2JM/AIPlayerEngine
if mvn compile -q 2>/dev/null; then
    echo "  BUILD SUCCESS"
else
    echo "  BUILD FAILED - check compilation errors"
fi

# Step 4: Display next task
echo ""
echo "[4] Next pending task:"
grep -E '\|\s+[0-9]+\s+\|' /home/volodro/L2JM/TASKS.md | grep "pending" | head -1

echo ""
echo "=========================================="
echo "  READY TO START WORK"
echo "=========================================="
echo ""

# Output token estimate
echo "Token budget reminder:"
echo "  Docs only: 500-1500 tokens"
echo "  Code + docs: 2000-5000 tokens"
echo "  Full feature: 5000-10000 tokens"
echo ""
echo "Read the task carefully before starting."