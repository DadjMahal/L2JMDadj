#!/bin/bash
# Count AI Players - Query database for ai_% online players
# Task 17: Write scripts/count_ai_players.sh

echo "============================================================"
echo "  AI PLAYER COUNT - Database Query"
echo "============================================================"
echo ""

# Check if MySQL is available
if command -v mysql &> /dev/null; then
    echo "=== ONLINE AI PLAYERS ==="
    ONLINE=$(sudo mysql -u root gameserver -N -s -e "SELECT COUNT(*) FROM characters WHERE account_name LIKE 'ai_%' AND online = 1;" 2>/dev/null)
    if [ $? -eq 0 ] && [ -n "$ONLINE" ]; then
        echo "AI players currently online: $ONLINE"
    else
        echo "AI players currently online: 0 (or database not accessible)"
    fi
    echo ""

    echo "=== REGISTERED AI PLAYERS ==="
    TOTAL=$(sudo mysql -u root gameserver -N -s -e "SELECT COUNT(*) FROM characters WHERE account_name LIKE 'ai_%';" 2>/dev/null)
    if [ $? -eq 0 ] && [ -n "$TOTAL" ]; then
        echo "Total registered AI players: $TOTAL"
    else
        echo "Total registered AI players: 0 (or database not accessible)"
    fi
    echo ""

    echo "=== AI PLAYERS BY TYPE ==="
    sudo mysql -u root gameserver -N -s -e "
    SELECT 
        CASE 
            WHEN account_name LIKE 'ai_combat_%' THEN 'Combat'
            WHEN account_name LIKE 'ai_quest_%' THEN 'Quest'  
            WHEN account_name LIKE 'ai_merchant_%' THEN 'Merchant'
            WHEN account_name LIKE 'ai_social_%' THEN 'Social'
            ELSE 'Other'
        END as type,
        COUNT(*) as count
    FROM characters 
    WHERE account_name LIKE 'ai_%' 
    GROUP BY 1
    ORDER BY 2 DESC;
    " 2>/dev/null || echo "Database not accessible"
else
    echo "MySQL client not found in PATH"
fi
echo ""

# Log-based fallback
echo "=== RECENT AI PLAYER ACTIVITY (from logs) ==="
LOG_FILE="/home/volodro/L2JM/ServerBuild/game/log/stdout.log"
if [ -f "$LOG_FILE" ]; then
    echo "Characters loaded today:"
    grep "Character loaded for player:" "$LOG_FILE" 2>/dev/null | \
        tail -10 | \
        sed 's/.*player: //' | \
        sort | uniq -c | sort -rn
else
    echo "Server log not found at $LOG_FILE"
fi
echo ""

echo "============================================================"
echo "  END OF AI PLAYER COUNT REPORT"
echo "============================================================"
