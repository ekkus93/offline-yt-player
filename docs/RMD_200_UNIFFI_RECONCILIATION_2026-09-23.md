# RMD-200 Rust/Android UniFFI reconciliation

This evidence note records current-master implementation and qualification evidence for RMD-201, RMD-202, and RMD-204 without replacing or compressing the canonical remediation TODO. RMD-203 remains a separate reconciliation target until its full gateway/lifecycle/fake-interface surface is re-audited against current master.

## RMD-201 — Package Rust native libraries into the APK

`app/build.gradle.kts` defines the supported v1 Android ABI set as `arm64-v8a` (`aarch64-linux-android`) and `x86_64` (`x86_64-linux-android`). `prepareRustJniLibs` copies `liboffline_yt_core.so` for both targets into a generated JNI directory and fails when either expected library is absent. The generated JNI directory is wired into the Android main source set and the ABI filters are derived from the same target map.

`.github/workflows/ci.yml` builds both Rust Android targets, verifies both `.so` files exist, assembles the debug APK, lists the APK contents, and fails unless both `lib/arm64-v8a/liboffline_yt_core.so` and `lib/x86_64/liboffline_yt_core.so` are packaged.

## RMD-202 — Compile generated UniFFI Kotlin bindings into the app

`app/build.gradle.kts` generates Kotlin bindings from the built `offline-yt-core` library with the repository's UniFFI bindgen tool, places generated Kotlin under the Android build directory, and adds that directory to the main Java/Kotlin source set. Kotlin compilation depends on binding generation.

`verifyReproducibleUniffiKotlinBindings` independently regenerates bindings and diffs the two generated trees. `verifyUniffiKotlinBindings` additionally fails when no Kotlin files are generated or when the expected `com.ekkus.offlineytplayer.core` package is absent. The regular CI UniFFI job executes this verification before Android ABI and APK packaging checks, preventing silently stale generated bindings.

## RMD-204 — Android runtime FFI smoke

`app/src/androidTest/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGatewaySmokeTest.kt` runs against the installed packaged application. It explicitly loads `offline_yt_core`, opens a temporary app-private SQLite database, executes generated-gateway library and durable-queue calls, exercises representative success and structured-error conversion, enqueues and pauses durable work, and verifies state round-trips through the real native core before deleting the temporary app-private root.

`.github/workflows/android-smoke.yml` includes `GeneratedUniffiCoreGatewaySmokeTest` in the small API-29 device-side smoke lane. This is the acceleration-plan fast runtime tier: it proves native loading and representative real FFI behavior without waiting for the later full Compose/golden/E2E matrix.

## Qualification evidence

The current regular CI fast gate contains exact-commit assertions plus Rust fmt/clippy/tests, reproducible UniFFI Kotlin generation, both Android Rust ABI builds, APK native-library verification, Android lint/JVM tests, and Android assembly. The Android smoke workflow contains an exact-commit assertion and executes the packaged native/gateway smoke on an emulator.

The immediately preceding merged scheduler/notification evidence PR #327 passed exact-head push CI `35833267719`, PR CI `35833272325`, push Android smoke `35833267706`, and PR Android smoke `35833272315` on `cbaab677cbd0fea0b63fe57a0fb5efdbf7a459b9`; it merged to master as `192d9f6f569a7483a44c88a132c24a2a32fa1c3c`. Those runs exercised the unchanged RMD-201/RMD-202/RMD-204 production and CI paths described above.

## Reconciliation result

RMD-201, RMD-202, and RMD-204 have production implementation plus deterministic CI/runtime qualification on current master and are ready for canonical-TODO reconciliation after this evidence note itself is qualified and merged. RMD-203 is intentionally not claimed complete by this note; its stable app-owned gateway, model/error conversion, off-main-thread execution, cancellation/lifecycle semantics, repository/state APIs, and deterministic fake must be audited as a unit before its checkboxes are reconciled.