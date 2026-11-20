#!/usr/bin/env python3
"""Conservative source-declaration mapping for Rust LLVM LCOV FN/FNDA records.

This is deliberately a *partial* named-function inventory, not whole-program
function/branch coverage. Rust monomorphizations, closures and macro-generated
symbols cannot be assigned to a specific source declaration reliably.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from dataclasses import dataclass
import json
from pathlib import Path
import re

from scripts.summarize_rust_coverage import CoverageError, core_relative_path


DECLARATION = re.compile(r"\bfn\s+([A-Za-z_][A-Za-z_0-9]*)\b")
TEST_ATTRIBUTE = re.compile(r"^\s*#\s*\[\s*cfg\s*\(\s*test\s*\)\s*\]")
TEST_MODULE = re.compile(r"^\s*(?:pub\s+)?mod\s+tests\s*\{")


@dataclass(frozen=True)
class NamedFunction:
    file: str
    line: int
    name: str
    hits: int


@dataclass(frozen=True)
class NamedFunctionReport:
    matched: tuple[NamedFunction, ...]
    raw_symbols: int
    excluded_or_unmatched: int
    unreadable_files: tuple[str, ...]

    def to_dict(self) -> dict:
        return {
            "scope": "partial source-declaration matches, not all Rust functions",
            "matched_declarations": len(self.matched),
            "matched_executed": sum(f.hits > 0 for f in self.matched),
            "raw_llvm_symbols": self.raw_symbols,
            "unmatched_or_excluded_symbols": self.excluded_or_unmatched,
            "unreadable_source_files": list(self.unreadable_files),
            "rust_branch_coverage": None,
            "functions": [
                {"file": f.file, "line": f.line, "name": f.name, "hit": f.hits > 0}
                for f in self.matched
            ],
        }


def _test_module_start(lines: list[str]) -> int | None:
    # Conservative: the remainder of a source file containing an inline
    # cfg(test) module is unmeasured here. Never count a test function as
    # production, even if the test module is not the final declaration.
    for index, line in enumerate(lines):
        if not TEST_ATTRIBUTE.match(line):
            continue
        if any(TEST_MODULE.match(candidate) for candidate in lines[index + 1:index + 5]):
            return index + 1
    return None


def _declaration_at_or_before(lines: list[str], lineno: int, symbol: str) -> tuple[int, str] | None:
    # LLVM function records normally point to the declaration or its
    # signature. Only match a nearby source fn declaration whose *name*
    # finishes the raw non-closure symbol; never invent an identity via
    # token similarity or mangled-closure parents.
    for start in range(max(1, lineno - 3), lineno + 1):
        if start > len(lines):
            continue
        match = DECLARATION.search(lines[start - 1].split("//", 1)[0])
        if match and symbol.endswith(match.group(1)):
            return start, match.group(1)
    return None


def report_named_functions(lcov: str, source_root: Path) -> NamedFunctionReport:
    current: str | None = None
    entries: dict[str, dict[str, int]] = defaultdict(dict)
    locations: dict[str, dict[str, set[int]]] = defaultdict(lambda: defaultdict(set))
    records = 0
    for number, raw in enumerate(lcov.splitlines(), 1):
        line = raw.strip()
        if line.startswith("SF:"):
            if current is not None:
                raise CoverageError(f"line {number}: missing end_of_record")
            current = core_relative_path(line[3:])
            records += 1
        elif line == "end_of_record":
            current = None
        elif current is not None and line.startswith("FN:"):
            parts = line[3:].split(",", 2)
            if len(parts) == 2:
                line_str, symbol = parts
            elif len(parts) == 3:
                line_str, _end, symbol = parts
            else:
                raise CoverageError(f"line {number}: malformed FN")
            try:
                source_line = int(line_str)
            except ValueError as exc:
                raise CoverageError(f"line {number}: invalid FN source line") from exc
            if source_line < 1 or not symbol:
                raise CoverageError(f"line {number}: invalid FN entry")
            locations[current][symbol].add(source_line)
        elif current is not None and line.startswith("FNDA:"):
            parts = line[5:].split(",", 1)
            if len(parts) != 2 or not parts[1]:
                raise CoverageError(f"line {number}: malformed FNDA")
            try:
                hits = int(parts[0])
            except ValueError as exc:
                raise CoverageError(f"line {number}: invalid FNDA hits") from exc
            if hits < 0:
                raise CoverageError(f"line {number}: negative FNDA hits")
            symbol = parts[1]
            entries[current][symbol] = max(hits, entries[current].get(symbol, 0))
    if current is not None or not records:
        raise CoverageError("truncated or empty LCOV")
    mapped: dict[tuple[str, int, str], int] = {}
    unreadable: list[str] = []
    all_symbols = sum(len(symbols) for symbols in entries.values())
    for file, symbols in sorted(entries.items()):
        path = source_root / file
        try:
            source_lines = path.read_text(encoding="utf-8").splitlines()
        except (OSError, UnicodeError):
            unreadable.append(file)
            continue
        test_start = _test_module_start(source_lines)
        for symbol, hits in symbols.items():
            for line_no in locations[file].get(symbol, set()):
                match = _declaration_at_or_before(source_lines, line_no, symbol)
                if match is None:
                    continue
                decl_line, fn_name = match
                if test_start is not None and decl_line >= test_start:
                    continue
                key = file, decl_line, fn_name
                mapped[key] = max(mapped.get(key, 0), hits)
                break
    matched = tuple(
        NamedFunction(file, line, name, hits)
        for (file, line, name), hits in sorted(mapped.items())
    )
    # Raw symbols may have several codegen identities for one declaration.
    # This count is not a denominator for total source functions.
    return NamedFunctionReport(
        matched=matched,
        raw_symbols=all_symbols,
        excluded_or_unmatched=max(0, all_symbols - len(matched)),
        unreadable_files=tuple(unreadable),
    )


def markdown_summary(report: NamedFunctionReport, sha: str) -> str:
    result = [
        "### Rust source-mapped function evidence (partial; report only)",
        f"- Exact source SHA: {sha or 'local'}",
        f"- Matched source declarations: **{len(report.matched)}**; "
        f"executed: **{sum(f.hits > 0 for f in report.matched)}**.",
        f"- LLVM raw FNDA symbols: {report.raw_symbols}; unmatched/excluded: "
        f"{report.excluded_or_unmatched}. **Not** a total source-function denominator.",
        "- Rust branch coverage: **unmeasured**; no synthetic BRDA counters.",
        "- Inline test modules, closures, generics without reliable declaration "
        "location and macro-derived names are excluded or unmeasured.",
    ]
    if report.unreadable_files:
        result.append("- Missing/unreadable source: " + ", ".join(report.unreadable_files[:12]))
    missing = [f for f in report.matched if f.hits == 0][:25]
    if missing:
        result.extend(["", "| Source-matched unexecuted declaration |", "| --- |"])
        for item in missing:
            result.append(f"| " + chr(96) + f"{item.file}:{item.line} :: {item.name}" + chr(96) + " |")
    return "\n".join(result) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("lcov", type=Path)
    parser.add_argument("--source-root", type=Path, default=Path("."))
    parser.add_argument("--sha", default="")
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args()
    try:
        coverage = report_named_functions(
            args.lcov.read_text(encoding="utf-8"), args.source_root
        )
    except (OSError, UnicodeError, CoverageError) as error:
        print(f"FUNCTION_COVERAGE_UNMEASURED: {error}")
        return 1
    print(markdown_summary(coverage, args.sha))
    if args.json_out is not None:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(
            json.dumps(coverage.to_dict(), indent=2) + "\n", encoding="utf-8"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
