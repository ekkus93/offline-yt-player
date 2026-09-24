# RMD-200 UniFFI current-master evidence — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, RMD-200.

This note records current-master evidence for the implemented portions of RMD-200 without marking the canonical TODO complete by assertion. The canonical RMD-200 checkboxes remain the source of truth and should only be checked after this evidence is qualified, merged, and any remaining RMD-203 gateway/lifecycle scope is independently reconciled.

## RMD-201 — Android ABI packaging evidence

Current master defines the v1 supported Android ABI set in `app/build.gradle.kts` as:

- `arm64-v8a` from Rust target `aarch64-linux-android`
- `x86_64` from Rust target `x86_64-linux-android`

The Gradle `prepareRustJniLibs` task copies `target/<rust-target>/debug/liboffline_yt_core.so` into a generated JNI directory under the matching Android ABI name and fails before packaging if either expected Rust library is absent. The Android main source set consumes that generated JNI directory, and `defaultConfig.ndk.abiFilters` derives its ABI filter set from the same target map.

The regular CI `UniFFI Kotlin and Android ABI` job builds both Rust Android targets, asserts both `.so` files exist, assembles the debug APK, lists APK contents, and fails unless both `lib/arm64-v8a/liboffline_yt_core.so` and `lib/x86_64/liboffline_yt_core.so` are packaged.

## RMD-202 — Generated UniFFI Kotlin evidence

Current master generates Kotlin bindings from the built `offline-yt-core` library through `generateUniffiKotlinBindings`, writing generated Kotlin into the Android build directory and adding that directory to the Android main Java/Kotlin source set. Kotlin compilation depends on binding generation.

The `verifyReproducibleUniffiKotlinBindings` task independently regenerates the bindings into a second directory and diffs the two generated trees. The `verifyUniffiKotlinBindings` task additionally fails if no Kotlin files are generated or if the expected `com.ekkus.offlineytplayer.core` package is absent. The regular CI UniFFI job runs `./gradlew --no-daemon :app:verifyUniffiKotlinBindings`, so stale/divergent bindings fail the fast PR gate.

## RMD-204 — Packaged runtime FFI smoke evidence

`app/src/androidTest/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGatewaySmokeTest.kt` runs in the installed packaged app process. It explicitly loads `offline_yt_core`, opens a temporary app-private SQLite database, exercises generated gateway calls, checks representative success and structured-error conversion, round-trips durable queue state through enqueue/pause/list calls, checks library lookup/delete behavior, and deletes the temporary app-private root afterward.

`.github/workflows/android-smoke.yml` includes `GeneratedUniffiCoreGatewaySmokeTest` in the small API-29 device-side smoke lane alongside the existing runtime smoke coverage. That lane has an exact-commit assertion and is intentionally the acceleration-plan fast runtime tier rather than the later full Compose/golden/E2E matrix.

## Current exact-head qualification available before this evidence note

The current master SHA before this note is `f0312ac568b02fc86cafdce089207be7c5d2be59`. It passed:

- CI `35993085847`
- Android smoke `35993085815`
- Android FGS timeout `35993085797`

Those runs include exact-commit assertions and exercise the current RMD-201, RMD-202, and RMD-204 production/CI paths described above.

## Reconciliation boundary

RMD-201, RMD-202, and RMD-204 appear to have current-master implementation plus deterministic CI/runtime qualification. RMD-203 is not claimed complete by this note: its stable app-owned gateway interface, model/error conversion, off-main-thread execution, cancellation/lifecycle semantics, repository/state API surface, and deterministic fake implementation still need to be audited as a unit before its canonical checkboxes can be reconciled.
