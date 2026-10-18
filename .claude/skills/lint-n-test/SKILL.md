---
name: lint-n-test
description: Lint the files and run all tests (Rust, Android unit tests, Python governance tests), then summarize failures.
model: haiku
disable-model-invocation: true
---

Lint the code and run all tests from the repo root. Run each step, continue past failures, and report every failure at the end.

1. Rust lint: `cargo fmt --all -- --check`, then `cargo clippy --workspace --all-targets --all-features -- -D warnings`
2. Rust tests: `cargo test --workspace --all-features`
3. Android lint and unit tests: `./gradlew --no-daemon lintDebug testDebugUnitTest` (always the repo `./gradlew`)
4. Python governance tests: for each `tests/test_*.py`, run `python3 -m unittest tests/test_<name>.py`

Do not run instrumentation or smoke tests (`connectedDebugAndroidTest`); they need an emulator and run one class per invocation via `scripts/run_required_android_smoke.sh`.

Finish with a short summary: one line per step marked pass or fail, and for each failure the failing command and the key error lines. Do not fix anything unless asked.
