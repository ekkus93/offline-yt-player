---
name: ci-parity
description: Run the repo's CI-parity checks (Rust fmt/clippy/test, Android lint/unit tests/assemble, Python governance tests) and summarize failures. Use before declaring a change done.
---

Run from the repo root, in this order, and keep going after a failure so all results are reported:

1. `cargo fmt --all -- --check`
2. `cargo clippy --workspace --all-targets --all-features -- -D warnings`
3. `cargo test --workspace --all-features`
4. `./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug`
5. `for t in tests/test_*.py; do python3 -m unittest "$t"; done`

Skip the Gradle step if the change touched no `app/` or `core/` files that affect the Android build, and say so.

Report a table of step -> pass/fail. For each failure, give the first actionable error and the file:line. Use `scripts/summarize_android_test_failures.py` for Android test failures when XML reports exist. Do not edit golden hashes or test source to make a check pass.
