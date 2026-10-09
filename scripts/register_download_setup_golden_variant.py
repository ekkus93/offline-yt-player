#!/usr/bin/env python3
"""Read-only, exact-head Compose golden preflight for the Android smoke lane.

The CI test source must not be modified at runtime. Reviewed screenshot hashes
and the Compose idle barrier are pinned in the tracked Kotlin test; this script
only checks their presence and disables host emulator animations.
"""

from __future__ import annotations

from pathlib import Path
import re
import subprocess

TARGET = Path("app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeGoldenTest.kt")
REVIEWED_HASHES = (
    "9cfd346c4254a4510e5a363212d9e8d0992813e45c2c749007c6cd62b0facd72",
    "577c0cb6e2d2d0ca32722243d648184a44a75b2b6ae14d9fb14077656fee00d0",
)
DOWNLOAD_SETUP_BLOCK = re.compile(r'"download_setup"\s+to\s+setOf\((?P<values>[^)]*)\)')
DOWNLOAD_SETUP_READY = '        compose.onNodeWithText("Quality choices").assertIsDisplayed()\n'
DOWNLOAD_SETUP_SETTLED = '        compose.waitForIdle()\n'


def verify_pinned_golden_source(content: str) -> None:
    match = DOWNLOAD_SETUP_BLOCK.search(content)
    if match is None:
        raise ValueError("download_setup golden hash block not found in tracked source")
    registered = set(re.findall(r'"([0-9a-f]{64})"', match.group("values")))
    missing = set(REVIEWED_HASHES) - registered
    if missing:
        raise ValueError("reviewed download_setup hashes must be checked in: " + ", ".join(sorted(missing)))
    if DOWNLOAD_SETUP_READY + DOWNLOAD_SETUP_SETTLED not in content:
        raise ValueError("download_setup Compose idle barrier must be checked in")


def disable_platform_animations() -> None:
    for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        subprocess.run(
            ["adb", "shell", "settings", "put", "global", setting, "0"],
            check=True,
        )


def main() -> int:
    verify_pinned_golden_source(TARGET.read_text(encoding="utf-8"))
    disable_platform_animations()
    print("Verified pinned download_setup hashes and idle barrier; source remains unchanged")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
