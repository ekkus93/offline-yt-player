# RMD-200 UniFFI integration reconciliation

This evidence note reconciles the implementation behind RMD-201 through RMD-204 without replacing or compressing the detailed remediation TODO. The TODO remains authoritative and must only have its individual checkboxes changed when this evidence is incorporated into that file.

## RMD-201 — Package Rust native libraries into the APK

Implemented and qualified. `app/build.gradle.kts` defines the v1 packaged ABIs `arm64-v8a` and emulator-compatible `x86_64`, maps them to `aarch64-linux-android` and `x86_64-linux-android`, stages `liboffline_yt_core.so` into generated JNI libs, and wires that directory into the Android main source set. `.github/workflows/ci.yml` builds both Android cdylibs, verifies both files exist, assembles the APK, and fails unless both `lib/arm64-v8a/liboffline_yt_core.so` and `lib/x86_64/liboffline_yt_core.so` are present. The original arm64 integration was qualified on master SHA `218a8e90b315a329506b4fbf9fb8819ac9489d53` with post-merge CI `35501449031`; the x86_64 smoke-lane packaging was merged through PR #318 as `751b1762861f795aed1245c1fe48b9e521019856` and is continuously verified by the fast CI gate.

## RMD-202 — Compile generated UniFFI Kotlin bindings into the app

Implemented and qualified. `app/build.gradle.kts` generates Kotlin from the built core library, adds the generated directory to the main Java/Kotlin source set, regenerates into an independent verification directory, and diffs both outputs. `.github/workflows/ci.yml` runs `:app:verifyUniffiKotlinBindings`. Exact implementation head `d7e72741ea18a93638b6cd36867a546ef41192ad` passed qualification and post-merge CI `35496767918`; current master CI continues to run the generation-consistency gate.

## RMD-203 — Create an app-owned core gateway

Implemented and qualified. App-owned gateway interfaces and generated implementations under `app/src/main/java/com/ekkus/offlineytplayer/coregateway/` wrap generated UniFFI services, centralize model/error mapping, reject blocking main-thread calls, expose async operations, define lifecycle/close behavior, expose repository/state APIs, and provide deterministic fake gateways for Android UI tests. `MainActivity.kt` owns and closes production gateway instances. Exact implementation head `2acd4e9d97825d18accf06fe98122dd632f06fe5` passed push CI `35497888818` and PR CI `35497891688` before merge.

## RMD-204 — Android runtime FFI smoke test

Implemented and now executed in CI. `app/src/androidTest/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGatewaySmokeTest.kt` explicitly loads `offline_yt_core`, opens an app-cache-private temporary SQLite database, executes real generated core and download-control FFI calls, verifies structured invalid-input error conversion, round-trips durable queue records/state through enqueue/list/pause operations, reads missing library records, and cleans up the temporary root. PR #320 changed `.github/workflows/android-smoke.yml` so the API-29 device-side smoke lane executes both `AndroidRuntimeSmokeTest` and `GeneratedUniffiCoreGatewaySmokeTest` rather than leaving the FFI test as source-only coverage. Exact implementation head `700e06556cb2eacbb76b6e7c4c078020f1fbe237` passed push CI `35815237261`, push Android smoke `35815237204`, PR CI `35815386946`, and PR Android smoke `35815386970`. PR #320 merged as `119460a7f872aea520093f0fadd95cf52fd6c0b1`; post-merge master CI `35816014322` and Android smoke `35816014360` both passed on that exact merge SHA.

## Reconciliation result

RMD-201 through RMD-204 now have implementation, deterministic qualification, exact-head CI, merged-master evidence, and post-merge master verification. The canonical remediation TODO may therefore reconcile each RMD-200 checkbox as complete while preserving the broader RMD-1402+, RMD-1500+, RMD-1600, and RMD-1800 qualification requirements as open. This note does not claim that the full Android behavioral/golden/E2E matrix is complete.