#!/usr/bin/env python3
"""Fail release closeout while the detailed remediation TODO has unchecked items."""

from __future__ import annotations

import argparse
import re
from pathlib import Path

UNCHECKED_RE = re.compile(r"^\s*- \[ \] (?P<label>.+?)\s*$")


def unchecked_items(text: str) -> list[tuple[int, str]]:
    """Return 1-based line numbers and labels for unchecked checklist items."""
    found: list[tuple[int, str]] = []
    for number, line in enumerate(text.splitlines(), start=1):
        match = UNCHECKED_RE.match(line)
        if match:
            found.append((number, match.group("label")))
    return found


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify the detailed remediation TODO is fully reconciled."
    )
    parser.add_argument(
        "--todo",
        type=Path,
        default=Path("docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md"),
        help="Path to the authoritative remediation TODO.",
    )
    args = parser.parse_args()

    text = args.todo.read_text(encoding="utf-8")
    remaining = unchecked_items(text)
    if not remaining:
        print(f"closeout guard: PASS ({args.todo})")
        return 0

    print(
        f"closeout guard: FAIL: {len(remaining)} unchecked item(s) remain in "
        f"{args.todo}"
    )
    for line_number, label in remaining[:50]:
        print(f"  line {line_number}: {label}")
    if len(remaining) > 50:
        print(f"  ... {len(remaining) - 50} additional unchecked item(s)")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
