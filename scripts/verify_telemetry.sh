#!/bin/bash
# Task 28: Verify Telemetry Data Integrity
# Validates all telemetry logs for completeness and data integrity

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
AI_LOGS="$PROJECT_ROOT/AIPlayerEngine/target/logs"
TELEMETRY_LOGS="$PROJECT_ROOT/AIStatusLogs"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================"
echo "  Telemetry Data Integrity Verification"
echo "========================================"
echo ""

ERRORS=0
WARNINGS=0

# Function to check log file exists
check_log_file() {
    local log_file=$1
    local description=$2
    
    if [ -f "$log_file" ]; then
        echo -e "${GREEN}✓${NC} $description: $log_file"
        return 0
    else
        echo -e "${RED}✗${NC} $description: $log_file (MISSING)"
        ((ERRORS++))
        return 1
    fi
}

# Function to check telemetry events in a file
check_events() {
    local pattern=$1
    local description=$2
    local min_count=$3
    local log_file=$4
    
    if [ -f "$log_file" ]; then
        count=$(grep -c "$pattern" "$log_file" 2>/dev/null || echo 0)
        if [ "$count" -ge "$min_count" ]; then
            echo -e "  ${GREEN}✓${NC} $description: $count events (>= $min_count required)"
            return 0
        else
            echo -e "  ${RED}✗${NC} $description: $count events (< $min_count required)"
            ((ERRORS++))
            return 1
        fi
    else
        echo -e "  ${YELLOW}?${NC} $description: log file not available"
        ((WARNINGS++))
        return 1
    fi
}

echo "=== 1. Checking Log Files ==="
check_log_file "$AI_LOGS/telemetry.log" "Telemetry log file"
check_log_file "$TELEMETRY_LOGS/ai_activity_report.txt" "AI activity report"
check_log_file "$TELEMETRY_LOGS/ai_progress_report.txt" "AI progress report"
echo ""

echo "=== 2. Verifying Telemetry Events ==="

# Check COMBAT-LOG events
echo -e "${YELLOW}Combat Telemetry:${NC}"
check_events "\[COMBAT-LOG\]" "Combat events" 1 "$AI_LOGS/telemetry.log"

# Check SOCIAL-LOG events  
echo -e "${YELLOW}Social Telemetry:"
check_events "\[SOCIAL-LOG\]" "Social events" 1 "$AI_LOGS/telemetry.log"

# Check TRADE-LOG events
echo -e "${YELLOW}Trading Telemetry:"
check_events "\[TRADE-LOG\]" "Trade events" 1 "$AI_LOGS/telemetry.log"

# Check QUEST-LOG events
echo -e "${YELLOW}Quest Telemetry:"
check_events "\[QUEST-LOG\]" "Quest events" 1 "$AI_LOGS/telemetry.log"

# Check ADENA_FLOW events
echo -e "${YELLOW}Economic Telemetry:"
check_events "\[ADENA_FLOW\]" "Adena flow events" 1 "$AI_LOGS/telemetry.log"
check_events "\[PRICE_CHANGE\]" "Price change events" 1 "$AI_LOGS/telemetry.log"

# Check PERFORMANCE events
echo -e "${YELLOW}Performance Telemetry:"
check_events "\[PERFORMANCE\]" "Performance events" 1 "$AI_LOGS/telemetry.log"
check_events "\[METRICS" "Metrics summary events" 1 "$AI_LOGS/telemetry.log"

echo ""

echo "=== 3. Checking Telemetry Structure ==="

# Check for required telemetry fields
if [ -f "$AI_LOGS/telemetry.log" ]; then
    # Check COMBAT_END has damage_dealt field
    if grep -q "damage_dealt=" "$AI_LOGS/telemetry.log" 2>/dev/null; then
        echo -e "  ${GREEN}✓${NC} Combat events include damage tracking"
    else
        echo -e "  ${YELLOW}?${NC} Combat damage tracking not found yet"
    fi
    
    # Check PRICE_CHANGE has delta field
    if grep -q "delta=" "$AI_LOGS/telemetry.log" 2>/dev/null; then
        echo -e "  ${GREEN}✓${NC} Economic events include delta tracking"
    else
        echo -e "  ${YELLOW}?${NC} Economic delta tracking not found yet"
    fi
    
    # Check PERFORMANCE has latency field
    if grep -q "latency=" "$AI_LOGS/telemetry.log" 2>/dev/null; then
        echo -e "  ${GREEN}✓${NC} Performance events include latency tracking"
    else
        echo -e "  ${YELLOW}?${NC} Performance latency tracking not found yet"
    fi
fi

echo ""

echo "=== 4. Telemetry Summary ==="
if [ -f "$AI_LOGS/telemetry.log" ]; then
    TOTAL=$(wc -l < "$AI_LOGS/telemetry.log")
    echo "Total telemetry log lines: $TOTAL"
    
    COMBAT=$(grep -c "\[COMBAT-LOG\]" "$AI_LOGS/telemetry.log" 2>/dev/null || echo 0)
    SOCIAL=$(grep -c "\[SOCIAL-LOG\]" "$AI_LOGS/telemetry.log" 2>/dev/null || echo 0)
    TRADE=$(grep -c "\[TRADE-LOG\]" "$AI_LOGS/telemetry.log" 2>/dev/null || echo 0)
    QUEST=$(grep -c "\[QUEST-LOG\]" "$AI_LOGS/telemetry.log" 2>/dev/null || echo 0)
    ECONOMIC=$(grep -c "\[ADENA_FLOW\]\|\[PRICE_CHANGE\]" "$AI_LOGS/telemetry.log" 2>/dev/null || echo 0)
    PERF=$(grep -c "\[PERFORMANCE\]\|\\[METRICS" "$AI_LOGS/telemetry.log" 2>/dev/null || echo 0)
    
    echo "  COMBAT-LOG events: $COMBAT"
    echo "  SOCIAL-LOG events: $SOCIAL"
    echo "  TRADE-LOG events: $TRADE"
    echo "  QUEST-LOG events: $QUEST"
    echo "  ECONOMIC events: $ECONOMIC"
    echo "  PERFORMANCE events: $PERF"
else
    echo -e "${YELLOW}Telemetry log not available (run AI players first)${NC}"
fi

echo ""
echo "========================================"
echo "Verification Complete"
echo "Errors: $ERRORS | Warnings: $WARNINGS"
echo "========================================"

if [ $ERRORS -gt 0 ]; then
    exit 1
else
    exit 0
fi