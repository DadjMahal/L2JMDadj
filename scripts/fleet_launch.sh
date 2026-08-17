#!/bin/bash
# S9-T04: reusable fleet launcher with race rotation.
# Usage: scripts/fleet_launch.sh [count] [dashPort] [accountPrefix] [charIdBase] [races]
#   races  = "random" | comma list ("ELF,DARK_ELF,ORC,DWARF,HUMAN") | empty -> all Human
#   Example: scripts/fleet_launch.sh 50 8210 ai_rand_ 500000 ELF,DARK_ELF,ORC,DWARF,HUMAN
set -u
COUNT="${1:-5}"
DASH="${2:-8210}"
PREFIX="${3:-ai_combat_}"
CHARID="${4:-100000}"
RACES="${5:-}"
ENGINE=/home/dadj/Projects/l24lude/AIPlayerEngine
LOG="/tmp/fleet_launch_${PREFIX}.log"
cd "$ENGINE" || exit 1
ARGS="$COUNT 127.0.0.1 7777 2106 $DASH movement $PREFIX $CHARID"
[ -n "$RACES" ] && ARGS="$ARGS $RACES"
setsid -f ~/.jdk/jdk-25.0.4+7/bin/java -cp target/classes:src/main/resources \
  com.aiplayer.examples.FleetPlay $ARGS </dev/null >"$LOG" 2>&1 &
echo "launched: FleetPlay $ARGS  (log=$LOG)"
