#!/usr/bin/env python3
"""Fail Android smoke if required packaged instrumentation test classes never ran."""

from __future__ import annotations

import argparse
import xml.etree.ElementTree as ET
from pathlib import Path


def executed_test_classes(root: Path) -> set[str]:
    executed: set[str] = set()
    for path in sorted(root.rglob("*.xml")):
        try:
            tree = ET.parse(path)
        except ET.ParseError as error:
            raise ValueError(f"unreadable Android instrumentation XML: {path}") from error
        for case in tree.iter("testcase"):
            class_name = case.get("classname") or ""
            if not class_name or not case.get("name"):
                continue
            if case.find("skipped") is None:
                # Gradle already fails the test task for failed cases. This guard
                # specifically catches zero-tests/entirely-skipped false greens.
                executed.add(class_name)
    return executed


def missing_required_classes(root: Path, required: set[str]) -> list[str]:
    return sorted(required - executed_test_classes(root))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results-dir", type=Path, required=True)
    parser.add_argument("--required-class", action="append", required=True)
    args = parser.parse_args()
    if not args.results_dir.is_dir():
        parser.error(f"instrumentation XML directory not found: {args.results_dir}")
    present = executed_test_classes(args.results_dir)
    missing = sorted(set(args.required_class) - present)
    for entry in sorted(present):
        print(f"ANDROID_SMOKE_EXECUTED {entry}")
    if missing:
        for entry in missing:
            print(f"ANDROID_SMOKE_MISSING_REQUIRED {entry}")
        return 1
    print(f"ANDROID_SMOKE_REQUIRED_CLASSES_OK count={len(set(args.required_class))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
