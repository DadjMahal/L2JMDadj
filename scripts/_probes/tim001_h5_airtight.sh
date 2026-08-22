#!/usr/bin/env bash
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
. "$(dirname "$0")/_dash_curl.sh"
# ============================================================================
# tim001_h5_airtight.sh - airtight, reproducible TIM-001 H5 (organic XP) probe.
#
# Closes the audit-46/H5 proof gap: the earlier claim rested on a DB delta alone
# with no persisted per-kill server record. This run persists raw artifacts and
# prints a full causal chain: server per-kill SystemMessages + StatusUpdate EXP
# gains (echoed to the run log by FleetPlay's EVIDENCE-H5 hooks) cross-referenced
# against an authoritative gameserver.characters BEFORE/AFTER exp diff.
#
# Produces (committed) under  Documentation/Evidence/<stamp>-h5-airtight/:
#   characters_before.txt / characters_after.txt / db_diff.txt
#   fleet_run.log  telemetry_start.json,telemetry_end.json  dashboard.json
#   H5_SUMMARY.txt
#
# Usage: bash scripts/tim001_h5_airtight.sh [run_minutes]   (default 10)
# Env: ENGINE, MYSQL_ARGS
# ============================================================================
set -uo pipefail

ENGINE="${ENGINE:-/home/dadj/Projects/l24lude}"
RUN_MIN="${1:-10}"
RUN_SEC=$((RUN_MIN * 60))
: "${DB_USER:?set DB_USER (scripts/fleet_env.local — see fleet_env.local.example)}"
: "${DB_PASS:?set DB_PASS (scripts/fleet_env.local — see fleet_env.local.example)}"
MYSQL_ARGS="${MYSQL_ARGS:-mysql -u "$DB_USER" -p"$DB_PASS" gameserver}"
CHARS="CombatBot_01 CombatBot_02 CombatBot_03 CombatBot_04 CombatBot_05"
DASH_PORT=8080
STAMP=$(date +%Y-%m-%d_%H%M%S)
EVID="${ENGINE}/Documentation/Evidence/${STAMP}-h5-airtight"
CHAR_SQL="SELECT char_name,level,exp,x,y,z FROM characters WHERE char_name IN ($(printf '"%s",' $CHARS | sed 's/,$//'))"

mkdir -p "$EVID"
log(){ echo "[h5] $*"; echo "[h5] $*" >> "$EVID/H5_SUMMARY.txt"; }

cd "$ENGINE" || { echo "FAIL engine dir"; exit 2; }
for p in 2106 7777; do
  ss -tlnp 2>/dev/null | grep -q ":$p " || { echo "FAIL port $p down"; exit 2; }
done
log "server ports UP (2106/7777); building? classes present: $([ -d AIPlayerEngine/target/classes ] && echo yes || echo no)"
[ -d AIPlayerEngine/target/classes ] || { (cd AIPlayerEngine && mvn -o -q -DskipTests compile) || exit 2; }

log "== BEFORE snapshot ($(date +%T)) =="
$MYSQL_ARGS -e "$CHAR_SQL" 2>/dev/null | tee "$EVID/characters_before.txt"

log "launching FleetPlay (5 bots, movement FORCED ON) for ${RUN_MIN} min..."
setsid nohup env JAVA_HOME=/home/dadj/.jdk/jdk-25.0.4+7 \
  /home/dadj/.jdk/jdk-25.0.4+7/bin/java -cp "$ENGINE/AIPlayerEngine/target/classes" \
  com.aiplayer.examples.FleetPlay 5 127.0.0.1 7777 2106 "$DASH_PORT" movement \
  > "$EVID/fleet_run.log" 2>&1 &
FPID=$!
log "fleet pid=$FPID log=$EVID/fleet_run.log"

UP=0
for i in $(seq 1 $((RUN_SEC/5))); do
  sleep 5
  curl -sf "$(durl "http://127.0.0.1:$DASH_PORT/telemetry")" -o "$EVID/telemetry_start.json" 2>/dev/null && { UP=1; break; }
done
[ "$UP" = 1 ] || { log "FAIL dashboard never up"; kill "$FPID" 2>/dev/null; exit 2; }
log "dashboard up after ~$((i*5))s; capturing telemetry start"

