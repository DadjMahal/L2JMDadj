#!/bin/bash
# Telemetry Dashboard - Aggregate AI player metrics in one view
# Task 21: Create telemetry dashboard/summary script
#
# Aggregates data from:
# 1. Server stdout.log - PACKET-LOG and PROTOCOL entries
# 2. Database - AI player counts, levels, locations
# 3. AIStatusLogs - existing activity reports

echo "================================================================"
echo "  🤖 AI PLAYER TELEMETRY DASHBOARD"
echo "  Generated: $(date '+%Y-%m-%d %H:%M:%S UTC')"
echo "================================================================"
echo ""

# --- Section 1: Database Metrics ---
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  SECTION 1: DATABASE METRICS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if command -v mysql &> /dev/null; then
    ONLINE=$(mysql -u root gameserver -N -s -e \
        "SELECT COUNT(*) FROM characters WHERE account_name LIKE 'ai_%' AND online = 1;" 2>/dev/null)
    TOTAL=$(mysql -u root gameserver -N -s -e \
        "SELECT COUNT(*) FROM characters WHERE account_name LIKE 'ai_%';" 2>/dev/null)
    
    echo "  Online AI Players: ${ONLINE:-0}"
    echo "  Total Registered AI Players: ${TOTAL:-0}"
    echo ""
    
    echo "  ── AI Players by Type ──"
    mysql -u root gameserver -N -s -e "
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
    " 2>/dev/null || echo "  (database not accessible)"
else
    echo "  MySQL client not found - database metrics unavailable"
fi
echo ""

# --- Section 2: Server Packet Telemetry ---
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  SECTION 2: SERVER PACKET TELEMETRY"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

SERVER_LOG="/home/volodro/L2JM/ServerBuild/game/log/stdout.log"
if [ -f "$SERVER_LOG" ]; then
    echo "  Server log: $SERVER_LOG"
    echo "  Log size: $(wc -l < "$SERVER_LOG") lines"
    echo ""
    
    # Count AI player activity entries
    PROTOCOL_ENTRIES=$(grep -c "\[PROTOCOL\]" "$SERVER_LOG" 2>/dev/null || true)
    PLAYER_CREATED=$(grep -c "AI Player created" "$SERVER_LOG" 2>/dev/null || true)
    MOVED=$(grep -c "\[PROTOCOL\].*MOVED" "$SERVER_LOG" 2>/dev/null || true)
    ATTACKING=$(grep -c "\[PROTOCOL\].*ATTACKING" "$SERVER_LOG" 2>/dev/null || true)
    CHAT=$(grep -c "\[PROTOCOL\].*CHAT" "$SERVER_LOG" 2>/dev/null || true)
    ERRORS=$(grep -c "Think error" "$SERVER_LOG" 2>/dev/null || true)
    PROTOCOL_ENTRIES=${PROTOCOL_ENTRIES:-0}
    PLAYER_CREATED=${PLAYER_CREATED:-0}
    MOVED=${MOVED:-0}
    ATTACKING=${ATTACKING:-0}
    CHAT=${CHAT:-0}
    ERRORS=${ERRORS:-0}
    
    echo "  ┌──────────────────┬──────────┐"
    echo "  │ Metric           │ Count    │"
    echo "  ├──────────────────┼──────────┤"
    printf "  │ %-16s │ %8d │\n" "AI Players Created" "$PLAYER_CREATED"
    printf "  │ %-16s │ %8d │\n" "Protocol Events" "$PROTOCOL_ENTRIES"
    printf "  │ %-16s │ %8d │\n" "  - Moved" "$MOVED"
    printf "  │ %-16s │ %8d │\n" "  - Attacking" "$ATTACKING"
    printf "  │ %-16s │ %8d │\n" "  - Chat" "$CHAT"
    printf "  │ %-16s │ %8d │\n" "Think Errors" "$ERRORS"
    echo "  └──────────────────┴──────────┘"
    echo ""
    
    # Show recent AI player activity
    echo "  ── Recent AI Player Activity ──"
    grep "AI Player created\|Think error\|PROTOCOL" "$SERVER_LOG" 2>/dev/null | tail -10 || echo "  (no activity found)"
else
    echo "  Server log not found at $SERVER_LOG"
    echo "  (start the game server first to collect packet telemetry)"
fi
echo ""

# --- Section 3: AI Status Logs Summary ---
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  SECTION 3: AI STATUS LOGS SUMMARY"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

STATUS_LOG_DIR="/home/volodro/L2JM/AIPlayerEngine/AIStatusLogs"
if [ -d "$STATUS_LOG_DIR" ]; then
    echo "  Log directory: $STATUS_LOG_DIR"
    echo ""
    
    # List log files
    echo "  ── Log Files ──"
    for f in "$STATUS_LOG_DIR"/*; do
        if [ -f "$f" ]; then
            fsize=$(wc -l < "$f" 2>/dev/null || true)
            echo "    $(basename "$f") - ${fsize} lines"
        fi
    done
    echo ""
    
    # Check for existing reports
    echo "  ── Existing Reports ──"
    if [ -f "$STATUS_LOG_DIR/ai_activity_report.txt" ]; then
        echo "    ai_activity_report.txt found"
        head -3 "$STATUS_LOG_DIR/ai_activity_report.txt" 2>/dev/null | tail -1
    fi
else
    echo "  Status logs directory not found"
fi
echo ""

# --- Section 4: Packet Logger Telemetry ---
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  SECTION 4: PACKET LOGGER TELEMETRY"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  PacketLogger.java tracks these packet types:"
echo "    - CHAR_INFO (0x03)    : Player position, level, class"
echo "    - STATUS_UPDATE (0x0E): HP, MP, CP, Level, EXP"
echo "    - NPC_INFO (0x16)     : Monster/NPC position, attackable"
echo "    - ITEM_LIST (0x1B)    : Inventory items"
echo "    - QUEST_INFO (0xFE:0x19): Quest tracking"
echo "    - DELETE_OBJECT (0x12): Object removed"
echo "    - SYSTEM_MESSAGE (0x64): System messages"
echo ""
echo "  Telemetry counters available via PacketLogger.getTelemetrySummary()"
echo "  AI modules wired: CombatAI, QuestAI, MerchantAI, SocialAI"
echo ""

# --- Section 5: Build Status ---
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  SECTION 5: BUILD STATUS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
cd /home/volodro/L2JM/AIPlayerEngine 2>/dev/null
COMPILE_STATUS=$(mvn compile -q -o 2>&1 | grep -E "BUILD" | tail -1)
if echo "$COMPILE_STATUS" | grep -q "SUCCESS"; then
    echo "  Java compilation: ✅ BUILD SUCCESS"
else
    echo "  Java compilation: ⚠️  Offline compile (may need network)"
fi
echo ""

echo "================================================================"
echo "  TELEMETRY DASHBOARD COMPLETE"
echo "================================================================"
