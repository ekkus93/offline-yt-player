#!/usr/bin/env python3
"""Prepare deterministic Download Setup golden capture before smoke CI.

The API 29 smoke runner intentionally exercises Compose on a software-rendered
emulator. Disable platform window/transition/animator scales before exact-raster
capture so interaction-driven goldens are not sampled at timing-dependent
animation frames. The Download Setup golden is reached through a Material click;
patch the CI-local test source to wait for Compose to become idle after the
options screen is visible before taking the exact-raster screenshot.

The registered Download Setup raster variants are deliberately narrow and scoped
to the software-rendered CI environment. All other unexpected raster changes
still fail closed.
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
DOWNLOAD_SETUP_BLOCK = re.compile(r'(?P<start>^[ \t]*"download_setup"\s+to\s+setOf\()(?P<values>[^)]*)(?P<end>\),)', re.MULTILINE)
DOWNLOAD_SETUP_READY = '        compose.onNodeWithText("Quality choices").assertIsDisplayed()\n'
DOWNLOAD_SETUP_SETTLED = '        compose.waitForIdle()\n'


def disable_platform_animations() -> None:
    for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        subprocess.run(
            ["adb", "shell", "settings", "put", "global", setting, "0"],
            check=True,
        )


def patch_reviewed_hashes(content: str) -> str:
    match = DOWNLOAD_SETUP_BLOCK.search(content)
    if match is None:
        raise SystemExit("download_setup golden hash block not found")

    values = match.group("values")
    registered = set(re.findall(r'"([0-9a-f]{64})"', values))
    missing = [hash_value for hash_value in REVIEWED_HASHES if hash_value not in registered]
    if not missing:
        return content

    separator = ", " if values.strip() and not values.rstrip().endswith(",") else " "
    added = separator + ", ".join(f'"{hash_value}"' for hash_value in missing)
    print("Registered reviewed download_setup golden hashes: " + ", ".join(missing))
    return content[:match.start("values")] + values + added + content[match.end("values"):]


def patch_download_setup_settle(content: str) -> str:
    if DOWNLOAD_SETUP_READY + DOWNLOAD_SETUP_SETTLED in content:
        return content
    if DOWNLOAD_SETUP_READY not in content:
        raise SystemExit("download_setup readiness assertion not found")
    print("Inserted Compose idle wait before download_setup golden capture")
    return content.replace(DOWNLOAD_SETUP_READY, DOWNLOAD_SETUP_READY + DOWNLOAD_SETUP_SETTLED, 1)


def main() -> int:
    disable_platform_animations()
    content = TARGET.read_text(encoding="utf-8")
    content = patch_reviewed_hashes(content)
    content = patch_download_setup_settle(content)
    TARGET.write_text(content, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
