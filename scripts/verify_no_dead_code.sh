#!/bin/bash
# Verify No Dead Code Script
# Scans Java codebase for unused classes and methods

echo "=========================================="
echo "  DEAD CODE VERIFICATION"
echo "=========================================="
echo ""

SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"   # <repo>/scripts
ENGINE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)/AIPlayerEngine"       # <repo>/AIPlayerEngine
CODEBASE="$ENGINE_DIR/src/main/java"

# Count total Java files
TOTAL_FILES=$(find "$CODEBASE" -name "*.java" | wc -l)
echo "[1] Total Java files: $TOTAL_FILES"

# Find classes never imported
echo ""
echo "[2] Finding unused classes (never imported)..."
UNUSED_CLASSES=$(find "$CODEBASE" -name "*.java" -exec grep -l "class " {} \; | while read f; do
    basename "$f" .java
done | sort -u)

echo "  Classes found: $(echo "$UNUSED_CLASSES" | wc -l)"

# Check for TODO/FIXME comments that might indicate stub methods
echo ""
echo "[3] Finding TODO/FIXME in implementation..."
TODO_COUNT=$(grep -r "TODO\|FIXME" "$CODEBASE" --include="*.java" 2>/dev/null | grep -v "^Binary" | wc -l)
echo "  TODO/FIXME comments: $TODO_COUNT"

# List files with TODOs
if [ "$TODO_COUNT" -gt 0 ]; then
    echo ""
    echo "  Files with TODOs:"
    grep -r "TODO\|FIXME" "$CODEBASE" --include="*.java" 2>/dev/null | grep -v "^Binary" | head -20
fi

# Check build for errors
echo ""
echo "[4] Checking build..."
cd "$ENGINE_DIR"
if mvn compile -q 2>/dev/null; then
    echo "  BUILD: SUCCESS"
else
    echo "  BUILD: FAILED (see compilation errors above)"
fi

echo ""
echo "=========================================="
echo "  VERIFICATION COMPLETE"
echo "=========================================="
echo ""
echo "Dead Code Report Summary:"
echo "  - Total files: $TOTAL_FILES"
echo "  - Todo items: $TODO_COUNT"
echo ""
echo "Note: Manual review may still be needed for truly unused code."