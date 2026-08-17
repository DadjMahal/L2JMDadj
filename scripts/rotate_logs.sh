#!/bin/bash
# S9-T07: rotate oversized fleet/server logs (50 bots => logs grow fast).
# Usage: scripts/rotate_logs.sh [sizeKb] [keep]
set -u
LIMIT="${1:-50000}"       # rotate when a log exceeds ~50MB
KEEP="${2:-5}"            # keep this many rotated copies
LOGS="/tmp/fleet_dir"
for f in /tmp/fleet_launch_*.log /tmp/fleet50.log /tmp/watch_fleet.log; do
  [ -f "$f" ] || continue
  kb=$(du -k "$f" 2>/dev/null | cut -f1)
  [ -n "$kb" ] && [ "$kb" -gt "$LIMIT" ] || continue
  for n in $(seq $((KEEP - 1)) -1 1); do mv -f "$f.$n" "$f.$((n + 1))" 2>/dev/null; done
  mv -f "$f" "$f.1"
  echo "rotated $f ($((kb / 1000))MB)"
done