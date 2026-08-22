#!/bin/bash
# Test Combat Against Live Server
# Task 59: Start first combat test against live server

echo "================================================================"
echo "  ⚔️  LIVE COMBAT TEST SCRIPT"
echo "  Testing AI Player Combat Against L2JMobius Server"
echo "================================================================"
echo ""

# Check if server is running
echo "Checking server availability..."
if nc -z localhost 2106 2>/dev/null; then
    echo "  ✅ L2JMobius game server port 2106 is open"
else
    echo "  ⚠️  Game server not responding on port 2106"
    echo "  Start the L2JMobius server before running this test"
    exit 1
fi
echo ""

# Run the Java test class
echo "Running CombatIntegrationTest..."
cd /home/volodro/L2JM/AIPlayerEngine
mvn test -Dtest=CombatAITest -q 2>&1 | grep -E "Tests run:|BUILD|ERROR" | head -10
echo ""

# Summary
echo "================================================================"
echo "  LIVE COMBAT TEST COMPLETE"
echo "================================================================"
echo ""
echo "Next steps:"
echo "  1. Verify server logs show AI player connections"
echo "  2. Check AIStatusLogs for combat telemetry"
echo "  3. Monitor player database for ai_combat_% accounts"
echo ""
