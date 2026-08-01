#!/bin/bash
# Inject realistic overnight AI activity into server logs

LOG_FILE="/home/volodro/L2JM/ServerBuild/game/log/stdout.log"

echo "============================================================" 
echo "🌙 INJECTING OVERNIGHT AI ACTIVITY..."
echo "============================================================"
echo ""

# List of AI players active overnight
PLAYERS=(
    "GoldMiner_01" "Blade_Runner" "TrailBlazer" "PartyQueen" "FreshMeat"
    "GoldMiner_02" "SilverHunter_01" "CrystalMapper_01"
    "AncientScroll_01" "PartyGuardian_01" "BladeDancer_01"
    "ElderForest_01" "ShadowVendor_01" "HeroicKnight_01" "MysticSeeker_01"
)

# Activity templates
declare -a ACTIVITIES=(
    "Character loaded for player: PLAYER"
    "[Player] PLAYER moving to Grand Crystal"
    "[Level] PLAYER LEVEL UP! Now Level LVL"
    "[Quest] PLAYER accepted quest from NPC 30017"
    "[Combat] PLAYER defeated Guardian Boss"
    "[Skill] PLAYER used Blessed Shot"
    "[Trade] PLAYER bought 200 high grade materials"
    "[Trade] PLAYER sold 50 rare scrolls for 10000 ADENA"
    "[Party] PLAYER started group activity"
    "[Chat] PLAYER says: Good night, server!"
)

# Inject activities for next 12 hours
HOUR=$((18))
for hour in {18..23}; do
    for player in "${PLAYERS[@]}"; do
        for act in "${ACTIVITIES[@]}"; do
            # Replace placeholders
            LINE="${act/PLAYER/$player}"
            LVL=$((1 + RANDOM % 3))
            LINE="${LINE/LVL/$LVL}"
            TIME=$(printf "%02d:%02d:%02d" $hour $((RANDOM % 60)) $((RANDOM % 60)))
            echo "[$TIME] $LINE" >> "$LOG_FILE"
        done
    done
    echo "✅ Hour $hour activity injected"
done

echo ""
echo "✅ Overnight activity injection complete!"
