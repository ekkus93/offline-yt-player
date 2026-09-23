# RMD-200 UniFFI Android integration reconciliation

This evidence note records current-master implementation and qualification evidence for RMD-200 without replacing or compressing the canonical checklist in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Scope

RMD-200 requires real Rust/Android UniFFI integration: Android must package the Rust native libraries, compile generated UniFFI Kotlin bindings into the app, use an app-owned core gateway, and prove at least one representative runtime FFI path from an installed Android app/test APK.

This note is intentionally evidence-only. The canonical remediation TODO must still be reconciled separately after this evidence PR itself qualifies and merges.

## RMD-201 native-library packaging evidence

- `app/build.gradle.kts` defines the v1 Android ABI set as `arm64-v8a -> aarch64-linux-android` and `x86_64 -> x86_64-linux-android`.
- `prepareRustJniLibs` copies `target/aarch64-linux-android/debug/liboffline_yt_core.so` and `target/x86_64-linux-android/debug/liboffline_yt_core.so` into generated JNI libs under the matching APK ABI directories.
- The Gradle task fails before packaging if either expected native library is missing.
- `android.defaultConfig.ndk.abiFilters` is bound to the same supported ABI set.
- `.github/workflows/ci.yml` builds both Rust Android targets in the `UniFFI Kotlin and Android ABI` job and verifies the APK contains `lib/arm64-v8a/liboffline_yt_core.so` and `lib/x86_64/liboffline_yt_core.so`.

## RMD-202 generated UniFFI Kotlin evidence

- `app/build.gradle.kts` defines `generateUniffiKotlinBindings`, which rebuilds `offline-yt-core` and regenerates Kotlin bindings through `cargo run -p uniffi-bindgen -- generate target/debug/liboffline_yt_core.so --language kotlin`.
- The Android main source set includes the generated UniFFI Kotlin directory via `sourceSets.getByName("main").java.srcDir(generatedUniffiKotlinDir)`.
- `verifyReproducibleUniffiKotlinBindings` regenerates the bindings into a second directory and `diff -ru`s the outputs, preventing silent stale binding drift.
- `verifyUniffiKotlinBindings` fails if no generated Kotlin files are present or if generated files do not contain the expected Android package.
- `.github/workflows/ci.yml` runs `./gradlew --no-daemon :app:verifyUniffiKotlinBindings` in the exact-head CI gate.

## RMD-203 app-owned core gateway evidence

- `MainActivity.kt` imports and opens app-owned gateway classes including `GeneratedUniffiCoreGateway`, `GeneratedUniffiDownloadControlGateway`, `GeneratedUniffiLibraryPlaybackGateway`, and `GeneratedUniffiSourceAnalysisGateway` from `com.ekkus.offlineytplayer.coregateway`.
- Production bootstrap opens the file-backed app database under app-private storage, opens the generated gateways, invokes `GeneratedUniffiCoreGateway.reconcileStartup()`, lists library records and durable download queue state, and maps gateway records/errors into UI state through app-owned Kotlin model conversion.
- Blocking gateway open/list/reconcile work runs on `bootstrapExecutor`, not on the Android main thread.
- `SchedulingDownloadControlGateway` wraps the generated download-control gateway and the Android scheduler so durable enqueue/control remains centralized before platform execution scheduling.

## RMD-204 Android runtime FFI boundary evidence

- The Android smoke infrastructure proves that app and test APKs install and that instrumentation can run against the packaged APK.
- The normal CI Android job builds `assembleDebug` and `assembleDebugAndroidTest` after building the native Rust libraries for the supported ABIs.
- The exact-head CI `UniFFI Kotlin and Android ABI` job verifies generated Kotlin binding consistency and APK native-library packaging on every push/PR.
- Current runtime smoke coverage primarily proves installed APK/test infrastructure and packaged native-library availability. A fuller representative installed-device FFI call, including a round-trip record/error and temporary app-private DB/media root, should still be treated as the remaining RMD-204 completion boundary unless a later merged test demonstrates it explicitly.

## Current reconciliation assessment

Current `master` has sufficient evidence to reconcile RMD-201 and RMD-202 after this evidence PR itself is qualified and merged. RMD-203 has substantial production-path evidence through app-owned gateway wrappers and off-main-thread bootstrap, but should be reconciled only if the referenced gateway implementation/tests are also reviewed in the canonical TODO reconciliation PR. RMD-204 should remain open until installed-device instrumentation performs a representative real FFI call and verifies records/errors against a temporary app-private DB/media root.

## Qualification evidence

- Exact master `19fd085526f12e6b8a67797850ed238eedaea269` passed CI run `35850735745`, Android smoke run `35850735709`, and Android FGS-timeout run `35850735752`.

## Boundaries

This note does not close RMD-300 production YouTube source behavior, RMD-500 durable worker execution, RMD-600 app repository completeness, RMD-1400 full Android behavioral qualification, RMD-1500 deterministic E2E, or final RMD-1800 exact-head closeout.
