#!/bin/bash
# Smart Movement Tracker - Analyzes Player Navigation Patterns
# Helps improve AI pathfinding and decision making

LOG_FILE="/home/volodro/L2JM/ServerBuild/game/log/stdout.log"
MOVE_DATA="/tmp/ai_movement_data.csv"

echo "================================================================"
echo "   🧭 SMART MOVEMENT TRACKER"
echo "================================================================"
echo "Timestamp: $(date)"
echo ""

# Create CSV header for movement analysis
echo "timestamp,player,action,location,x,y,z,reason" > "$MOVE_DATA"

# Parse movement events from logs
grep -E "(teleport|move|travel|route|path|walk|run)" "$LOG_FILE" 2>/dev/null | while read line; do
    timestamp=$(echo "$line" | cut -d']' -f1 | tr -d '[')
    # Extract player name (pattern: Name_###)
    player=$(echo "$line" | grep -oE "[A-Za-z]+_[0-9]+" | head -1)
    action=$(echo "$line" | grep -oE "(teleport|move|travel)" | head -1)
    location=$(echo "$line" | sed -E 's/.*[A-Za-z]+_[0-9]+ (?:teleported to|moved to|traveling|at) //' | cut -c1-30)
    
    if [ ! -z "$player" ]; then
        echo "$timestamp,$player,$action,$location,0,0,0,smart_route" >> "$MOVE_DATA"
    fi
done

echo "Movement patterns captured in: $MOVE_DATA"
echo ""

# Analyze top movement routes
echo "================================================================"
echo "   📍 TOP MOVEMENT ROUTES"
echo "================================================================"

cut -d',' -f2,4 "$MOVE_DATA" | sort | uniq -c | sort -rn | head -15

echo ""
echo "================================================================"
echo "   🔄 ROUTE LOOP DETECTION"
echo "================================================================"

# Detect loops and repeated patterns
for player in GoldMiner_01 Blade_Runner TrailBlazer PartyQueen; do
    ROUTES=$(grep ",$player," "$MOVE_DATA" 2>/dev/null | cut -d',' -f4 | sort | uniq -c | sort -rn | head -3)
    echo "$player top routes:"
    echo "$ROUTES"
    echo "---"
done

echo ""
echo "================================================================"
echo "   🎯 RECOMMENDATIONS FOR IMPROVING AI MOVEMENT"
echo "================================================================"

echo "1. Optimize Teleport Routes: Use gatekeepers for faster travel"
echo "2. Dynamic Zone Rotation: Avoid over-farming same spots"
echo "3. Weather/Time-Aware: Choose activities by conditions"
echo "4. Group Movement: Sync parties for efficiency"
echo "5. Avoid Congestion: Route around busy towns"

echo ""
echo "Analysis complete! Movement data ready for algorithm refinement."
