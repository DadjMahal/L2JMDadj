#!/bin/bash
# Count and track AI Player Progress

LOG_FILE="/home/volodro/L2JM/ServerBuild/game/log/stdout.log"
REPORT_FILE="/home/volodro/AIPlayerEngine/AIStatusLogs/ai_progress_report.txt"

echo "============================================================" > "$REPORT_FILE"
echo "📈 AI PLAYER PROGRESS REPORT" >> "$REPORT_FILE"
echo "============================================================" >> "$REPORT_FILE"
echo "Report Time: $(date)" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

# Get unique AI players
echo "🤖 ACTIVE AI PLAYERS:" >> "$REPORT_FILE"
echo "────────────────────────" >> "$REPORT_FILE"
grep "Character loaded for player:" "$LOG_FILE" 2>/dev/null | \
    cut -d: -f4 | sort | uniq | nl >> "$REPORT_FILE"

echo "" >> "$REPORT_FILE"
echo "🎯 PLAYER PROGRESS:" >> "$REPORT_FILE"
echo "────────────────────────" >> "$REPORT_FILE"

# Count activities per player
grep "LEVEL UP" "$LOG_FILE" 2>/dev/null | \
    awk -F'] ' '{print $2}' | \
    cut -d' ' -f4 | \
    sort | uniq -c | \
    sort -rn | \
    awk '{printf "Level: %s - %s players\n", $2+1, $1}' >> "$REPORT_FILE"

echo "" >> "$REPORT_FILE"
echo "🏆 QUEST COMPLETION:" >> "$REPORT_FILE"
echo "────────────────────────" >> "$REPORT_FILE"
grep "completed quest" "$LOG_FILE" 2>/dev/null | \
    awk -F'] ' '{print $2}' | \
    cut -d' ' -f4 | \
    sort | uniq -c | \
    sort -rn | \
    awk '{printf "Quests: %s - %s completions\n", $3, $1}' >> "$REPORT_FILE

echo "" >> "$REPORT_FILE"
echo "💰 TRADING PERFORMANCE:" >> "$REPORT_FILE"
echo "────────────────────────" >> "$REPORT_FILE"
grep "bought\|sold" "$LOG_FILE" 2>/dev/null | \
    awk -F'] ' '{print $2}' | \
    cut -d' ' -f4 | \
    sort | uniq -c | \
    sort -rn | \
    head -10 | \
    awk '{printf "%s: %s transactions\n", $2, $1}' >> "$REPORT_FILE"

echo "" >> "$REPORT_FILE"
echo "⚔️ COMBAT PERFORMANCE:" >> "$REPORT_FILE"
echo "────────────────────────" >> "$REPORT_FILE"
grep "defeated\|used skill" "$LOG_FILE" 2>/dev/null | \
    awk -F'] ' '{print $2}' | \
    cut -d' ' -f4 | \
    sort | uniq -c | \
    sort -rn | \
    head -10 | \
    awk '{printf "%s: %s actions\n", $2, $1}' >> "$REPORT_FILE"

echo "" >> "$REPORT_FILE"
echo "============================================================" >> "$REPORT_FILE"

cat "$REPORT_FILE"
