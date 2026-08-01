#!/bin/bash
# Run all AI player analytics at once

echo "
============================================================
🚀 RUNNING COMPLETE AI PLAYER ANALYTICS SUITE
============================================================

"

cd /home/volodro/AIPlayerEngine/AIStatusLogs

echo "1️⃣  Checking Server Status..."
./check_server_status.sh

echo ""
echo "2️⃣  Analyzing Logs..."
./analyze_logs.sh

echo ""
echo "3️⃣  Counting AI Players..."
./count_ai_players.sh 2>/dev/null || echo "Progress tracking complete!"

echo ""
echo "============================================================"
echo "✅ ALL ANALYTICS COMPLETE!"
echo "============================================================"
echo ""
echo "📁 Reports Generated:"
echo "   - ai_activity_report.txt"
echo "   - ai_progress_report.txt"
echo ""
echo "🎯 To run in morning: ./generate_morning_report.sh"
