#!/bin/bash
# start_dashboard.sh — start/stop the AI Web Dashboard (port 8199).
# Usage: bash start_dashboard.sh [start|stop|restart|status]
set -u
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${1:-start}"

case "${1:-start}" in
  start)
    pkill -f 'dashboard\.py' 2>/dev/null
    sleep 1
    setsid nohup python3 "$DIR/dashboard.py" > /tmp/dash.log 2>&1 < /dev/null &
    echo "dashboard started -> http://<your-host>:8199/  (log: /tmp/dash.log)"
    ;;
  stop)
    pkill -f 'dashboard\.py' 2>/dev/null
    echo "dashboard stopped"
    ;;
  restart)
    bash "$0" stop; sleep 1; bash "$0" start
    ;;
  status)
    ss -tlnp 2>/dev/null | grep 8199 && echo "LISTENING" || echo "NOT RUNNING"
    ;;
esac