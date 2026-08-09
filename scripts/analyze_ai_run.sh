#!/bin/bash
# analyze_ai_run.sh — post-run report for a MultiPlayerSession experiment.
# Run AFTER the 2h window: bash scripts/analyze_ai_run.sh [rundir]
# Reads samples.tsv (60s DB snapshots) + mpsession.out (driver markers) and prints:
#   per-account online time / sessions / engages / actions / deaths / level-ups / exp gain,
#   engagement rate (from DIAG), top targets, and overall run summary.
set -u
RUN="${1:-$(cat /tmp/run_dir.txt 2>/dev/null)}"
RUN="/home/volodro/L2JM/$RUN"
TSV="$RUN/samples.tsv"
LOG="$RUN/mpsession.out"

if [ ! -f "$TSV" ] || [ ! -f "$LOG" ]; then
    echo "Missing data files in $RUN"
    echo "  expected $TSV and $LOG"
    exit 1
fi
if [ ! -s "$TSV" ]; then echo "samples.tsv is empty"; exit 1; fi

echo "════════════════════════════════════════════════════════════"
echo "  AI PLAYERS RUN REPORT — $RUN"
echo "  generated: $(date '+%Y-%m-%d %H:%M:%S')"
echo "════════════════════════════════════════════════════════════"

# --- Run spans ---------------------------------------------------
T0=$(awk 'NR==2{print $1}' "$TSV" 2>/dev/null)
TN=$(tail -1 "$TSV" 2>/dev/null | cut -f1)
START_STR=$(date -u -d "@${T0:-0}" '+%H:%M' 2>/dev/null)
END_STR=$(date -u -d "@${TN:-0}" '+%H:%M' 2>/dev/null)
SAMPLES=$(($(wc -l < "$TSV")-1))
echo "  Window:        $START_STR UTC -> $END_STR UTC   ($SAMPLES sampled minutes => ~$((SAMPLES)) min)"
echo

# --- Per-account stats ------------------------------------------
# Header
printf '%-16s %7s %7s %7s %7s %7s %7s %10s %9s\n' \
  ACCOUNT ONLINE_MIN SESSIONS ENGAGES ACTIONS DEATHS LEVELUPS EXP_GAIN ENGAGE_pct
ONLINE_TOT=0
while read -r acc; do
    # online minutes from samples
    MIN_ON=$(awk -v a="$acc" -F'\t' '$2==a && $3==1{c++} END{print c+0}' "$TSV")
    # exp at first/last sample
    E0=$(awk -v a="$acc" -F'\t' '$2==a{n++; if(n==1) e0=$5} END{print e0+0}' "$TSV")
    EN=$(awk -v a="$acc" -F'\t' '$2==a{e=$5} END{print e+0}' "$TSV")
    EXP_GAIN=$(( ${EN:-0} - ${E0:-0} ))
    # last level
    LV=$(awk -v a="$acc" -F'\t' '$2==a{l=$4} END{print l+0}' "$TSV")
    SESS=$(grep -c "\[MP\] $acc IN-WORLD" "$LOG" 2>/dev/null || true)
    ENG=$(grep -c "\[MP\] $acc ENGAGE" "$LOG" 2>/dev/null || true)
    ACT=$(grep -oE "\[MP\] $acc ENGAGE .*actions=[0-9]+" "$LOG" 2>/dev/null | grep -oE 'actions=[0-9]+' | tail -1 | cut -d= -f2)
    DEAD=$(grep -c "\[MP\] $acc DEAD" "$LOG" 2>/dev/null || true)
    LUP=$(grep -c "\[MP\] $acc LEVEL-UP" "$LOG" 2>/dev/null || true)
    # engagement rate from DIAG lines
    DT=$(grep "\[MP\] $acc DIAG" "$LOG" 2>/dev/null | wc -l)
    DA=$(grep "\[MP\] $acc DIAG action=ATTACK" "$LOG" 2>/dev/null | wc -l)
    if [ "${DT:-0}" -gt 0 ]; then PCT=$(( (DA*100)/DT )); else PCT=0; fi
    printf '%-16s %7d %7d %7d %7s %7d %7d %10d %8d%%\n' \
      "$acc" "${MIN_ON:-0}" "${SESS:-0}" "${ENG:-0}" "${ACT:-0}" "${DEAD:-0}" "${LUP:-0}" "${EXP_GAIN:-0}" "$PCT"
    ONLINE_TOT=$((ONLINE_TOT + MIN_ON))
done < <(awk -F'\t' 'NR>1{print $2}' "$TSV" | sort -u)

echo
echo "  Total bot-minutes online: $ONLINE_TOT"
echo

# --- Aggregate + behavioral notes --------------------------------
echo "── Aggregate markers ──"
echo "  login/first-enter events : $(grep -c 'IN-WORLD' "$LOG")"
echo "  engage (attack bursts)   : $(grep -c 'ENGAGE' "$LOG")"
echo "  deaths                   : $(grep -c ' DEAD ' "$LOG")"
echo "  level-ups                : $(grep -c 'LEVEL-UP' "$LOG")"
echo "  login failures           : $(grep -c 'LOGIN-FAIL' "$LOG")"
echo "  slice errors / fatal     : $(grep -cE 'SLICE-ERR|FATAL' "$LOG")"
echo
echo "── Decision mix (from DIAG, every 10s per bot) ──"
grep 'DIAG' "$LOG" | grep -oE 'action=[A-Z_]+' | sort | uniq -c | sort -rn
echo
echo "── Most-targeted NPC objectIds ──"
grep 'ENGAGE' "$LOG" | grep -oE 'target=[0-9]+' | sort | uniq -c | sort -rn | head -8
echo
echo "── Live positions at last sample (where bots ended) ──"
awk -F'\t' 'NR>1{pos[$2]=$6","$7","$8} END{for (a in pos) printf "  %-16s %s\n", a, pos[a]}' "$TSV" | sort
echo
echo "════════════════════════════════════════════════════════════"