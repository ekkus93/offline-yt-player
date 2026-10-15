#!/usr/bin/env python3
"""Verify expected Android golden PNGs and raster SHA sidecars.

The raster SHA-256 is over bitmap dimensions and pixels, not PNG file bytes.
This validator checks export completeness; Android instrumentation checks hashes.
"""
import argparse
import re
import sys
from pathlib import Path

NAMES = (
    "library_empty", "library_populated", "add_invalid", "add_resolved",
    "download_setup", "downloads_active", "downloads_failure", "player",
    "settings_hub", "smallest_portrait", "large_font_library", "large_font_settings",
)
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def read_png_signature(path: Path) -> bool:
    with path.open("rb") as stream:
        return stream.read(len(PNG_SIGNATURE)) == PNG_SIGNATURE


def verify(directory: Path) -> int:
    failures = []
    manifest = []
    for name in NAMES:
        png = directory / f"{name}.png"
        sidecar = directory / f"{name}.sha256"
        if not png.is_file() or not read_png_signature(png):
            failures.append(f"{name}: missing or invalid PNG")
        if not sidecar.is_file():
            failures.append(f"{name}: missing raster SHA-256 sidecar")
            continue
        digest_line = sidecar.read_text(encoding="utf-8").strip()
        if not re.fullmatch(rf"{re.escape(name)}=[0-9a-f]{{64}}", digest_line):
            failures.append(f"{name}: malformed raster SHA-256 sidecar")
        else:
            manifest.append(digest_line)
    if failures:
        for failure in failures:
            print(f"ANDROID_GOLDEN_EVIDENCE_MISSING: {failure}", file=sys.stderr)
        return 1
    (directory / "golden-raster-sha256.txt").write_text(
        "\n".join(manifest) + "\n", encoding="utf-8"
    )
    print(f"ANDROID_GOLDEN_EVIDENCE_OK: {len(NAMES)} PNG/raster-digest pairs")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    sys.exit(verify(parser.parse_args().directory))
