#!/usr/bin/env python3
"""Summarize production Android/Kotlin JVM coverage from Kover's JaCoCo XML."""
from __future__ import annotations

import argparse
from collections import defaultdict
from dataclasses import dataclass
import json
from pathlib import Path
import sys
from xml.etree import ElementTree


BASE_PACKAGE = "com/ekkus/offlineytplayer"


class CoverageError(ValueError):
    """A coverage report cannot support the claimed denominator."""


@dataclass(frozen=True)
class KotlinCoverage:
    line_covered: int
    line_total: int
    branch_covered: int | None
    branch_total: int | None
    packages: tuple[tuple[str, int, int], ...]

    def as_dict(self) -> dict:
        return {
            "language": "kotlin-jvm",
            "scope": "com.ekkus.offlineytplayer (excluding generated UniFFI)",
            "line_covered": self.line_covered,
            "line_total": self.line_total,
            "line_percentage": round(self.line_covered * 100 / self.line_total, 2),
            "branch_covered": self.branch_covered,
            "branch_total": self.branch_total,
            "branch_percentage": (
                round(self.branch_covered * 100 / self.branch_total, 2)
                if self.branch_total else None
            ),
            "packages": [
                {"package": name, "covered": covered, "total": total}
                for name, covered, total in self.packages
            ],
        }


def production_package(name: str) -> bool:
    if name != BASE_PACKAGE and not name.startswith(BASE_PACKAGE + "/"):
        return False
    # Generated UniFFI records/JNA glue are under the 'core' package.
    return name != BASE_PACKAGE + "/core" and not name.startswith(
        BASE_PACKAGE + "/core/"
    )


def production_sourcefile(name: str) -> bool:
    return (
        name.endswith((".kt", ".java"))
        and not name.endswith((
            "Test.kt", "Tests.kt", "Test.java", "Tests.java",
            "BuildConfig.java", "R.java"
        ))
        and not name.startswith("R$")
    )


def parse_kover_xml(report: str) -> KotlinCoverage:
    try:
        root = ElementTree.fromstring(report)
    except ElementTree.ParseError as error:
        raise CoverageError(f"malformed XML: {error}") from error
    if root.tag != "report":
        raise CoverageError("Kover/JaCoCo XML must start with <report>")
    source_lines: dict[tuple[str, str, int], tuple[int, int]] = {}
    source_branches: dict[tuple[str, str, int], tuple[int, int]] = {}
    for package in root.findall("package"):
        name = package.get("name", "")
        if not production_package(name):
            continue
        for file in package.findall("sourcefile"):
            filename = file.get("name", "")
            if not production_sourcefile(filename):
                continue
            for line in file.findall("line"):
                try:
                    number = int(line.attrib["nr"])
                    missed = int(line.attrib["mi"])
                    covered = int(line.attrib["ci"])
                except (KeyError, ValueError) as error:
                    raise CoverageError(
                        f"invalid line metrics in {name}/{filename}"
                    ) from error
                if number <= 0 or missed < 0 or covered < 0:
                    raise CoverageError(
                        f"negative/invalid line metrics in {name}/{filename}"
                    )
                if covered + missed == 0:
                    continue
                key = (name, filename, number)
                former = source_lines.get(key, (0, 0))
                source_lines[key] = (max(former[0], missed), max(former[1], covered))
                if "mb" in line.attrib or "cb" in line.attrib:
                    try:
                        bm = int(line.attrib["mb"])
                        bc = int(line.attrib["cb"])
                    except (KeyError, ValueError) as error:
                        raise CoverageError(
                            f"invalid branch metrics in {name}/{filename}"
                        ) from error
                    if bm < 0 or bc < 0:
                        raise CoverageError("negative branch metric")
                    prior = source_branches.get(key, (0, 0))
                    source_branches[key] = (max(prior[0], bm), max(prior[1], bc))
    if not source_lines:
        raise CoverageError("no instrumented production Kotlin/JVM lines found")
    packages: dict[str, list[int]] = defaultdict(lambda: [0, 0])
    for (name, _, _), (missed, covered) in source_lines.items():
        packages[name][1] += 1
        packages[name][0] += int(covered > 0)
    measured = sorted(
        ((name.replace("/", "."), covered, total)
         for name, (covered, total) in packages.items()),
        key=lambda entry: (-(entry[2] - entry[1]), entry[0]),
    )
    btotal = sum(sum(value) for value in source_branches.values())
    bcovered = sum(value[1] for value in source_branches.values())
    return KotlinCoverage(
        line_covered=sum(value[0] for value in packages.values()),
        line_total=sum(value[1] for value in packages.values()),
        branch_covered=bcovered if btotal else None,
        branch_total=btotal if btotal else None,
        packages=tuple(measured),
    )


def summary_markdown(value: KotlinCoverage, sha: str, version: str) -> str:
    pct = 100 * value.line_covered / value.line_total
    branch = (
        f"{value.branch_covered}/{value.branch_total} "
        f"({100 * value.branch_covered / value.branch_total:.2f}%)"
        if value.branch_total else "unmeasured"
    )
    lines = [
        "### Android JVM business-logic coverage (Kover; report only)",
        f"- Exact source SHA: {sha or 'local / not in GitHub Actions'}",
        f"- Kover version: {version}",
        "- Scope: production `com.ekkus.offlineytplayer` Kotlin/Java source files "
        "excluding generated `core` UniFFI/JNA bindings, tests and generated Android classes.",
        f"- Line coverage: **{value.line_covered}/{value.line_total} ({pct:.2f}%)**.",
        f"- Branch coverage: **{branch}**.",
        "- Android instrumentation execution is **not included** in these JVM numbers.",
        "- Source files not present in the report remain **unmeasured**; no threshold enforced.",
        "",
        "| Production package | Covered / measured lines |",
        "| --- | ---: |",
    ]
    lines.extend(f"| `{n}` | {c}/{t} |" for n, c, t in value.packages[:14])
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml", type=Path)
    parser.add_argument("--sha", default="")
    parser.add_argument("--plugin-version", default="0.9.11")
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args()
    try:
        value = parse_kover_xml(args.xml.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, CoverageError) as error:
        print(f"COVERAGE_UNMEASURED: {error}", file=sys.stderr)
        return 1
    print(summary_markdown(value, args.sha, args.plugin_version))
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(value.as_dict(), indent=2) + "\n",
                                 encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
