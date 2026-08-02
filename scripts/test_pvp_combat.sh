#!/bin/bash
# Test PvP Combat
# Task 61: Verify PvP combat logic

echo "================================================================"
echo "  ⚔️  PvP COMBAT TEST SCRIPT"
echo "  Testing AI Player vs AI Player Combat"
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

# Check configuration
echo "2. Checking PvP configuration..."
cd /home/volodro/L2JM/AIPlayerEngine
if grep -q "combat.pvp_enabled" src/main/resources/config/ai-player.properties 2>/dev/null; then
    PvP_STATUS=$(grep "combat.pvp_enabled" src/main/resources/config/ai-player.properties)
    echo "   PvP config: $PvP_STATUS"
else
    echo "   Using default: combat.pvp_enabled=false"
fi
echo ""

# Run unit tests
echo "3. Running combat unit tests..."
mvn test -q 2>&1 | grep -E "Tests run:|BUILD" | head -5
echo ""

# Check PvP logic implementation
echo "4. Verifying PvP logic in CombatAI..."
if grep -q "isPvPenabled\|isHostilePlayer\|isPlayerHostile" src/main/java/com/aiplayer/engine/CombatAI.java 2>/dev/null; then
    echo "   ✅ PvP-specific logic found in CombatAI"
else
    echo "   ⚠️  PvP-specific logic may need enhancement"
fi
echo ""

# Verify packet handling
echo "5. Verifying PvP packet handling..."
if grep -q "ATTACK_REQUEST" src/main/java/com/aiplayer/protocol/PacketCodec.java 2>/dev/null; then
    echo "   ✅ Attack packet encoding implemented"
else
    echo "   ❌ Attack packet encoding missing"
fi
echo ""

echo "================================================================"
echo "  PvP COMBAT TEST COMPLETE"
echo "================================================================"
echo ""
echo "To test full PvP combat:"
echo "  1. Start L2JMobius server with multiple player zones"
echo "  2. Set combat.pvp_enabled=true in config"
echo "  3. Spawn AI combat players in same area"
echo "  4. Monitor AIStatusLogs for PvP combat telemetry"
echo ""
