# Android Rust and UniFFI Build Flow

This document records the current Android/Rust integration build contract for the remediation track. It is a build and troubleshooting reference for RMD-1702; it does not by itself close the remaining end-to-end qualification tasks.

## Supported Android ABIs for v1

The current Android application build supports two v1 ABIs:

- `arm64-v8a`
- `x86_64`

The Gradle configuration maps these ABIs to the Rust Android targets:

- `arm64-v8a` -> `aarch64-linux-android`
- `x86_64` -> `x86_64-linux-android`

Relevant file:

- `app/build.gradle.kts`

The representative native library paths used by the app build are:

- `target/aarch64-linux-android/debug/liboffline_yt_core.so`
- `target/x86_64-linux-android/debug/liboffline_yt_core.so`

Additional ABIs must not be claimed as supported until the Rust target build, Gradle packaging, APK verification, and runtime smoke coverage are all extended for those ABIs.

## Rust Android library build

The Android CI lane installs the pinned Rust toolchain and the supported Android targets, then builds the portable Rust core as a `cdylib` for Android packaging.

The app Gradle build expects the Rust outputs before JNI merge tasks run. `prepareRustJniLibs` copies the native libraries into a generated JNI libs directory under the Gradle build tree:

- from: `target/aarch64-linux-android/debug/liboffline_yt_core.so`
- to: generated `rustJniLibs/arm64-v8a/`
- from: `target/x86_64-linux-android/debug/liboffline_yt_core.so`
- to: generated `rustJniLibs/x86_64/`

The Gradle merge-JNI tasks depend on this copy task, so absent Rust Android libraries fail the build instead of silently creating an APK without the core.

## UniFFI Kotlin generation

Kotlin bindings are generated from the Rust core interface during the app build. The Gradle task `generateUniffiKotlinBindings` runs from the repository root and uses the native desktop debug library as the bindgen input:

1. Build `offline-yt-core` for the host.
2. Clear the generated Kotlin output directory.
3. Run `uniffi-bindgen` to generate Kotlin bindings into the Gradle-generated source directory.

The generated source directory is added to the Android main Java/Kotlin source set so production Kotlin code can compile against the generated UniFFI package.

The verification task `verifyUniffiKotlinBindings` checks that Kotlin files were generated and that they use the expected Android package:

- `com.ekkus.offlineytplayer.core`

## Gradle integration points

The app build wires generated outputs into Android compilation and packaging:

- `sourceSets.getByName("main").jniLibs.srcDir(...)` includes generated JNI libs.
- `sourceSets.getByName("main").java.srcDir(...)` includes generated UniFFI Kotlin bindings.
- Kotlin compile tasks depend on UniFFI generation.
- JNI merge tasks depend on Rust JNI library preparation.
- `ndk.abiFilters` limits the APK to the declared v1 ABI set.

This keeps native packaging and generated Kotlin consumption deterministic rather than relying on stale checked-in outputs.

## CI qualification lanes

The CI matrix currently includes these Android/Rust integration checks:

- exact-head identity assertion;
- generated UniFFI Kotlin verification;
- supported Android Rust ABI builds for `aarch64-linux-android` and `x86_64-linux-android`;
- APK native-library packaging verification for `arm64-v8a` and `x86_64`;
- Android lint/unit/build qualification;
- Rust fmt, clippy, and tests.

The APK packaging gate must prove that the built APK contains the expected native library paths for all supported ABIs. A build that compiles the Rust core but does not package it into the APK is not qualified.

## Common native-loading failures

### Missing `liboffline_yt_core.so`

Likely causes:

- A supported Rust Android target was not installed.
- The core was not built for `aarch64-linux-android` or `x86_64-linux-android`.
- `prepareRustJniLibs` ran before the native libraries existed.
- The expected library path changed without updating Gradle.

Expected behavior:

- CI/build should fail before release qualification.
- Do not replace this with a runtime fallback that pretends the core is unavailable but still marks download capabilities complete.

### ABI mismatch

Likely causes:

- The APK was installed on an unsupported ABI.
- `abiFilters` and generated JNI output directories disagree.
- A new ABI was added to Gradle but not to the Rust target build or packaging verification.

Expected behavior:

- The supported ABI set must be updated in one place and verified by CI before being documented as supported.

### Stale or missing UniFFI Kotlin bindings

Likely causes:

- Rust interface changed but bindings were not regenerated.
- Bindgen output directory was reused without clearing.
- Kotlin compilation used stale generated sources from an earlier build.

Expected behavior:

- The generation task clears output before regeneration.
- Kotlin compile depends on generation.
- CI verifies generated binding presence and package identity.

### Main-thread blocking through FFI

Native calls that may block on I/O or long-running work must not run on Android's main thread. The app-owned gateway layer owns thread/cancellation/lifecycle policy for production repositories and UI state. RMD-203 records the app-owned gateway contract; RMD-604 still owns broader lifecycle-aware state-holder/process-restoration architecture.

## Emulator and device setup notes

The current checked-in instrumentation tests include runtime smoke contracts for native loading and network capability, and the Android smoke workflow runs on an API 29 x86_64 emulator. Final release qualification still requires the broader deterministic Android E2E matrix tracked under RMD-1500 and RMD-1803.

Minimum setup expectations for local Android runtime validation:

- Android SDK installed with the compile SDK used by Gradle.
- Android NDK version matching CI expectations.
- Rust toolchain pinned to the repository CI configuration.
- Rust targets `aarch64-linux-android` and `x86_64-linux-android` installed when validating the full supported ABI set.
- An `arm64-v8a` device or `x86_64` emulator matching one of the supported ABIs.

## Remaining remediation ownership

This document supports RMD-1702. It does not close:

- RMD-1500 deterministic end-to-end fixture qualification;
- RMD-1601's deterministic E2E fixture lane;
- RMD-1803 final exact-head full qualification.
