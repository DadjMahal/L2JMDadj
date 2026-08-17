#!/bin/bash
# S9-T10: fleet health check — fail (exit 1) when fewer than EXPECTED bots are online.
# Usage: scripts/health_check.sh [expected] [dashUrl]
set -u
EXPECTED="${1:-50}"
DASH="${2:-http://localhost:8210/api/v1/health}"
N=$(curl -s -m 6 "$DASH" 2>/dev/null | python3 -c 'import sys,json;print(json.load(sys.stdin).get("botCount",0))' 2>/dev/null || echo 0)
if [ "$N" -lt "$EXPECTED" ]; then
  echo "HEALTH ALERT: bots online $N < expected $EXPECTED ($(date +%H:%M:%S))"
  exit 1
fi
echo "OK: $N/$EXPECTED bots online"
exit 0