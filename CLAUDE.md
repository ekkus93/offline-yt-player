# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Rust core (`core/`, crate `offline-yt-core`) exposed to an Android Compose app (`app/`) through UniFFI.

## Commands

CI parity (docs/BUILDING.md; CI on Ubuntu 24.04 is the executable reference):

- Rust: `cargo fmt --all -- --check`, `cargo clippy --workspace --all-targets --all-features -- -D warnings`, `cargo test --workspace --all-features`
- Single Rust integration test: `cargo test -p offline-yt-core --test <file_stem> [name]` (files in `core/tests/`)
- Android: `./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug` (always the repo `./gradlew`; CI also runs `assembleDebugAndroidTest`)
- Single instrumentation class/method: `./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fqcn>[#method]`
- Python governance tests (stdlib unittest, from repo root): `python3 -m unittest tests/test_<name>.py`
- UniFFI Kotlin bindings are checked by `:app:verifyUniffiKotlinBindings`; regenerate with `cargo build -p offline-yt-core && cargo run -p uniffi-bindgen -- generate target/debug/liboffline_yt_core.so --language kotlin --out-dir target/uniffi-kotlin`. See docs/ANDROID_RUST_FFI_BUILD.md.

Setup: JDK 17, Android SDK `platforms;android-37.0` + `build-tools;37.0.0`, NDK `30.0.16248370` (Android-ABI build only), `rustup target add aarch64-linux-android`, Rust pinned to 1.98.1 by `rust-toolchain.toml`.

Required Android smoke tests run through `scripts/run_required_android_smoke.sh`: one class per fresh `connectedDebugAndroidTest` invocation, because bulk runs hit intermittent Compose SnapshotStateObserver cross-thread failures. Do not batch them. A local emulator is available for device tests.

## Style

- No Kotlin formatter is configured; Rust uses plain rustfmt. `.editorconfig`: 4-space indent for kt/kts/rs, 2-space for yml/json/toml/md/xml.
- Rust: `unsafe_code = "forbid"`; clippy warnings fail CI.
- Commits: conventional commits with a lowercase scope (`fix(android): ...`, `test(android): ...`, `ci(android): ...`, `docs: ...`). Work is committed directly on `master`.

## Gotchas

- `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md` is the authoritative checklist. Never assume an unchecked item is done, and nothing counts as qualified without exact-head CI evidence. `scripts/check_remediation_closeout.py` fails while any `- [ ]` remains.
- Every `actions/upload-artifact` step must be guarded by `github.ref_type == 'tag'`, including failure evidence. Workflows that upload artifacts also trigger on `push.tags: ['**']`. Enforced by `tests/test_actions_artifact_tag_policy.py` (docs/CI_ARTIFACT_TAG_POLICY.md).
- Reviewed screenshot hashes for `ProductionComposeGoldenTest` are pinned in the tracked Kotlin source. CI must not modify test source at runtime (`git diff --exit-code` enforces it). Golden evidence is exported via MediaStore before instrumentation teardown; the test fails if evidence is missing.
- UI is portrait-only (ADR 0005): no landscape resources, rotate/fullscreen actions, horizontal scrolling, or controls that need scrolling. Only the Library and Downloads lists may scroll.
- Provider-specific extraction stays behind the `MediaSource` adapter, never in generic UI or library APIs. Keep Android lifecycle/UI/notification/Media3 code out of the Rust core, and keep the Rust/Android boundary coarse-grained.
- Prefer deterministic fixtures and the local HTTP fixture server over live network. Live YouTube tests belong only in `youtube-live-smoke.yml`.
- The legal/policy release gate is human-only (license is `LicenseRef-OYP-Pending`).
