# Offline YT Player

Offline YT Player is an Android-first, portrait-only offline media application. It is designed around a narrow workflow: add or share a supported video URL, resolve downloadable formats, download with durable pause/resume/retry behavior, keep the completed item in a local library, and play it without a network connection.

> **Development status:** v1 is under active remediation and is not yet a public-release build. Individual runtime paths and tests are implemented, but the complete end-to-end workflow is not engineering-closeout qualified yet. Source-service policy/legal review remains a separate human release gate, and provider support is intentionally isolated behind replaceable adapters.

## Architecture

The project deliberately separates portable media/download logic from Android presentation and lifecycle code.

- `core/` — Rust workspace crate containing domain models, source abstraction/registry, download state and retry policy, HTTP transfer logic, persistence, local-library promotion, fixture sources, and deterministic integration tests.
- `app/` — Android application using Kotlin, Jetpack Compose, Material 3, and AndroidX Media3. Android owns the portrait UI, app lifecycle, MediaSession, local playback, share intents, notifications/services, and platform integration.
- `tools/uniffi-bindgen/` — pinned UniFFI binding-generation utility used by CI.
- `docs/` — product specification, remediation checklist, architecture/policy notes, operational documentation, and design mockups.

The Rust/Android boundary is intentionally coarse grained. Provider-specific extraction types must not leak into generic UI or library APIs. Completed playback is designed to use local assets; final end-to-end offline playback qualification remains part of the remediation checklist.

## Supported development platform

CI currently qualifies the repository on Ubuntu 24.04. Local development on another host may work, but the pinned toolchain and Android SDK requirements below are authoritative.

Required tooling:

- Git
- Rust **1.98.1** with `rustfmt` and `clippy`
- JDK **17**
- Android SDK command-line tools
- Android platform **37.0** and build-tools **37.0.0**
- Android NDK **30.0.16248370** for the representative `aarch64-linux-android` Rust build
- Gradle wrapper from this repository (do not substitute a system Gradle)

The Android application currently declares `minSdk 26`, `targetSdk 36`, and `compileSdk 37`.

## Bootstrap

From a clean checkout:

```bash
git clone https://github.com/ekkus93/offline-yt-player.git
cd offline-yt-player

rustup toolchain install 1.98.1 --profile minimal --component rustfmt --component clippy
rustup override set 1.98.1

./gradlew --version
```

For UniFFI/Android ABI qualification, also install the Android Rust target and the pinned NDK:

```bash
rustup target add aarch64-linux-android
sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "ndk;30.0.16248370"
```

Android SDK/NDK environment variables depend on the local SDK installation. CI's `.github/workflows/ci.yml` is the executable reference for the exact Linux setup. `docs/ANDROID_RUST_FFI_BUILD.md` documents the Android ABI build, UniFFI generation/Gradle integration, emulator/device setup, and common native-loading failures.

## Build and test

Run the portable Rust checks:

```bash
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --all-features -- -D warnings
cargo test --workspace --all-features
```

Run Android lint, unit tests, and a debug build:

```bash
./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug
```

Generate representative Kotlin UniFFI bindings:

```bash
cargo build -p offline-yt-core
rm -rf target/uniffi-kotlin
cargo run -p uniffi-bindgen -- \
  generate target/debug/liboffline_yt_core.so \
  --language kotlin \
  --out-dir target/uniffi-kotlin
```

CI also cross-builds `offline-yt-core` for `aarch64-linux-android`, verifies generated Kotlin bindings, and verifies that the representative APK packages the Rust core. See `.github/workflows/ci.yml` and `docs/ANDROID_RUST_FFI_BUILD.md` for the pinned setup and integration details.

## Supported and unsupported workflows

The repository currently contains production and test wiring for the Android/Rust gateway, durable download state, Android background-execution scheduling, notifications, local-library/playback components, and deterministic fixture-based qualification. These pieces are being reconciled under the remediation checklist and must not be interpreted individually as proof that the full v1 workflow is complete.

The following remain unsupported as release claims until their corresponding remediation acceptance criteria are checked with exact-head evidence:

- a fully qualified real-URL Add/Share → resolve → download → library → offline-playback path;
- complete durable pause/resume/cancel/retry and process-death/reboot recovery across supported Android API levels;
- complete device/emulator instrumentation, accessibility/layout, screenshot/golden, and deterministic fixture E2E qualification;
- public/app-store distribution or any claim that source-service policy/legal approval has been granted.

For Android background execution, `docs/ANDROID_BACKGROUND_EXECUTION.md` describes the API 34+ user-initiated data-transfer path, API 26-33 fallback, notification behavior, reboot/process-death constraints, Android 15+ restrictions, and the remaining remediation ownership.

## Local development

The app is intentionally **portrait only**. Do not add landscape resources or a rotate/fullscreen-landscape action. Primary controls must remain discoverable without horizontal scrolling or scrolling merely to reach an action; naturally unbounded collections such as the Library and Downloads list may scroll within bounded regions.

For core changes, prefer deterministic fixture sources and the local HTTP fixture server instead of live provider/network dependencies. Exact-head CI is required before a milestone is considered qualified.

## Current limitations and release gates

- The authoritative engineering tracker is `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; unchecked items are intentionally unresolved and must not be inferred complete from implementation or policy constants alone.
- Public/app-store distribution remains blocked pending the documented source-service terms/policy/legal review. That approval is external to engineering and must be made by an appropriate human authority.
- Provider extraction remains behind the `MediaSource` abstraction and must be treated as replaceable; live external-service behavior is not a deterministic CI dependency.
- The license decision is still represented as `LicenseRef-OYP-Pending`; do not treat the repository as carrying a finalized distribution license until that decision is made.
- Android is the only required v1 UI. iOS/desktop UI is out of scope, although the Rust core is intended to remain portable.
- Device/emulator-only acceptance cases remain explicit remediation gates until exact-head evidence is recorded.

## Documentation

Start with:

- `docs/OFFLINE_YT_PLAYER_REMEDIATION_SPEC_2026-09-17.md` — normative remediation requirements.
- `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md` — authoritative detailed remediation checklist and acceptance gates.
- `docs/OFFLINE_YT_PLAYER_SPEC.md` — product and engineering requirements.
- `docs/ANDROID_RUST_FFI_BUILD.md` — Android/Rust build and UniFFI integration.
- `docs/ANDROID_BACKGROUND_EXECUTION.md` — Android background execution model and remaining limits.
- `.github/workflows/ci.yml` — executable CI/toolchain reference.

The design uses the **Midnight Transit** semantic palette and fixed portrait functional regions described in the specification.
