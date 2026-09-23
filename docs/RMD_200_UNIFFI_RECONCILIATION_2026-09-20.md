# RMD-200 UniFFI integration reconciliation

This evidence note reconciles the implementation behind RMD-201 through RMD-204 without replacing or compressing the detailed remediation TODO. The TODO remains authoritative and must only have its individual checkboxes changed when this evidence is incorporated into that file.

## RMD-201 — Package Rust native libraries into the APK

Implemented. `app/build.gradle.kts` defines the supported v1 Android ABI map (`arm64-v8a`/`aarch64-linux-android` and `x86_64`/`x86_64-linux-android`), constrains Android `abiFilters` to that set, stages `liboffline_yt_core.so` into generated JNI libs, and wires that generated JNI directory into the Android main source set. `.github/workflows/ci.yml` builds both Android Rust targets, verifies both `.so` files exist, assembles the debug APK, and fails unless the APK contains both `lib/arm64-v8a/liboffline_yt_core.so` and `lib/x86_64/liboffline_yt_core.so`. Original RMD-201 packaging evidence was present on qualified master SHA `218a8e90b315a329506b4fbf9fb8819ac9489d53` with post-merge CI run `35501449031`; current dual-ABI packaging was requalified on master SHA `119460a7f872aea520093f0fadd95cf52fd6c0b1` with CI run `35816014322` passing.

## RMD-202 — Compile generated UniFFI Kotlin bindings into the app

Implemented. `app/build.gradle.kts` generates Kotlin bindings from the built Rust core library, adds the generated directory to the Android main Java/Kotlin source set, regenerates bindings into an independent verification directory, diffs both outputs, and exposes `:app:verifyUniffiKotlinBindings` so stale or divergent bindings fail CI. Implementation was originally qualified at exact head `d7e72741ea18a93638b6cd36867a546ef41192ad` with post-merge CI run `35496767918`; current master SHA `119460a7f872aea520093f0fadd95cf52fd6c0b1` requalified the binding consistency gate in CI run `35816014322`.

## RMD-203 — Create an app-owned core gateway

Implemented. App-owned gateway interfaces and generated implementations under `app/src/main/java/com/ekkus/offlineytplayer/coregateway/` wrap generated UniFFI services, centralize model/error conversion, reject blocking main-thread calls, expose asynchronous repository/control operations with closeable lifecycle semantics, and provide deterministic fake gateways for Android UI tests. `MainActivity.kt` owns and closes production gateway instances. Exact implementation head `2acd4e9d97825d18accf06fe98122dd632f06fe5` passed push CI `35497888818` and PR CI `35497891688`; current master SHA `119460a7f872aea520093f0fadd95cf52fd6c0b1` requalified the Android/JVM/build path in CI run `35816014322`.

## RMD-204 — Android runtime FFI smoke test

Implemented and now executed in the Android smoke lane. `app/src/androidTest/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGatewaySmokeTest.kt` explicitly loads `offline_yt_core`, opens generated UniFFI-backed core and download-control gateways against an app-cache-private temporary SQLite database, lists library/download repository state, verifies structured invalid-input error behavior, enqueues and pauses durable queue state, reads it back through the core gateway, checks missing-item/delete behavior, and cleans up the temporary root. PR #320 added this test to `.github/workflows/android-smoke.yml` beside `AndroidRuntimeSmokeTest`, preserving the small acceleration tier while proving packaged Rust/UniFFI runtime loading and representative calls on an emulator. Exact-head PR #320 evidence: head `700e06556cb2eacbb76b6e7c4c078020f1fbe237` passed CI run `35815386946` and Android-smoke run `35815386970`; post-merge master SHA `119460a7f872aea520093f0fadd95cf52fd6c0b1` passed CI run `35816014322` and Android-smoke run `35816014360`.

## Canonical TODO status

This note supports canonical TODO reconciliation for RMD-201 through RMD-204. The detailed TODO remains the source of completion truth, so RMD-200 checkboxes must still be updated in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md` with this evidence before RMD-200 is considered reconciled.
