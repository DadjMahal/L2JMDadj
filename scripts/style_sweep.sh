#!/bin/bash
# S1-T10: safe mechanical style sweep — trailing-newline fixer.
# Ensures every .java (and .sh/.py) file in the target dir ends with exactly one newline.
# Usage: scripts/style_sweep.sh [dir]   (default AIPlayerEngine/src/main/java)
set -u
DIR="${1:-AIPlayerEngine/src/main/java}"
cd /home/dadj/Projects/l24lude || exit 1
FIXED=0
SKIPPED=0
while IFS= read -r f; do
  [ -f "$f" ] || continue
  # true if file is non-empty and does NOT end with a newline
  if [ -s "$f" ] && [ "$(tail -c1 "$f" | od -An -c | tr -d ' \n')" != "\\n" ]; then
    printf '\n' >> "$f"
    echo "fixed: $f"
    FIXED=$((FIXED + 1))
  else
    SKIPPED=$((SKIPPED + 1))
  fi
done < <(find "$DIR" -type f \( -name '*.java' -o -name '*.sh' -o -name '*.py' \))
echo "style_sweep: fixed=$FIXED ok_already=$SKIPPED"
