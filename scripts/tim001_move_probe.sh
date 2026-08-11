#!/usr/bin/env bash
# ============================================================================
# TIM-001 deep-review evidence runner (run on the box with the LIVE stack).
#
# Purpose: prove/falsify the TIM-001 hypotheses with paste-able evidence -
#   H1: MoveToLocation(0x01) frames actually move the char server-side.
#   H2: destinations are far (VM tracks each scheduled move + server ack delta).
#   H3: bots proactively travel (zone-routed) instead of only chasing hostiles.
#   H4: DB spawn vs live position are consistent (logged & diffed).
#   H5: organic XP gain happens during the run (not just the seeded 1.4M).
#
# What it does (all evidence-first, no fabricated data):
#   1. Snapshot chars.x/y/z + exp in gameserver.characters (BEFORE).
#   2. Launch FleetPlay (default 5 bots) with phase0.movement FORCED ON via the
#      6th arg (never edits ai-player.properties; default remains OFF).
#   3. Waits RUN_MIN (default 3) minutes, then curls :8080/telemetry (MoveTelemetry.report())
#      and :8080/json (live coords).
#   4. Re-snapshots the DB (AFTER), diffs positions + exp.
#   5. Prints an H1..H5 verdict block to stdout — paste into TIM-001 "Done notes".
#
# Usage:  bash scripts/tim001_move_probe.sh [engine_dir] [run_minutes]
# Env:    ENGINE, MYSQL_ARGS (default "sudo mysql -u root gameserver")
# ============================================================================
set -uo pipefail

ENGINE="${ENGINE:-${1:-/home/volodro/L2JM/AIPlayerEngine}}"
RUN_MIN="${2:-3}"
RUN_SEC=$((RUN_MIN * 60))
MYSQL_ARGS="${MYSQL_ARGS:-sudo mysql -u root gameserver}"
CHARS="CombatBot_01 CombatBot_02 CombatBot_03 CombatBot_04 CombatBot_05"
DASH_PORT=8080

echo "================================================================"
echo "  TIM-001 move/quest/combat deep-review evidence run ($RUN_MIN min)"
echo "================================================================"
echo "[tim001] ENGINE=$ENGINE  DASH=http://localhost:$DASH_PORT/telemetry"

# 0) Preflight: engine built + server listen
cd "$ENGINE" || { echo "FAIL: engine dir not found: $ENGINE"; exit 2; }
ENGINE_POM="$ENGINE/AIPlayerEngine"
if [ ! -d "$ENGINE_POM/target/classes" ]; then
  echo "WARN: target/classes missing -> building"
  (cd "$ENGINE_POM" && mvn -o -q compile) || exit 2
fi

for p in 2106 7777; do
  if ! ss -tlnp 2>/dev/null | grep -q ":$p "; then
    echo "FAIL: port $p not listening (LoginServer/GameServer down?) — start the stack first."
    exit 2
  fi
done
echo "[tim001] server ports UP (2106 / 7777)"

# 1) BEFORE snapshot
echo "--- characters BEFORE ---"
$MYSQL_ARGS -e "SELECT char_name,level,exp,x,y,z FROM characters WHERE char_name IN ($(echo "$CHARS" | sed 's/ /","/g;s/^/"/;s/$/"/'));" 2>/dev/null || echo "(no DB rows / no DB access — H4/H5 DB diff skipped)"

# 2) Run the fleet with movement forced ON
echo "[tim001] launching FleetPlay (phase0.movement forced ON) for $RUN_MIN min..."
RUNLOG=/tmp/tim001_fleet_$(date +%s).out
setsid nohup java -cp "$ENGINE_POM/target/classes" com.aiplayer.examples.FleetPlay \
  5 127.0.0.1 7777 2106 $DASH_PORT movement \
  >"$RUNLOG" 2>&1 &
FPID=$!
echo "[tim001] fleet pid=$FPID log=$RUNLOG"

CURL_UP=0
for i in $(seq 1 $((RUN_SEC / 5))); do
  sleep 5
  if curl -sf "http://127.0.0.1:$DASH_PORT/telemetry" -o /tmp/tim001_telemetry.txt 2>/dev/null; then
    CURL_UP=1
    break
  fi
done
[ "$CURL_UP" = 1 ] || { echo "FAIL: dashboard never came up (see $RUNLOG)"; kill "$FPID" 2>/dev/null; exit 2; }
echo "[tim001] dashboard up after ~$((i * 5))s"

# Tail the run to the requested duration
sleep "$RUN_SEC"

# 3) Collect evidence from the live instrument
echo ""
echo "================ MOVE TELEMETRY (live /telemetry) ================"
curl -s "http://127.0.0.1:$DASH_PORT/telemetry" | tee /tmp/tim001_telemetry.txt
echo ""
echo "================ LIVE POSITIONS (dashboard /json) ================"
curl -s "http://127.0.0.1:$DASH_PORT/json" > /tmp/tim001_json.txt
python3 - <<'PYEOF'
import json
try:
    d = json.load(open('/tmp/tim001_json.txt'))
    for b in d.get('bots', []):
        print(f"  {b.get('account'):14s} ({b.get('name')}) L{b.get('level')} exp={b.get('exp')} state={b.get('state')} pos=({b.get('x')},{b.get('y')},{b.get('z')})")
except Exception as e:
    print("  (json parse failed: %s)" % e)
PYEOF

# 4) AFTER snapshot + DB diff
echo "--- characters AFTER ---"
$MYSQL_ARGS -e "SELECT char_name,level,exp,x,y,z FROM characters WHERE char_name IN ($(echo "$CHARS" | sed 's/ /","/g;s/^/"/;s/$/"/'));" 2>/dev/null || echo "(no DB rows / no DB access)"

# 5) Verdict recap
echo ""
echo "================ TIM-001 VERDICT RECAP =========================="
echo "  H1 (server moved)  : see EVIDENCE-H1 serverMoved / total in /telemetry"
echo "  H2 (degenerate dst): see EVIDENCE-H2 (0 / N = all far)"
echo "  H3 (proactive)     : 'travel:*' states + routing reasons in /json + fleet log"
echo "  H4 (DB vs live)    : compare BEFORE/AFTER chars.* above (also DB-vs-live x/y/z)"
echo "  H5 (organic XP)    : see EVIDENCE-H5 expGained (seeded 1.4M baseline)"
echo "  Paste this whole output into Documentation/TASKS.md TIM-001 Done notes."
echo "================================================================"

# 6) Cleanup
kill "$FPID" 2>/dev/null
pkill -f "com.aiplayer.examples.FleetPlay" 2>/dev/null
echo "[tim001] fleet stopped; runbook done."