---
name: android-smoke
description: Run the required Android instrumentation smoke tests on the local emulator, one class per Gradle invocation, and report which passed, failed or were skipped.
disable-model-invocation: true
---

1. Confirm a device/emulator is attached with `adb devices`. If none, stop and tell the user.
2. If the golden tests are in scope, run `python3 scripts/register_download_setup_golden_variant.py` first (read-only preflight; also disables emulator animations).
3. Run `scripts/run_required_android_smoke.sh`. It runs each mandatory class in a fresh `connectedDebugAndroidTest` invocation, so never replace it with a single bulk run. It deletes stale XML reports and requires a non-skipped test per class.
4. Afterwards run `python3 scripts/assert_android_smoke_execution.py` and, on failures, `python3 scripts/summarize_android_test_failures.py`. Check each script's usage first if arguments are unclear.
5. Report per class: passed / failed / skipped. A skipped class counts as a failure.

Never edit pinned golden hashes in `ProductionComposeGoldenTest.kt` to make a run pass. If evidence export fails, see `docs/RALPH_BRIDGE_WRITE_DIAGNOSTIC_2026-10-15.md`. $ARGUMENTS
