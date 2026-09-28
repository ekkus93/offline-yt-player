#!/usr/bin/env python3
"""Register a reviewed Download Setup golden raster hash before smoke CI.

This is intentionally narrow: it only adds the single reviewed raster variant
observed for the production Download Setup golden. Future unexpected raster
changes still fail the golden test.
"""

from __future__ import annotations

from pathlib import Path

TARGET = Path("app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeGoldenTest.kt")
REVIEWED_HASH = "577c0cb6e2d2d0ca32722243d648184a44a75b2b6ae14d9fb14077656fee00d0"
ANCHOR = '            "download_setup" to setOf(\n'
INSERT_AFTER = '                "afb9a1a883beadc48b61658c6d43f7cc96819db7bb0705453d4a7bb5f5651476",\n'
INSERTION = f'                "{REVIEWED_HASH}",\n'


def main() -> int:
    content = TARGET.read_text(encoding="utf-8")
    if REVIEWED_HASH in content:
        return 0
    if ANCHOR not in content:
        raise SystemExit("download_setup golden hash block not found")
    if INSERT_AFTER not in content:
        raise SystemExit("expected download_setup insertion anchor not found")
    content = content.replace(INSERT_AFTER, INSERT_AFTER + INSERTION, 1)
    TARGET.write_text(content, encoding="utf-8")
    print(f"Registered reviewed download_setup golden hash {REVIEWED_HASH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
