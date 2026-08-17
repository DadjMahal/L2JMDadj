#!/bin/bash
# S9-T01: fleet watcher wrapper — background + pid, per .clinerules (no tail -f in-turn).
# Usage: scripts/watch_fleet.sh [dashUrl] [notesPath] [intervalSec] [durationMin]
#   defaults: dash http://localhost:8210/json, notes /tmp/watch_fleet.log, 120s, 120min
set -u
DASH="${1:-http://localhost:8210/json}"
NOTES="${2:-/tmp/watch_fleet.log}"
INTERVAL="${3:-120}"
DUR="${4:-120}"
SDIR="$(cd "$(dirname "$0")" && pwd)"
nohup python3 "$SDIR/watch_fleet.py" "$DASH" "$NOTES" "$INTERVAL" "$DUR" >/dev/null 2>&1 &
echo $! > /tmp/watch_fleet.pid
echo "watcher pid=$(cat /tmp/watch_fleet.pid) notes=$NOTES"
