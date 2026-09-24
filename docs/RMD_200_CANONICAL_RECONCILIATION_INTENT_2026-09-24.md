# RMD-200 canonical reconciliation intent — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, RMD-200.

This document records the precise current-master evidence for checking RMD-201 through RMD-204 in the canonical remediation TODO. It is intentionally not a substitute for the canonical TODO. The canonical file must still be updated with the corresponding checkboxes and evidence paragraph before RMD-200 is considered reconciled.

## Current master and qualification baseline

Current master before this branch: `010228734192069d0fbbfd7b906fd9220911cd97`.

Post-merge master qualification on that exact SHA passed:

- CI `36017284912`
- Android smoke `36017284906`
- Android FGS timeout `36017284973`

Those master runs include the merged RMD-200/RMD-203 evidence notes and exercise the current fast-gate CI and small Android smoke strategy required by `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`.

## RMD-201 — Package Rust native libraries into the APK

Evidence supports checking all RMD-201 subtasks:

- `app/build.gradle.kts` defines the supported v1 ABI set as `arm64-v8a` from `aarch64-linux-android` and `x86_64` from `x86_64-linux-android`.
- `prepareRustJniLibs` copies `target/<rust-target>/debug/liboffline_yt_core.so` into the generated JNI directory under the matching Android ABI name.
- `prepareRustJniLibs` fails if either expected `.so` file is absent.
- The Android main source set consumes the generated JNI directory and `defaultConfig.ndk.abiFilters` derives from the same ABI map.
- `.github/workflows/ci.yml` builds both Rust Android targets, asserts both `.so` files exist, assembles the debug APK, lists APK contents, and fails unless both `lib/arm64-v8a/liboffline_yt_core.so` and `lib/x86_64/liboffline_yt_core.so` are packaged.

## RMD-202 — Compile generated UniFFI Kotlin bindings into the app

Evidence supports checking all RMD-202 subtasks:

- `generateUniffiKotlinBindings` generates Kotlin bindings from the built `offline-yt-core` library.
- Generated Kotlin is written under the Android build directory and added to the Android main Java/Kotlin source set.
- Kotlin compilation depends on binding generation.
- `verifyReproducibleUniffiKotlinBindings` regenerates bindings into a second directory and diffs the two generated trees.
- `verifyUniffiKotlinBindings` fails if no Kotlin files are generated or if the expected `com.ekkus.offlineytplayer.core` package is absent.
- Regular CI runs `./gradlew --no-daemon :app:verifyUniffiKotlinBindings`, so stale or divergent bindings fail the fast PR gate.

## RMD-203 — Create an app-owned core gateway

Evidence supports checking all RMD-203 subtasks:

- Stable app-owned interfaces exist in the Android core-gateway layer: `AppCoreGateway` and `AppDownloadControlGateway`.
- Generated UniFFI models and errors are converted through centralized gateway/policy mapping rather than leaking provider/generated payloads directly into UI surfaces.
- Blocking generated-core calls are executed through the gateway's asynchronous executor path, keeping production calls off the Android main thread.
- Gateway lifecycle/cancellation cleanup is explicit through close/use boundaries and executor shutdown/cancellation behavior.
- Repository/state APIs are exposed for library, download queue, item lookup/delete, startup reconciliation, enqueue, pause, resume, cancel, and retry surfaces used by the production composition root.
- Deterministic `FakeCoreGateway` and `FakeDownloadControlGateway` implementations exist for Android UI tests.

Merged evidence note: `docs/RMD_203_APP_CORE_GATEWAY_RECONCILIATION_2026-09-24.md`.

## RMD-204 — Android runtime FFI smoke test

Evidence supports checking all RMD-204 subtasks:

- `app/src/androidTest/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGatewaySmokeTest.kt` runs inside the installed packaged app process.
- It explicitly loads `offline_yt_core`.
- It opens a temporary app-private SQLite database/root.
- It executes generated-gateway library and durable-queue calls.
- It checks representative success, structured-error conversion, durable queue enqueue/pause/list round trips, library lookup/delete behavior, and temporary root cleanup.
- `.github/workflows/android-smoke.yml` includes `GeneratedUniffiCoreGatewaySmokeTest` in the small API-29 device-side smoke lane with exact-commit assertion.

## Exact evidence chain

Merged RMD-200 evidence note:

- `docs/RMD_200_UNIFFI_CURRENT_MASTER_EVIDENCE_2026-09-24.md`
- PR #358 exact head `0df83e1b9313f2e55486170a1c029e20c0cc4e86`
- Merged as `8b34e44a2645d161a629e1bf972dc7554324883b`

Merged RMD-203 evidence note:

- `docs/RMD_203_APP_CORE_GATEWAY_RECONCILIATION_2026-09-24.md`
- PR #359 exact head `8094b8051f33d8ca68bb099bf14edc6bff1d5784`
- Merged as `010228734192069d0fbbfd7b906fd9220911cd97`
- Post-merge master CI `36017284912`, Android smoke `36017284906`, and Android FGS timeout `36017284973` passed on exact SHA `010228734192069d0fbbfd7b906fd9220911cd97`.

## Required canonical TODO edit

The canonical TODO should be updated as a focused RMD-200 reconciliation PR that:

1. Changes every RMD-201, RMD-202, RMD-203, and RMD-204 checkbox from `[ ]` to `[x]`.
2. Adds an `Evidence (RMD-200)` paragraph immediately after the RMD-200 acceptance statement.
3. Cites the implementation paths, deterministic tests, merged evidence notes, exact PR heads, merge SHAs, and post-merge master run IDs listed above.
4. Leaves all other TODO sections unchanged.

This file is a staging/evidence artifact only. It must not be cited as completing RMD-200 until the canonical TODO itself is updated, qualified at exact head, merged to `master`, reloaded from `master`, and post-merge master CI is green.