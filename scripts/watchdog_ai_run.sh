#!/bin/bash
# watchdog_ai_run.sh — every 120s, if the MultiPlayerSession driver died, relaunch it
# (fresh slice) so the 2h experiment keeps collecting. Does NOT touch the collector.
# Usage (start detached): setsid nohup bash scripts/watchdog_ai_run.sh <rundir> &
set -u
RUN="${1:-$(cat /tmp/run_dir.txt)}"
ENGINE=/home/volodro/L2JM/AIPlayerEngine
LOG=/home/volodro/L2JM/AIStatusLogs/watchdog_$(date +%Y%m%d_%H%M).log
echo "[watchdog] watching $RUN -> $LOG" >> "$LOG"
while true; do
    if ! jps -l 2>/dev/null | grep -q MultiPlayerSession; then
        echo "$(date +%H:%M:%S) [watchdog] driver died -> relaunch" >> "$LOG"
        cd /home/volodro/L2JM && setsid nohup bash scripts/start_mp.sh 170 600 "/home/volodro/L2JM/$RUN/mpsession.out" >> /dev/null 2>&1 < /dev/null &
    fi
    sleep 120
done