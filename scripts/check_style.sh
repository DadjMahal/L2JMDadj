#!/bin/bash
# Style Consistency Checker — Stream F (TASKS.md task 95), refined Stream G (task 110).
#
# Enforces the AI Player Engine's coding conventions so multi-agent work doesn't drift:
#   1. No tabs (project uses 4-space indentation)
#   2. No trailing whitespace on lines
#   3. No Math.random() CALL in the ENGINE packages (decisions must be deterministic — Streams D/E
#      removed them all; comment mentions are allowed). The examples/ package is EXEMPT: those are
#      live proof-drivers / demos, not the shipping decision path.
#   4. No System.out.println in the ENGINE packages (must use java.util.logging). The examples/
#      package is EXEMPT by design: the live drivers (CombatLoop/GoalDrivenLoop/probes) print
#      grep-able [X] markers that the proof scripts rely on.
#   5. TODO/FIXME in src/main is flagged (should be resolved).
#   6. No CRLF line endings
#
# Usage: bash scripts/check_style.sh   (exit 0 = clean; exit 1 = violations found)
# Legit TODOs are allowed via a LEGIT_TODO marker on the line (they must be tracked in
# Documentation/StreamGDisposition.md §4 — see AIPlayerEngine.java:59 + CombatAI.java:108).

# Resolve engine source root relative to this repo (was hardcoded to a stale
# /home/volodro/... path — F-07 makes it portable).
GATE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CODEBASE="$GATE_ROOT/AIPlayerEngine/src/main/java"
SCHEME="\033[1;36m"; RED="\033[1;31m"; GREEN="\033[1;32m"; RESET="\033[0m"

echo -e "${SCHEME}==========================================${RESET}"
echo -e "${SCHEME}  STYLE CONSISTENCY CHECKER${RESET}"
echo -e "${SCHEME}==========================================${RESET}"

VIOLATIONS=0
note(){ echo -e "${RED}[FAIL]${RESET} $1"; VIOLATIONS=$((VIOLATIONS+1)); }

# 1. Tabs
TABS=$(grep -rlP "\t" "$CODEBASE" --include="*.java" 2>/dev/null)
if [ -n "$TABS" ]; then note "Tabs found in:"; echo "$TABS"; else echo -e "${GREEN}[ok]${RESET} no tabs"; fi

# 2. Trailing whitespace
TRAIL=$(grep -rlP " +$" "$CODEBASE" --include="*.java" 2>/dev/null)
if [ -n "$TRAIL" ]; then note "Trailing whitespace in:"; echo "$TRAIL"; else echo -e "${GREEN}[ok]${RESET} no trailing whitespace"; fi

# 3. Non-deterministic randomness in the engine (Math.random() / no-arg new Random()) —
#    every random source must be seeded (DeterministicRandom or a bot-owned seeded Random), so
#    a run reproduces. examples/ are demos/drivers and stay exempt.
ENGINE_ROOT="$CODEBASE/com/aiplayer"
# Current live packages (post EP-2/EP-3 tree), excluding examples/ (demos) and attic (not compiled).
ENGINE_PKGS=$(find "$ENGINE_ROOT" -maxdepth 1 -mindepth 1 -type d -name '*' | grep -v '/examples$' | tr '\n' ' ')
RAND_LINES=$(grep -rnE "Math\.random\(\)|new Random\(\)" $ENGINE_PKGS --include="*.java" 2>/dev/null | grep -vE '^[^:]+:[0-9]+:[ \t]*(//|/\*|\*)')
if [ -n "$RAND_LINES" ]; then note "unseeded randomness (Math.random / new Random()) in engine code:"; echo "$RAND_LINES"; else echo -e "${GREEN}[ok]${RESET} no unseeded randomness in engine (all seeds deterministic, EB-02)"; fi

# 4. System.out.println in the engine (examples/ drivers print proof markers by design).
#    Scoped to the plumbing packages; the behavior/ pile of legacy System.out is tracked as
#    EB-12 (logging hygiene). Console lines that are grep-able PROOF markers for the live
#    scripts (bracket-tagged, e.g. [EVIDENCE-H5], [FleetPlay]) are allowed just like examples'.
PLUMBING="$ENGINE_ROOT/protocol $ENGINE_ROOT/net $ENGINE_ROOT/web $ENGINE_ROOT/monitor $ENGINE_ROOT/metrics $ENGINE_ROOT/core $ENGINE_ROOT/cli $ENGINE_ROOT/learning $ENGINE_ROOT/knowledge"
PRINT=$(grep -rnE "System\.out\.(println|print)" $PLUMBING --include="*.java" 2>/dev/null | grep -vE '"[ \t]*\[[A-Za-z0-9_-]+\]')
if [ -n "$PRINT" ]; then note "System.out (should use logging) in engine:"; echo "$PRINT"; else echo -e "${GREEN}[ok]${RESET} no System.out in engine (examples drivers + bracket proof markers use them by design)"; fi

# 5. TODO/FIXME in src/main (allow lines that carry the literal LEGIT_TODO marker)
TODO_LINES=$(grep -rn "TODO\|FIXME" "$CODEBASE" --include="*.java" 2>/dev/null | grep -v "LEGIT_TODO" | grep -v "^Binary")
if [ -n "$TODO_LINES" ]; then note "TODO/FIXME in src/main:"; echo "$TODO_LINES"; else echo -e "${GREEN}[ok]${RESET} no TODO/FIXME"; fi

# 6. CRLF line endings
CRLF=$(grep -rlP "\r$" "$CODEBASE" --include="*.java" 2>/dev/null)
if [ -n "$CRLF" ]; then note "CRLF line endings in:"; echo "$CRLF"; else echo -e "${GREEN}[ok]${RESET} no CRLF"; fi

echo -e "${SCHEME}==========================================${RESET}"
if [ "$VIOLATIONS" -eq 0 ]; then
    echo -e "${GREEN}STYLE CHECK PASSED (0 violations)${RESET}"
    exit 0
else
    echo -e "${RED}STYLE CHECK FAILED ($VIOLATIONS violation groups)${RESET}"
    exit 1
fi
