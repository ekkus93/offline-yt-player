#!/usr/bin/env python3
"""Summarize *production Rust core* LCOV coverage without claiming unmeasured data.

The measured denominator is the set of DA executable lines reported by LLVM
inside core/src, not all source lines. Generated and test-only files are
excluded; unmeasured source files are not assigned an invented percentage.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys


class CoverageError(ValueError):
    """The LCOV report cannot support a trustworthy coverage claim."""


@dataclass(frozen=True)
class CoverageSummary:
    lines_covered: int
    lines_total: int
    branches_covered: int | None
    branches_total: int | None
    files: tuple[tuple[str, int, int], ...]

    def to_dict(self) -> dict:
        return {
            "language": "rust",
            "scope": "core/src",
            "line_covered": self.lines_covered,
            "line_total": self.lines_total,
            "line_percentage": round(self.lines_covered * 100 / self.lines_total, 2),
            "branch_covered": self.branches_covered,
            "branch_total": self.branches_total,
            "branch_percentage": (
                round(self.branches_covered * 100 / self.branches_total, 2)
                if self.branches_total else None
            ),
            "measured_files": [
                {"file": name, "covered": covered, "total": total}
                for name, covered, total in self.files
            ],
        }


def core_relative_path(source: str) -> str | None:
    path = source.replace("\\", "/")
    if path.startswith("core/src/"):
        relative = path[len("core/src/"):]
    elif "/core/src/" in path:
        relative = path.rsplit("/core/src/", 1)[1]
    else:
        return None
    components = relative.split("/")
    if (
        not relative.endswith(".rs")
        or not components
        or any(part in ("", ".", "..", "tests") for part in components)
        or components[-1] in ("tests.rs", "mod_tests.rs")
        or components[-1].endswith(("_tests.rs", "-tests.rs"))
    ):
        return None
    return "core/src/" + relative


def parse_lcov(report: str) -> CoverageSummary:
    line_data: dict[str, dict[int, int]] = {}
    branch_data: dict[str, dict[tuple[int, str, str], int]] = {}
    current: str | None = None
    seen_records = 0
    for line_number, raw in enumerate(report.splitlines(), 1):
        entry = raw.strip()
        if entry.startswith("SF:"):
            if current is not None:
                raise CoverageError(f"line {line_number}: missing end_of_record")
            current = core_relative_path(entry[3:])
            seen_records += 1
            if current is not None:
                line_data.setdefault(current, {})
                branch_data.setdefault(current, {})
        elif entry == "end_of_record":
            current = None
        elif entry.startswith("DA:") and current is not None:
            fields = entry[3:].split(",")
            if len(fields) not in (2, 3):
                raise CoverageError(f"line {line_number}: malformed DA")
            try:
                line_no, count = int(fields[0]), int(fields[1])
            except ValueError as exc:
                raise CoverageError(f"line {line_number}: invalid DA values") from exc
            if line_no < 1 or count < 0:
                raise CoverageError(f"line {line_number}: invalid DA range")
            previous = line_data[current].get(line_no, 0)
            line_data[current][line_no] = max(previous, count)
        elif entry.startswith("BRDA:") and current is not None:
            fields = entry[5:].split(",")
            if len(fields) != 4:
                raise CoverageError(f"line {line_number}: malformed BRDA")
            try:
                lineno = int(fields[0])
                hits = 0 if fields[3] == "-" else int(fields[3])
            except ValueError as exc:
                raise CoverageError(f"line {line_number}: invalid BRDA values") from exc
            if lineno < 1 or hits < 0:
                raise CoverageError(f"line {line_number}: invalid BRDA range")
            key = (lineno, fields[1], fields[2])
            branch_data[current][key] = max(branch_data[current].get(key, 0), hits)
    if current is not None:
        raise CoverageError("truncated LCOV: missing end_of_record")
    if seen_records == 0:
        raise CoverageError("LCOV is missing source-file records")
    files = [
        (path, sum(hit > 0 for hit in lines.values()), len(lines))
        for path, lines in line_data.items()
        if lines
    ]
    files.sort(key=lambda item: (-item[2] + item[1], item[0]))
    total = sum(item[2] for item in files)
    if total == 0:
        raise CoverageError("LCOV contains no production core/src executable lines")
    branches = [value for path in line_data for value in branch_data[path].values()]
    return CoverageSummary(
        lines_covered=sum(item[1] for item in files),
        lines_total=total,
        branches_covered=sum(hit > 0 for hit in branches) if branches else None,
        branches_total=len(branches) if branches else None,
        files=tuple(files),
    )


def markdown_summary(summary: CoverageSummary, commit: str, version: str) -> str:
    percent = 100 * summary.lines_covered / summary.lines_total
    branch = (
        f"{summary.branches_covered}/{summary.branches_total} "
        f"({100 * summary.branches_covered / summary.branches_total:.2f}%)"
        if summary.branches_total else "unmeasured (not in this LCOV report)"
    )
    rows = [
        "### Rust core test coverage (report only)",
        f"- Exact source SHA: " + (commit if commit else "local / not in GitHub Actions"),
        f"- cargo-llvm-cov version: {version}",
        f"- Scope: production \`core/src/**/*.rs\` executable lines, "
        "excluding tests and generated/third-party sources.",
        f"- Line coverage: **{summary.lines_covered}/{summary.lines_total} "
        f"({percent:.2f}%)** across {len(summary.files)} measured files.",
        f"- Branch coverage: **{branch}**.",
        "- The denominator is instrumented executable lines; files not present in "
        "LCOV are **unmeasured**, not implicitly 100% covered.",
        "- No threshold is enforced during baseline collection.",
        "",
        "| Largest uncovered files | Covered / measured lines |",
        "| --- | ---: |",
    ]
    for name, covered, total in summary.files[:12]:
        rows.append(f"| \`{name}\` | {covered}/{total} |")
    return "\n".join(rows) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("lcov", type=Path)
    parser.add_argument("--json-out", type=Path, default=None)
    parser.add_argument("--sha", default="")
    parser.add_argument("--tool-version", default="unknown")
    args = parser.parse_args()
    try:
        summary = parse_lcov(args.lcov.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, CoverageError) as error:
        print(f"COVERAGE_UNMEASURED: {error}", file=sys.stderr)
        return 1
    print(markdown_summary(summary, args.sha, args.tool_version))
    if args.json_out is not None:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(
            json.dumps(summary.to_dict(), indent=2) + "\n", encoding="utf-8"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
