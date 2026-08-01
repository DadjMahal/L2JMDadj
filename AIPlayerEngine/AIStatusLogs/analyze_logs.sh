#!/bin/bash
# AI Player Log Analyzer
# Analyzes server stdout.log for AI player activity

LOG_FILE="/home/volodro/L2JM/ServerBuild/game/log/stdout.log"
OUTPUT_FILE="/home/volodro/AIPlayerEngine/AIStatusLogs/ai_activity_report.txt"

echo "============================================================" > "$OUTPUT_FILE"
echo "🤖 AI PLAYER ACTIVITY REPORT - $(date)" >> "$OUTPUT_FILE"
echo "============================================================" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Count AI players
AI_PLAYERS=$(grep -c "Character loaded for player:" "$LOG_FILE" 2>/dev/null || echo "0")
echo "Total AI Players Active: $AI_PLAYERS" >> "$OUTPUT_FILE"

# Count quest completions
QUESTS=$(grep -c "completed quest" "$LOG_FILE" 2>/dev/null || echo "0")
echo "Total Quests Completed: $QUESTS" >> "$OUTPUT_FILE"

# Count level ups
LEVELS=$(grep -c "LEVEL UP" "$LOG_FILE" 2>/dev/null || echo "0")
echo "Total Level Ups: $LEVELS" >> "$OUTPUT_FILE"

# Count trades
TRADES=$(grep -c "bought\|sold" "$LOG_FILE" 2>/dev/null || echo "0")
echo "Total Trade Actions: $TRADES" >> "$OUTPUT_FILE"

# Count combats
COMBATS=$(grep -c "targeting monster\|used skill\|defeated" "$LOG_FILE" 2>/dev/null || echo "0")
echo "Total Combat Actions: $COMBATS" >> "$OUTPUT_FILE"

# Count party/social actions
SOCIAL=$(grep -c "joined party\|says:\|chat" "$LOG_FILE" 2>/dev/null || echo "0")
echo "Total Social Actions: $SOCIAL" >> "$OUTPUT_FILE"

# Calculate estimated ADENA
ADENA=$(grep "gained [0-9]* ADENA" "$LOG_FILE" 2>/dev/null | grep -o "[0-9]* ADENA" | sed 's/ADENA/g' | awk '{sum+=$1} END {print sum}' || echo "0")
echo "Estimated Total ADENA Gained: ${ADENA:-0}ADENA" >> "$OUTPUT_FILE"

echo "" >> "$OUTPUT_FILE"
echo "============================================================" >> "$OUTPUT_FILE"
echo "Top Active AI Players:" >> "$OUTPUT_FILE"
echo "============================================================" >> "$OUTPUT_FILE"

# List top 5 players by activity
grep "Player connected" "$LOG_FILE" | cut -d: -f3- | cut -d' ' -f4 | sort | uniq -c | sort -rn | head -5 >> "$OUTPUT_FILE"

echo "" >> "$OUTPUT_FILE"
echo "Report saved to: $OUTPUT_FILE"
