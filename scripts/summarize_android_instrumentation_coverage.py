#!/usr/bin/env python3
"""Validate AGP on-emulator JaCoCo coverage, independent of JVM/Kover totals."""
from __future__ import annotations

import argparse
from pathlib import Path
import sys
from xml.etree import ElementTree

from scripts.summarize_kotlin_coverage import (
    CoverageError,
    parse_kover_xml,
)


def verify_instrumented_ui_path(report: str) -> dict:
    # JaCoCo/AGP XML has the same package/sourcefile/line structure as Kover XML;
    # do not conflate the execution contexts or label this as a JVM test result.
    value = parse_kover_xml(report)
    try:
        root = ElementTree.fromstring(report)
    except ElementTree.ParseError as exc:
        raise CoverageError(f"bad Android coverage XML: {exc}") from exc
    hits = []
    for package in root.findall("package"):
        if package.get("name") != "com/ekkus/offlineytplayer/ui":
            continue
        for sourcefile in package.findall("sourcefile"):
            filename = sourcefile.get("name", "")
            if not filename.endswith(".kt") or filename.endswith("Test.kt"):
                continue
            covered = 0
            for line in sourcefile.findall("line"):
                try:
                    covered += int(line.get("ci", "0")) > 0
                except ValueError as exc:
                    raise CoverageError("invalid UI sourcefile coverage") from exc
            if covered > 0:
                hits.append((filename, covered))
    if not hits:
        raise CoverageError(
            "instrumented API 29 test did not cover a production UI source file"
        )
    return {
        "language": "kotlin-android-instrumentation",
        "device": "API 29 x86_64 emulator",
        "line_covered": value.line_covered,
        "line_total": value.line_total,
        "line_percentage": value.as_dict()["line_percentage"],
        "branch_covered": value.branch_covered,
        "branch_total": value.branch_total,
        "verified_production_ui_files": sorted(hits),
    }


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("xml", type=Path)
    p.add_argument("--sha", required=True)
    args = p.parse_args()
    try:
        summary = verify_instrumented_ui_path(args.xml.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, CoverageError) as exc:
        print(f"ANDROID_COVERAGE_UNMEASURED: {exc}", file=sys.stderr)
        return 1
    print("### Android API 29 instrumentation coverage (report only)")
    print(f"- Exact source SHA: {args.sha}")
    print("- Execution: actual Android emulator; not included in Kover JVM totals.")
    print(f"- AGP XML: {args.xml}")
    print(
        f"- Measured production lines: {summary['line_covered']}/"
        f"{summary['line_total']} ({summary['line_percentage']:.2f}%)."
    )
    print(
        "- Measured branches: "
        + (f"{summary['branch_covered']}/{summary['branch_total']}"
           if summary['branch_total'] else "unmeasured")
    )
    print(
        "- Verified covered production UI source files: "
        + ", ".join(f"{name} ({hits} lines)" for name, hits in
                    summary["verified_production_ui_files"][:8])
    )
    print("- No threshold enforced; generated UniFFI source and test code excluded.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
