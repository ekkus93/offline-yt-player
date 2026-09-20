# RMD-200 UniFFI integration reconciliation

This evidence note reconciles the implementation behind RMD-201 through RMD-204 without replacing or compressing the detailed remediation TODO. The TODO remains authoritative and must only have its individual checkboxes changed when this evidence is incorporated into that file.

## RMD-201 — Package Rust native libraries into the APK

Implemented. `app/build.gradle.kts` defines the v1 `arm64-v8a` ABI, maps it to `aarch64-linux-android`, stages `liboffline_yt_core.so` into generated JNI libs, and wires that directory into the Android main source set. `.github/workflows/ci.yml` builds the Android cdylib, verifies the file exists, assembles the APK, and fails unless `lib/arm64-v8a/liboffline_yt_core.so` is present. These paths are present on qualified master SHA `218a8e90b315a329506b4fbf9fb8819ac9489d53`; post-merge CI run `35501449031` passed on that exact SHA.

## RMD-202 — Compile generated UniFFI Kotlin bindings into the app

Implemented. `app/build.gradle.kts` generates Kotlin from the built core library, adds the generated directory to the main Java/Kotlin source set, regenerates into an independent verification directory, and diffs both outputs. `.github/workflows/ci.yml` runs `:app:verifyUniffiKotlinBindings`. Implementation was qualified at exact head `d7e72741ea18a93638b6cd36867a546ef41192ad`; post-merge CI run `35496767918` passed.

## RMD-203 — Create an app-owned core gateway

Implemented. App-owned gateway interfaces and generated implementations under `app/src/main/java/com/ekkus/offlineytplayer/coregateway/` wrap generated UniFFI services, centralize model/error mapping, reject blocking main-thread calls, expose async operations, and provide deterministic fake gateways. `MainActivity.kt` owns and closes production gateway instances. Exact implementation head `2acd4e9d97825d18accf06fe98122dd632f06fe5` passed push CI `35497888818` and PR CI `35497891688` before merge.

## RMD-204 — Android runtime FFI smoke test

Implemented as test coverage, with CI execution still separately tracked by RMD-1401/RMD-1601. `app/src/androidTest/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGatewaySmokeTest.kt` explicitly loads `offline_yt_core`, opens an app-cache-private temporary SQLite database, exercises generated core and download-control gateways, verifies structured invalid-input error behavior, enqueues and pauses durable queue state, reads it back through the core gateway, and cleans up the temporary root. The implementation head `2acd4e9d97825d18accf06fe98122dd632f06fe5` passed push CI `35497888818` and PR CI `35497891688` before merge.

This note deliberately does not claim that Android instrumentation currently executes in regular CI. That remaining qualification gap belongs to RMD-1401/RMD-1601 and must remain fail-closed until an emulator/device lane runs these tests.
