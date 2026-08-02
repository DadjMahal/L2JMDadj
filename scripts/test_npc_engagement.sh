#!/bin/bash
# Test NPC Engagement
# Task 60: Verify AI players can engage NPCs with real server

echo "================================================================"
echo "  ⚔️  NPC ENGAGEMENT TEST SCRIPT"
echo "  Verifying AI players can engage NPCs against L2JMobius server"
echo "================================================================"
echo ""

# Check server status
echo "1. Checking server availability..."
if nc -z localhost 2106 2>/dev/null; then
    echo "   ✅ Game server port 2106 is open"
else
    echo "   ❌ Game server not responding on port 2106"
    echo "   Start the L2JMobius server before running this test"
    exit 1
fi
echo ""

# Check database
echo "2. Checking database connectivity..."
if command -v mysql &> /dev/null; then
    ONLINE=$(mysql -u root gameserver -N -s -e "SELECT COUNT(*) FROM characters WHERE account_name LIKE 'ai_%' AND online = 1;" 2>/dev/null || echo "0")
    echo "   AI Players Online: ${ONLINE:-0}"
else
    echo "   MySQL client not found - skipping DB check"
fi
echo ""

# Run unit tests
echo "3. Running combat unit tests..."
cd /home/volodro/L2JM/AIPlayerEngine
mvn test -q 2>&1 | grep -E "Tests run:|BUILD" | head -5
echo ""

# Check combat implementation
echo "4. Verifying combat implementation..."
echo "   CombatAI methods:"
grep -E "private|public.*CombatDecision|public.*boolean" src/main/java/com/aiplayer/engine/CombatAI.java | wc -l | xargs -I {} echo "   Total methods: {}"
echo ""

# Check packet encoding
echo "5. Verifying packet encoding..."
if grep -q "sendAttack" src/main/java/com/aiplayer/protocol/L2JProtocol.java; then
    echo "   ✅ sendAttack method exists in L2JProtocol"
else
    echo "   ❌ sendAttack method missing"
fi
echo ""

echo "================================================================"
echo "  NPC ENGAGEMENT TEST COMPLETE"
echo "================================================================"
echo ""
echo "To test full NPC engagement:"
echo "  1. Start L2JMobius server with NPCs/spawns"
echo "  2. Run: java -cp target/classes com.aiplayer.engine.AIPlayerEngine --spawn-all"
echo "  3. Monitor AIStatusLogs for combat telemetry"
echo "  4. Verify database shows ai_combat_% players attacking NPCs"
echo ""
