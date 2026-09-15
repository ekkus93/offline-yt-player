# Offline YT Player

Offline YT Player is an Android-first, portrait-only offline media application. It is designed around a narrow workflow: add or share a supported video URL, resolve downloadable formats, download with durable pause/resume/retry behavior, keep the completed item in a local library, and play it without a network connection.

> **Development status:** v1 is under active engineering. This repository is not yet a public-release build. Source-service policy/legal review remains a release gate, and provider support is intentionally isolated behind replaceable adapters.

## Architecture

The project deliberately separates portable media/download logic from Android presentation and lifecycle code.

- `core/` — Rust workspace crate containing domain models, source abstraction/registry, download state and retry policy, HTTP transfer logic, persistence, local-library promotion, fixture sources, and deterministic integration tests.
- `app/` — Android application using Kotlin, Jetpack Compose, Material 3, and AndroidX Media3. Android owns the portrait UI, app lifecycle, MediaSession, local playback, share intents, notifications/services, and platform integration.
- `tools/uniffi-bindgen/` — pinned UniFFI binding-generation utility used by CI.
- `docs/` — product specification, implementation TODO, architecture/policy notes, and design mockups.

The Rust/Android boundary is intentionally coarse grained. Provider-specific extraction types must not leak into generic UI or library APIs. Completed playback uses local assets; the app must not require a network request to play a completed library item.

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

Android SDK/NDK environment variables depend on the local SDK installation. CI's `.github/workflows/ci.yml` is the executable reference for the exact Linux setup.

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

CI also cross-builds `offline-yt-core` for `aarch64-linux-android`. See `.github/workflows/ci.yml` for the pinned linker/NDK environment used for that check.

## Local development

The app is intentionally **portrait only**. Do not add landscape resources or a rotate/fullscreen-landscape action. Primary controls must remain discoverable without horizontal scrolling or scrolling merely to reach an action; naturally unbounded collections such as the Library and Downloads list may scroll within bounded regions.

For core changes, prefer deterministic fixture sources and the local HTTP fixture server instead of live provider/network dependencies. Exact-head CI is required before a milestone is considered qualified.

## Current limitations

- v1 engineering is still in progress; `docs/OFFLINE_YT_PLAYER_TODO.md` is the closeout checklist.
- Public/app-store distribution is blocked pending the documented source-service terms/policy/legal review.
- Provider extraction remains behind the `MediaSource` abstraction and must be treated as replaceable; live external-service behavior is not a deterministic CI dependency.
- The license decision is still represented as `LicenseRef-OYP-Pending`; do not treat the repository as carrying a finalized distribution license until that decision is made.
- Android is the only required v1 UI. iOS/desktop UI is out of scope, although the Rust core is intended to remain portable.
- CI exercises deterministic fixture flows and JVM/unit qualification; device/emulator-only acceptance cases still require explicit qualification before v1 closeout.

## Documentation

Start with:

- `docs/OFFLINE_YT_PLAYER_SPEC.md` — product and engineering requirements.
- `docs/OFFLINE_YT_PLAYER_TODO.md` — stable task IDs and acceptance gates.
- `.github/workflows/ci.yml` — executable CI/toolchain reference.

The design uses the **Midnight Transit** semantic palette and fixed portrait functional regions described in the specification.
