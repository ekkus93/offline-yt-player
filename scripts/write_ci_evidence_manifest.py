#!/usr/bin/env python3
"""Write a bounded CI evidence manifest for release qualification.

The manifest is intentionally secret-free: it records GitHub-provided identity
fields, tracked workflow names, and the artifact-retention policy needed by the
RMD-1603 evidence-quality gate. It does not inspect environment variables beyond
the explicit allow-list below.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "build" / "reports" / "ci-evidence" / "candidate-evidence.md"
WORKFLOW_DIR = ROOT / ".github" / "workflows"
ALLOWED_ENV_KEYS = [
    "GITHUB_SHA",
    "GITHUB_REF",
    "GITHUB_REF_NAME",
    "GITHUB_EVENT_NAME",
    "GITHUB_RUN_ID",
    "GITHUB_RUN_NUMBER",
    "GITHUB_RUN_ATTEMPT",
    "GITHUB_WORKFLOW",
    "GITHUB_JOB",
    "GITHUB_REPOSITORY",
]


def read_env() -> dict[str, str]:
    return {key: os.environ.get(key, "") for key in ALLOWED_ENV_KEYS}


def workflow_names() -> list[str]:
    if not WORKFLOW_DIR.is_dir():
        return []
    return sorted(path.name for path in WORKFLOW_DIR.glob("*.yml"))


def generate() -> str:
    env = read_env()
    workflows = workflow_names()
    lines: list[str] = [
        "# CI evidence manifest",
        "",
        "This manifest supports RMD-1603 by recording the exact candidate identity and the bounded evidence-artifact policy for the current workflow run.",
        "",
        "## Candidate identity",
        "",
        "| Field | Value |",
        "| --- | --- |",
    ]
    for key in ALLOWED_ENV_KEYS:
        lines.append(f"| `{key}` | `{env[key]}` |")

    lines.extend([
        "",
        "## Tracked workflow files",
        "",
    ])
    if workflows:
        for name in workflows:
            lines.append(f"- `{name}`")
    else:
        lines.append("- No workflow files found.")

    lines.extend([
        "",
        "## Evidence policy",
        "",
        "- Standard CI preserves Rust, Android, UniFFI, APK, and Gradle failure evidence via bounded uploaded artifacts.",
        "- Android smoke preserves instrumentation reports, test results, screenshot/golden evidence, and managed-device output with bounded retention.",
        "- Android FGS-timeout preserves timeout qualification reports and test outputs with bounded retention.",
        "- Supply chain preserves the generated Rust lockfile, Gradle dependency reports, and checked third-party notices with bounded retention.",
        "- Final RMD-1803 closeout must cite the candidate SHA and the specific run IDs that passed for that candidate.",
        "",
    ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT), help="manifest output path")
    args = parser.parse_args()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(generate(), encoding="utf-8")
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
