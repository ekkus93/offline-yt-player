#!/usr/bin/env python3
"""Fail Android smoke if required packaged instrumentation test classes never ran."""

from __future__ import annotations

import argparse
import xml.etree.ElementTree as ET
from pathlib import Path


def executed_test_methods(root: Path) -> set[tuple[str, str]]:
    executed: set[tuple[str, str]] = set()
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
                executed.add((class_name, case.get("name")))
    return executed


def executed_test_classes(root: Path) -> set[str]:
    return {class_name for class_name, _ in executed_test_methods(root)}


def missing_required_classes(root: Path, required: set[str]) -> list[str]:
    return sorted(required - executed_test_classes(root))


def missing_required_methods(root: Path, required: set[tuple[str, str]]) -> list[str]:
    return sorted(f"{klass}#{method}" for klass, method in (required - executed_test_methods(root)))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results-dir", type=Path, required=True)
    parser.add_argument("--required-class", action="append", required=True)
    parser.add_argument("--required-test", action="append", default=[], help="Required fully qualified CLASS#METHOD")
    args = parser.parse_args()
    if not args.results_dir.is_dir():
        parser.error(f"instrumentation XML directory not found: {args.results_dir}")
    executed_methods = executed_test_methods(args.results_dir)
    present = {klass for klass, _ in executed_methods}
    missing = sorted(set(args.required_class) - present)
    required_methods = set()
    for entry in args.required_test:
        if "#" not in entry:
            parser.error("--required-test must have CLASS#METHOD format")
        klass, method = entry.rsplit("#", 1)
        if not klass or not method:
            parser.error("--required-test must have CLASS#METHOD format")
        required_methods.add((klass, method))
    missing_methods = sorted(required_methods - executed_methods)
    for entry in sorted(present):
        print(f"ANDROID_SMOKE_EXECUTED {entry}")
    if missing or missing_methods:
        for entry in missing:
            print(f"ANDROID_SMOKE_MISSING_REQUIRED {entry}")
        for klass, method in missing_methods:
            print(f"ANDROID_SMOKE_MISSING_REQUIRED_TEST {klass}#{method}")
        return 1
    print(f"ANDROID_SMOKE_REQUIRED_CLASSES_OK count={len(set(args.required_class))}")
    if required_methods:
        print(f"ANDROID_SMOKE_REQUIRED_TESTS_OK count={len(required_methods)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
