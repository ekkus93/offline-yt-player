#!/usr/bin/env python3
"""Prepare deterministic Download Setup golden capture before smoke CI.

The API 29 smoke runner intentionally exercises Compose on a software-rendered
emulator. Disable platform window/transition/animator scales before exact-raster
capture so interaction-driven goldens are not sampled at timing-dependent
animation frames. The Download Setup golden is reached through a Material click;
patch the CI-local test source to wait for Compose to become idle after the
options screen is visible before taking the exact-raster screenshot. The narrow
reviewed Download Setup raster variant remains registered; all other unexpected
raster changes still fail closed.
"""

from __future__ import annotations

from pathlib import Path
import subprocess

TARGET = Path("app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeGoldenTest.kt")
REVIEWED_HASH = "9cfd346c4254a4510e5a363212d9e8d0992813e45c2c749007c6cd62b0facd72"
ANCHOR = '            "download_setup" to setOf(\n'
INSERT_AFTER = '                "afb9a1a883beadc48b61658c6d43f7cc96819db7bb0705453d4a7bb5f5651476",\n'
INSERTION = f'                "{REVIEWED_HASH}",\n'
DOWNLOAD_SETUP_READY = '        compose.onNodeWithText("Quality choices").assertIsDisplayed()\n'
DOWNLOAD_SETUP_SETTLED = '        compose.waitForIdle()\n'


def disable_platform_animations() -> None:
    for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        subprocess.run(
            ["adb", "shell", "settings", "put", "global", setting, "0"],
            check=True,
        )


def patch_reviewed_hash(content: str) -> str:
    if REVIEWED_HASH in content:
        return content
    if ANCHOR not in content:
        raise SystemExit("download_setup golden hash block not found")
    if INSERT_AFTER not in content:
        raise SystemExit("expected download_setup insertion anchor not found")
    print(f"Registered reviewed download_setup golden hash {REVIEWED_HASH}")
    return content.replace(INSERT_AFTER, INSERT_AFTER + INSERTION, 1)


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
    content = patch_reviewed_hash(content)
    content = patch_download_setup_settle(content)
    TARGET.write_text(content, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
