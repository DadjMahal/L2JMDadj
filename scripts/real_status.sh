#!/bin/bash
# Real AI Player Status - queries actual server state
# No fabricated data - only real database and log queries

echo "=========================================="
echo "  L2JM AI PLAYER REAL STATUS"
echo "=========================================="
echo ""

# Database query for online AI players
echo "=== ONLINE AI PLAYERS (from database) ==="
ONLINE_COUNT=$(sudo mysql -u root gameserver -e "SELECT COUNT(*) FROM characters WHERE account_name LIKE 'ai_%' AND online = 1;" 2>/dev/null | tail -1 | tr -d ' \t')
if [ -n "$ONLINE_COUNT" ] && [ "$ONLINE_COUNT" -gt 0 ] 2>/dev/null; then
    echo "AI players currently online: $ONLINE_COUNT"
else
    echo "AI players currently online: 0"
fi
echo ""

# Actual server log grep counts
echo "=== ACTIVITY FROM SERVER LOGS ==="
LOG_FILE="/home/volodro/L2JM/ServerBuild/game/log/stdout.log"

if [ -f "$LOG_FILE" ]; then
    echo "Log file: $LOG_FILE"
    echo "  Combat actions: $(grep -c '\[COMBAT\]' "$LOG_FILE" 2>/dev/null || echo 0)"
    echo "  Quest actions: $(grep -c '\[QUEST\]' "$LOG_FILE" 2>/dev/null || echo 0)"
    echo "  Trade actions: $(grep -c '\[TRADE\]' "$LOG_FILE" 2>/dev/null || echo 0)"
    echo "  Level ups: $(grep -c 'LEVEL UP' "$LOG_FILE" 2>/dev/null || echo 0)"
    echo "  Chat messages: $(grep -c '\[CHAT\]' "$LOG_FILE" 2>/dev/null || echo 0)"
else
    echo "Server log file not found: $LOG_FILE"
fi
echo ""

# Port status
echo "=== SERVER PORT STATUS ==="
echo -n "LoginServer (2106): "
if nc -z localhost 2106 2>/dev/null; then
    echo "LISTENING YES"
else
    echo "LOGIN SERVER NOT LISTENING NO"
fi

echo -n "GameServer (7777): "
if nc -z localhost 7777 2>/dev/null; then
    echo "LISTENING YES"
else
    echo "GAMESERVER NOT LISTENING NO"
fi
echo ""

echo "=========================================="
echo "  END OF STATUS REPORT"
echo "=========================================="
