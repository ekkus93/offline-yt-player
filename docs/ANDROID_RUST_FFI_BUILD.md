# Android Rust and UniFFI Build Flow

This document records the current Android/Rust integration build contract for the remediation track. It is a build and troubleshooting reference for RMD-1702; it does not by itself close the remaining runtime integration or end-to-end qualification tasks.

## Supported Android ABI for v1

The current Android application build supports one v1 ABI:

- `arm64-v8a`

The Gradle configuration maps this ABI to the Rust Android target:

- `aarch64-linux-android`

Relevant file:

- `app/build.gradle.kts`

The representative native library path used by the app build is:

- `target/aarch64-linux-android/debug/liboffline_yt_core.so`

Additional ABIs must not be claimed as supported until the Rust target build, Gradle packaging, APK verification, and runtime smoke coverage are all extended for those ABIs.

## Rust Android library build

The Android CI lane installs the pinned Rust toolchain and the `aarch64-linux-android` target, then builds the portable Rust core as a `cdylib` for Android packaging.

The app Gradle build expects the Rust output before JNI merge tasks run. `prepareRustJniLibs` copies the native library into a generated JNI libs directory under the Gradle build tree:

- from: `target/aarch64-linux-android/debug/liboffline_yt_core.so`
- to: generated `rustJniLibs/arm64-v8a/`

The Gradle merge-JNI tasks depend on this copy task, so an absent Rust Android library fails the build instead of silently creating an APK without the core.

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
- `ndk.abiFilters` limits the APK to the declared v1 ABI.

This keeps native packaging and generated Kotlin consumption deterministic rather than relying on stale checked-in outputs.

## CI qualification lanes

The CI matrix currently includes these Android/Rust integration checks:

- exact-head identity assertion;
- generated UniFFI Kotlin verification;
- representative Android ABI build;
- APK native-library packaging verification;
- Android lint/unit/build qualification;
- Rust fmt, clippy, and tests.

The APK packaging gate must prove that the built APK contains the expected native library path for the supported ABI. A build that compiles the Rust core but does not package it into the APK is not qualified.

## Common native-loading failures

### Missing `liboffline_yt_core.so`

Likely causes:

- Rust Android target was not installed.
- The core was not built for `aarch64-linux-android`.
- `prepareRustJniLibs` ran before the native library existed.
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

Native calls that may block on I/O or long-running work must not run on Android's main thread. The app-owned gateway layer owns thread/cancellation/lifecycle policy for production ViewModels and repositories. RMD-203/RMD-604 remain responsible for proving those runtime semantics beyond build-time binding availability.

## Emulator and device setup notes

The current checked-in instrumentation tests include runtime smoke contracts for native loading and network capability, but final release qualification still requires the broader Android instrumentation/E2E matrix tracked under RMD-1400 and RMD-1500.

Minimum setup expectations for local Android runtime validation:

- Android SDK installed with the compile SDK used by Gradle.
- Android NDK version matching CI expectations.
- Rust toolchain pinned to the repository CI configuration.
- Rust target `aarch64-linux-android` installed.
- An arm64 device/emulator matching the supported ABI.

## Remaining remediation ownership

This document supports RMD-1702. It does not close:

- RMD-201/RMD-202 package/binding implementation if future ABI or generation requirements change;
- RMD-203 app-owned core gateway runtime semantics;
- RMD-204 full Android runtime FFI smoke qualification;
- RMD-1400 Android instrumentation infrastructure;
- RMD-1500 end-to-end fixture qualification.
