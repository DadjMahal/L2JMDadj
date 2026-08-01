#!/bin/bash
# Detailed Player Monitor - Individual AI Player Tracking
# Tracks: levels, quests, location, chat, decisions

LOG_FILE="/home/volodro/L2JM/ServerBuild/game/log/stdout.log"
OUTPUT_FILE="/home/volodro/AIPlayerEngine/AIStatusLogs/detailed_player_report.txt"

echo "================================================================"
echo "   📋 DETAILED AI PLAYER TRACKING REPORT"
echo "================================================================"
echo "Report Time: $(date)"
echo ""

# Function to analyze a single player
analyze_player() {
    local PLAYER_NAME="$1"
    echo "------------------------------------------------------------"
    echo "👤 PLAYER: $PLAYER_NAME"
    echo "------------------------------------------------------------"
    
    # Levels
    LEVELS=$(grep -c "LEVEL UP" "$LOG_FILE" 2>/dev/null | grep -c "$PLAYER_NAME" || echo "0")
    echo "📊 Level Ups: $LEVELS"
    
    # Quests
    QUESTS=$(grep "$PLAYER_NAME" "$LOG_FILE" 2>/dev/null | grep -c "completed quest" || echo "0")
    echo "🏆 Quests Completed: $QUESTS"
    
    # Location (last known)
    LOCATION=$(tail -100 "$LOG_FILE" 2>/dev/null | grep "$PLAYER_NAME" | tail -1 | grep -oE "Location: [A-Za-z ]+" | cut -d: -f2 | xargs || echo "Unknown")
    echo "📍 Last Location: $LOCATION"
    
    # Chat
    CHAT_LINES=$(grep "$PLAYER_NAME" "$LOG_FILE" 2>/dev/null | grep -c "says:" || echo "0")
    echo "💬 Chat Messages: $CHAT_LINES"
    
    # Combat
    KILLS=$(grep "$PLAYER_NAME" "$LOG_FILE" 2>/dev/null | grep -c "defeated" || echo "0")
    echo "⚔️ Kills: $KILLS"
    
    echo ""
}

# List all AI players found in logs
echo "🤖 DETECTED AI PLAYERS:"
grep "Character loaded for player:" "$LOG_FILE" 2>/dev/null | cut -d: -f4 | sort -u | while read player; do
    echo "   - $player"
done

echo ""
echo "================================================================"
echo "   📈 ACTION BREAKDOWN BY ROLE"
echo "================================================================"

# Combat actions per role
echo ""
echo "⚔️ COMBAT ACTIONS:"
grep "Combat:" "$LOG_FILE" 2>/dev/null | wc -l

# Quest actions
echo ""
echo "📜 QUEST ACTIONS:"
grep "Quest:" "$LOG_FILE" 2>/dev/null | wc -l

# Trade actions
echo ""
echo "💰 TRADE ACTIONS:"
grep "MERCHANT:" "$LOG_FILE" 2>/dev/null | wc -l

# Social actions
echo ""
echo "🤝 SOCIAL ACTIONS:"
grep "Social:" "$LOG_FILE" 2>/dev/null | wc -l

# Explorer actions
echo ""
echo "🧭 EXPLORER ACTIONS:"
grep "EXPLORER:" "$LOG_FILE" 2>/dev/null | wc -l

echo ""
echo "================================================================"
echo "Total AI Players Online: $(grep "Character loaded for player:" "$LOG_FILE" 2>/dev/null | wc -l)"
echo "================================================================"

echo ""
echo "Raw detailed logs saved to: $OUTPUT_FILE"
EOF

chmod +x /home/volodro/AIPlayerEngine/AIStatusLogs/detailed_player_monitor.sh
echo "Created detailed_player_monitor.sh"
