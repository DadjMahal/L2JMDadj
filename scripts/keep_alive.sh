#!/bin/bash
# S9-T08: keep the fleet alive — relaunch the 50-bot fleet if the process dies, every 60s.
# Usage: scripts/keep_alive.sh [launchCmd]   (run under cron/@reboot or as a background loop)
set -u
LAUNCH="${1:-/home/dadj/Projects/l24lude/scripts/fleet_launch.sh 50 8210 ai_rand_ 500000 ELF,DARK_ELF,ORC,DWARF,HUMAN}"
PATTERN="FleetPlay 50 "
for i in $(seq 1 999999); do
  if ! pgrep -f "$PATTERN" >/dev/null 2>&1; then
    echo "[keep_alive $(date +%H:%M:%S)] fleet down — relaunching"
    eval "$LAUNCH"
  fi
  sleep 60
done