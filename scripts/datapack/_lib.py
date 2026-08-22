#!/usr/bin/env python3
"""GK-1 — shared helpers for datapack JSON extractors.

Source of truth: SourceCode/dist/game/data/ (READ-ONLY — extractors NEVER write there).
Target: generated JSON committed to AIPlayerEngine/src/main/resources/knowledge/.
"""
from __future__ import annotations

import math
import re
import xml.etree.ElementTree as ET
from pathlib import Path

# Root of the repo (4 levels up from scripts/datapack/).
REPO_ROOT = Path(__file__).resolve().parents[2]
DATA_ROOT = REPO_ROOT / "SourceCode" / "dist" / "game" / "data"
TARGET_ROOT = REPO_ROOT / "AIPlayerEngine" / "src" / "main" / "resources" / "knowledge"

# World bounds (L2 interlude): coordinate sanity floor/ceiling.
WORLD_X_MIN, WORLD_X_MAX = -204_800, 204_800
WORLD_Y_MIN, WORLD_Y_MAX = -204_800, 204_800
WORLD_Z_MIN, WORLD_Z_MAX = -16_000, 16_000

# ASCII-only alnum → keep the JSON files portable; drop diacritics.
_DIACRITIC = re.compile(r"[\u0300-\u036f]")


def xml_files(root: Path, suffix: str = ".xml") -> list[Path]:
    """All XML files under root (recursive), sorted for deterministic output."""
    return sorted(root.rglob("*" + suffix))


def parse_xml(path: Path) -> ET.Element | None:
    """Parse an XML file; return the root element or None on parse error (caller logs)."""
    try:
        return ET.parse(path).getroot()
    except ET.ParseError:
        return None


def norm_name(raw: str | None) -> str:
    """Normalize a name: strip tags/entities, collapse whitespace, drop diacritics."""
    if not raw:
        return ""
    s = raw.replace("&nbsp;", " ").replace("&amp;", "&")
    s = re.sub(r"<[^>]+>", " ", s)
    s = "".join(ch for ch in s if not _DIACRITIC.search(ch))
    s = re.sub(r"\s+", " ", s).strip()
    return s


def norm_id(value: str | None) -> int | None:
    """Parse an int id; None when missing/not a number (validator flags later)."""
    if value is None:
        return None
    try:
        n = int(value)
        return n if n >= 0 else None
    except (TypeError, ValueError):
        return None


def round_coord(value: str | None) -> int | None:
    """Coordinate → nearest int (server coords are exact ints; nothing to round)."""
    if value is None:
        return None
    try:
        return int(float(value))
    except (TypeError, ValueError):
        return None


def in_world_bounds(x: int | None, y: int | None, z: int | None) -> bool:
    """True when the coordinate triple is inside the world (or all three are None)."""
    if x is None and y is None and z is None:
        return True
    if x is None or y is None or z is None:
        return False
    return (WORLD_X_MIN <= x <= WORLD_X_MAX
            and WORLD_Y_MIN <= y <= WORLD_Y_MAX
            and WORLD_Z_MIN <= z <= WORLD_Z_MAX)


def in_open_unit_interval(chance: float | None) -> bool:
    """True when chance is None (unset, not a violation) or in (0, 1]."""
    return chance is None or (0.0 < chance <= 1.0)


def ensure_target(path: Path) -> None:
    """Create the parent dir of a target JSON file (idempotent)."""
    path.parent.mkdir(parents=True, exist_ok=True)


def write_json(path: Path, obj) -> None:
    """Serialize obj to path with a stable indent; returns nothing, raises on failure."""
    import json
    ensure_target(path)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, indent=2, sort_keys=True)
        fh.write("\n")


def read_json(path: Path):
    """Load a generated JSON file; returns None when missing/unparseable (validator flags)."""
    import json
    try:
        with open(path, "r", encoding="utf-8") as fh:
            return json.load(fh)
    except (OSError, ValueError):
        return None