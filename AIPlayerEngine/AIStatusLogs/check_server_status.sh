#!/bin/bash
# Check L2JMobius Server Status

echo "============================================================"
echo "🖥️  L2JMOBIUS SERVER STATUS CHECK"
echo "============================================================"
echo ""

# Check LoginServer
if pgrep -f "LoginServer.jar" > /dev/null; then
    echo "✅ LoginServer: RUNNING"
else
    echo "❌ LoginServer: NOT RUNNING"
fi

# Check GameServer
if pgrep -f "GameServer.jar" > /dev/null; then
    echo "✅ GameServer: RUNNING"
else
    echo "❌ GameServer: NOT RUNNING"
fi

# Check MariaDB
if systemctl is-active --quiet mariadb 2>/dev/null || systemctl is-active --quiet mysql 2>/dev/null; then
    echo "✅ Database: RUNNING"
else
    echo "❌ Database: NOT RUNNING"
fi

echo ""
echo "============================================================"
echo "📡 PORT STATUS"
echo "============================================================"

# Check ports
for port in 2106 9014 7777; do
    if ss -tlnp 2>/dev/null | grep -q ":$port "; then
        echo "✅ Port $port: LISTENING"
    else
        echo "❌ Port $port: NOT LISTENING"
    fi
done

echo ""
echo "============================================================"
echo "================================================"
echo ""

# Count active player connections
CONNECTIONS=$(netstat -an 2>/dev/null | grep ESTABLISHED | grep -E "2106|7777" | wc -l)
echo "📊 Active Player Connections: $CONNECTIONS"
echo ""