curl -s "$(durl "http://127.0.0.1:$DASH_PORT/json")" -o "$EVID/dashboard.json"
log "run in progress ${RUN_MIN} min (start $(date +%T))..."

sleep "$RUN_SEC"

curl -s "$(durl "http://127.0.0.1:$DASH_PORT/telemetry")" -o "$EVID/telemetry_end.json"
log "run elapsed; telemetry_end captured $(date +%T)"

echo "=== stopping fleet $(date +%T) ===" >> "$EVID/H5_SUMMARY.txt"
kill "$FPID" 2>/dev/null
pkill -f "com.aiplayer.examples.FleetPlay" 2>/dev/null
log "waiting 15s for disconnect-save flush..."
sleep 15

echo "== AFTER snapshot ($(date +%T)) =="
$MYSQL_ARGS -e "$CHAR_SQL" 2>/dev/null | tee "$EVID/characters_after.txt"

# --- DB diff (authoritative outcome) ---
python3 - "$EVID" <<'PY'
import sys
ev=sys.argv[1]
def load(p):
    d={}
    for ln in open(p):
        f=ln.split('\t')
        if len(f)>=6 and f[0].startswith('CombatBot'):
            # SELECT char_name,level,exp,x,y,z -> name,level,exp,x,y,z
            d[f[0]]=(int(f[2]), int(f[1]), int(f[3]), int(f[4]), int(f[5].strip()))
    return d
b=load(ev+'/characters_before.txt'); a=load(ev+'/characters_after.txt')
out=['ACCOUNT  beforeExp -> afterExp   dExp   before->after pos']
for c in sorted(a):
    if c in b:
        be,bl,xb,yb,zb=b[c]; ae,al,x,y,z=a[c]
        out.append('%-14s %8d -> %8d  %+6d   (%d,%d,%d)'%(c,be,ae,ae-be,x,y,z))
    else:
        out.append('%-14s (missing BEFORE)'%c)
open(ev+'/db_diff.txt','w').write('\n'.join(out)+'\n')
print('\n'.join(out))
PY

# --- Per-kill causal chain from the run log (FleetPlay EVIDENCE-H5 hooks) ---
log ""
log "== per-bot kill/XP events captured in fleet_run.log (from StatusUpdate EXP) =="
i=1
for c in $CHARS; do
  acc=$(printf 'ai_combat_%02d' "$i")
  n=$(grep -c "EVIDENCE-H5.*${acc} EXP +" "$EVID/fleet_run.log" 2>/dev/null || echo 0)
  log "  $c ($acc): ${n} EXP-gain events"
  i=$((i+1))
done
log "  (full chain in fleet_run.log: per-kill EXP lines + server sysmsgs)"
log ""

# --- Authoritative post-flush re-snapshot ---
# The on-disconnect save can lag the 15-min CharacterDataStoreInterval (same cause as the
# original TIM-001 false-negative), so re-read the DB after a grace wait and refresh the diff.
log "waiting 20s then re-snapshotting DB for authoritative AFTER (flush grace)..."
sleep 20
$MYSQL_ARGS -e "$CHAR_SQL" 2>/dev/null | tee "$EVID/characters_after.txt"
python3 - "$EVID" <<'PY'
import sys
ev=sys.argv[1]
def load(p):
    d={}
    for ln in open(p):
        f=ln.split('\t')
        if len(f)>=6 and f[0].startswith('CombatBot'):
            d[f[0]]=(int(f[2]), int(f[1]), int(f[3]), int(f[4]), int(f[5].strip()))
    return d
b=load(ev+'/characters_before.txt'); a=load(ev+'/characters_after.txt')
out=['ACCOUNT  beforeExp -> afterExp   dExp   before->after pos (AUTHORITATIVE after flush)']
for c in sorted(a):
    if c in b:
        be,bl,xb,yb,zb=b[c]; ae,al,x,y,z=a[c]
        out.append('%-14s %8d -> %8d  %+6d   (%d,%d,%d)'%(c,be,ae,ae-be,x,y,z))
    else:
        out.append('%-14s (missing BEFORE)'%c)
open(ev+'/db_diff.txt','w').write('\n'.join(out)+'\n')
print('\n'.join(out))
PY

log ""
log "Artifacts written to: $EVID"
log "DONE $(date +%T)"
