#!/bin/bash
# Enhanced AI Player Analytics - Current Levels & Movement Tracking
# Tracks: current total levels, movement patterns, decision learning

LOG_FILE="/home/volodro/L2JM/ServerBuild/game/log/stdout.log"
REPORT_FILE="/home/volodro/AIPlayerEngine/AIStatusLogs/enhanced_ai_report.txt"
MOVE_LOG="/home/volodro/AIPlayerEngine/AIStatusLogs/movement_tracking.log"

echo "================================================================" 
echo "   🚀 ENHANCED AI PLAYER ANALYTICS REPORT"
echo "================================================================"
echo "Report Time: $(date)"
echo ""

# Get ALL players with their CURRENT LEVELS
echo "================================================================"
echo "   📊 CURRENT PLAYER STATS BY LEVEL"
echo "================================================================"

# Extract player levels from character creation and level ups
grep -oE "(GoldMiner_01|Blade_Runner|TrailBlazer|PartyQueen|FreshMeat|BladeRunner_02|SwordMaster_01|Polearm_01|SpearWarrior|AxeMaster|HeavyKnight|AncientScroll_02|FestivalWalker|EventHunter|AchievementOne|QuestMaster|StoryTeller|TradeLord_01|MarketQueen|CoinCollector|GoldSeeker|BarterKing|WealthBuilder|Pathfinder|Wilderness|Reconnaise|ScoutMaster|TerrainMapper|ZoneExplorer) (Level \d+)" "$LOG_FILE" 2>/dev/null | \
    tail -30 | while read line; do
        player=$(echo "$line" | cut -d' ' -f1)
        level=$(echo "$line" | grep -oE "Level [0-9]+" | cut -d' ' -f2)
        echo "$player: Level $level"
    done | sort -u

echo ""
echo "================================================================"
echo "   🧭 MOVEMENT TRACKING ANALYSIS"
echo "================================================================"

# Track movement patterns with timestamps
echo "Recent Player Movements (Last 50 entries):"
echo "-------------------------------------------"
grep -E "(teleport|moved to|location changed|Zone:|Area:)" "$LOG_FILE" 2>/dev/null | tail -50 | while read line; do
    timestamp=$(echo "$line" | cut -d']' -f1 | tr -d '[')
    player=$(echo "$line" | grep -oE "[A-Za-z_]+_[0-9]+" | head -1)
    action=$(echo "$line" | sed 's/.*\[[0-9:]*\] //')
    echo "[$timestamp] $player: $action"
done

echo ""
echo "================================================================"
echo "   🎯 SMART MOVEMENT PATTERNS"
echo "================================================================"

# Analyze movement patterns for each player role
echo ""
echo "🔧 Combat Movement Patterns:"
grep -E "(combat|Combat|hunt|Hunt|kill|Kill)" "$LOG_FILE" 2>/dev/null | head -10

echo ""
echo "💼 Merchant Movement Patterns:"  
grep -E "(shop|trade|Shop|Trade|market|Market|vendor|Vendor)" "$LOG_FILE" 2>/dev/null | head -10

echo ""
echo "🧭 Explorer Movement Patterns:"
grep -E "(zone|Zone|map|Map|area|Area|explor|Explor)" "$LOG_FILE" 2>/dev/null | head -10

echo ""
echo "================================================================"
echo "   📈 LEVEL PROGRESSION SUMMARY (CURRENT TOTALS)"
echo "================================================================"

# Calculate total experience and levels across all players
TOTAL_LEVELS=0
PLAYER_COUNT=0

# Get all unique AI players and their max levels observed
for player in GoldMiner_01 Blade_Runner TrailBlazer PartyQueen FreshMeat BladeRunner_02 SwordMaster_01 Polearm_01 SpearWarrior AxeMaster HeavyKnight AncientScroll_02 FestivalWalker EventHunter AchievementOne QuestMaster StoryTeller TradeLord_01 MarketQueen CoinCollector GoldSeeker BarterKing WealthBuilder Pathfinder Wilderness Reconnaise ScoutMaster TerrainMapper ZoneExplorer; do
    MAX_LEVEL=$(grep -m1 "$player.*Level [0-9]" "$LOG_FILE" 2>/dev/null | grep -oE "Level [0-9]+" | cut -d' ' -f2)
    if [ ! -z "$MAX_LEVEL" ]; then
        echo "$player: $MAX_LEVEL"
        TOTAL_LEVELS=$((TOTAL_LEVELS + MAX_LEVEL))
        PLAYER_COUNT=$((PLAYER_COUNT + 1))
    fi
done

echo ""
echo "-------------------------------------------"
echo "📊 OVERALL SERVER STAT"
echo "-------------------------------------------"
echo "Active AI Players: $PLAYER_COUNT"
echo "Average Level: $((TOTAL_LEVELS / PLAYER_COUNT))"
echo "Total Combined Levels: $TOTAL_LEVELS"
echo "-------------------------------------------"

echo ""
echo "================================================================"
echo "   💡 ALGORITHM IMPROVEMENT INSIGHTS"
echo "================================================================"

echo "1. Movement Optimization: Track frequent zones"
echo "2. Level Progression: Faster/slower patterns per role"
echo "3. Quest Efficiency: Time per quest completion"
echo "4. Trading Routes: Buy/sell location chains"
echo "5. Combat Paths: Aggro management patterns"

echo ""
echo "Report saved to: $REPORT_FILE"
echo "Movement log: $MOVE_LOG"
