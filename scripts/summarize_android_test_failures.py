#!/usr/bin/env python3
"""Print a bounded, URL-redacted Android instrumentation failure summary."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path("app/build/outputs/androidTest-results")
URL_PATTERN = re.compile(r"[A-Za-z][A-Za-z0-9+.-]*://\S+|https?://\S+")
MAX_MESSAGE_CHARS = 1200


def sanitize(value: str | None) -> str:
    if not value:
        return ""
    return URL_PATTERN.sub("[URL]", value.strip()).replace("\n", " ")[:MAX_MESSAGE_CHARS]


def main() -> int:
    printed = False
    if not ROOT.exists():
        print(f"ANDROID_TEST_FAILURE_SUMMARY missing_root root={ROOT}")
        return 0

    for path in sorted(ROOT.rglob("*.xml")):
        try:
            document = ET.parse(path)
        except Exception as exc:  # pragma: no cover - defensive CI helper
            print(f"ANDROID_TEST_XML_UNREADABLE {path}: {exc.__class__.__name__}")
            continue

        for testcase in document.findall(".//testcase"):
            failures = list(testcase.findall("failure")) + list(testcase.findall("error"))
            if not failures:
                continue
            printed = True
            classname = testcase.get("classname") or "unknown-class"
            name = testcase.get("name") or "unknown-test"
            print(f"ANDROID_TEST_FAILURE {classname}.{name}")
            for failure in failures:
                message = sanitize(failure.get("message") or failure.text)
                print(f"  {failure.tag}: {message}")

    if not printed:
        print(f"ANDROID_TEST_FAILURE_SUMMARY none_found root={ROOT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
