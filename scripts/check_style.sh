#!/bin/bash
# Style Consistency Checker — Stream F (TASKS.md task 95)
#
# Enforces the AI Player Engine's coding conventions so multi-agent work doesn't drift:
#   1. No tabs (project uses 4-space indentation)
#   2. No trailing whitespace on lines
#   3. No Math.random() in src/main (decisions must be deterministic — Streams D/E removed all)
#   4. No System.out.println in src/main (must use java.util.logging)
#   5. TODO/FIXME in src/main is flagged (should be resolved)
#   6. No CRLF line endings
#
# Usage: bash scripts/check_style.sh   (exit 0 = clean; exit 1 = violations found)
# Add a "docs-only" LEGIT_TODO comment to a TODO to allow it (see below).

CODEBASE="/home/volodro/L2JM/AIPlayerEngine/src/main/java"
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

# 3. Math.random() in src/main
RAND=$(grep -rl "Math.random()" "$CODEBASE" --include="*.java" 2>/dev/null)
if [ -n "$RAND" ]; then note "Math.random() (non-deterministic decision) in:"; echo "$RAND"; else echo -e "${GREEN}[ok]${RESET} no Math.random() in src/main"; fi

# 4. System.out.println in src/main
PRINT=$(grep -rl "System.out.println\|System.out.print" "$CODEBASE" --include="*.java" 2>/dev/null)
if [ -n "$PRINT" ]; then note "System.out (should use logging) in:"; echo "$PRINT"; else echo -e "${GREEN}[ok]${RESET} no System.out in src/main"; fi

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
