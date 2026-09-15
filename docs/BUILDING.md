# Building Offline YT Player

## Pinned toolchains

- Rust: 1.98.1 (`rust-toolchain.toml`)
- JDK: 17
- Android Gradle Plugin: 9.4.0
- Gradle: 9.6.0
- Kotlin / Compose compiler plugin: 2.4.20
- Compose BOM: 2026.08.00
- compileSdk: 37 (SDK package `platforms;android-37.0`)
- targetSdk: 36
- minSdk: 26
- Android build tools: 37.0.0

The checked-in `gradlew`/`gradlew.bat` bootstrap scripts download the exact Gradle distribution named by `gradle/wrapper/gradle-wrapper.properties` and verify its SHA-256 before execution. This repository intentionally keeps the bootstrap path text-only; no build depends on a machine-global Gradle install.

The app compiles against API 37 because the current stable Compose dependency line requires it, while v1 deliberately targets API 36. The command-line SDK publishes the compile platform under the minor-versioned package name `platforms;android-37.0`; CI pins that exact package.

## Clean checkout bootstrap

Linux/macOS:

```bash
rustup show
cargo test --workspace
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Windows PowerShell / cmd:

```text
gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

Android builds require SDK package `platforms;android-37.0` and build-tools 37.0.0. CI installs those exact packages before invoking Gradle.

## Repository boundaries

- `core/` — portable Rust domain, source, download, persistence, and FFI logic.
- `app/` — Android Kotlin/Compose presentation and platform integration.
- `docs/` — product, architecture, UX, legal/policy, and operational documentation.
- `app/src/test/` — JVM-side Android unit tests.
- `app/src/androidTest/` — device/instrumentation tests when introduced.
- `core/**/tests` and Rust `#[cfg(test)]` modules — portable core qualification.

## CI parity

Before opening or merging a change, run:

```bash
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --all-features -- -D warnings
cargo test --workspace --all-features
./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug
```

CI prints and verifies `GITHUB_SHA` against the checked-out commit so qualification evidence can be tied to an exact head.
