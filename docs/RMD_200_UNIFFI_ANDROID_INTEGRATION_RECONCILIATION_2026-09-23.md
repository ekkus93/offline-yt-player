# RMD-200 UniFFI Android integration reconciliation

This evidence note records current-master implementation and qualification evidence for RMD-200 without replacing or compressing the canonical checklist in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Scope

RMD-200 requires real Rust/Android UniFFI integration: Android must package the Rust native libraries, compile generated UniFFI Kotlin bindings into the app, use an app-owned core gateway, and prove representative runtime FFI behavior from an installed Android app/test APK.

This note is evidence-only. The canonical remediation TODO must still be reconciled separately after the evidence is qualified and merged.

## RMD-201 native-library packaging evidence

- `app/build.gradle.kts` defines the v1 Android ABI set as `arm64-v8a -> aarch64-linux-android` and `x86_64 -> x86_64-linux-android`.
- `prepareRustJniLibs` copies `liboffline_yt_core.so` for both targets into generated JNI libs and fails if either expected library is absent.
- `android.defaultConfig.ndk.abiFilters` is derived from the same supported ABI set.
- `.github/workflows/ci.yml` builds both Rust Android targets and fails unless the APK contains `lib/arm64-v8a/liboffline_yt_core.so` and `lib/x86_64/liboffline_yt_core.so`.

## RMD-202 generated UniFFI Kotlin evidence

- `app/build.gradle.kts` regenerates Kotlin bindings from the built Rust library through the repository UniFFI bindgen tool and adds the generated directory to the Android main source set.
- `verifyReproducibleUniffiKotlinBindings` independently regenerates and diffs the output trees.
- `verifyUniffiKotlinBindings` fails if generated Kotlin is absent or the expected Android package is missing.
- The regular exact-head CI UniFFI job executes this verification before ABI/APK packaging checks.

## RMD-203 app-owned core gateway audit

The gateway surface was re-audited on exact master `41cec8e44aa6c56272aaa76e675e886043b94d04` rather than inferred from prior checklist state.

- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt` defines the stable generated-binding-agnostic `AppCoreGateway` interface and app-owned `CoreLibraryItem`, `CoreDownloadSnapshot`, `CoreGatewayResult`, and `CoreGatewayError` models. Generated UniFFI reflection and model/error conversion are centralized in `GeneratedUniffiCoreGateway`.
- `CoreCallDispatcher` owns a lifecycle-scoped executor. Production synchronous gateway methods reject main-thread execution through `checkNotMainThread()`, async entry points return cancellable `Future` handles, and `close()` calls `shutdownNow()` so lifecycle teardown interrupts queued/running dispatcher work. Rust long-running operations separately expose cooperative `FfiCancellationToken`; this preserves explicit application-level cancellation rather than relying on foreign-future cancellation semantics.
- `AppDownloadControlGateway.kt` applies the same app-owned boundary to enqueue/pause/resume/cancel/retry, with off-main-thread checks and async futures. `SchedulingDownloadControlGateway` keeps durable queue control and Android scheduling behind that boundary.
- `AppSourceAnalysisGateway.kt` keeps generated YouTube source objects behind an app-owned analysis interface and uses a generated cooperative cancellation token for the blocking source calls. Superseded-analysis orchestration remains an RMD-603 concern rather than a missing RMD-203 gateway primitive.
- The gateway exposes library list/get/delete, durable download queue state, startup reconciliation, and durable download controls suitable for repository/ViewModel consumption without leaking generated record types.
- `FakeCoreGateway` and `FakeDownloadControlGateway` provide deterministic fake implementations. `AppCoreGatewayPolicyTest` exercises fake repository state, generated-state conversion, generated-service isolation, async/off-main-thread policy surface, and the absence of empty production callbacks.
- `MainActivity.bootstrapProductionUi` opens the file-backed app-private database and app-owned gateways off the main thread, invokes startup reconciliation, and consumes library/download state through the gateway models.

This audit satisfies all six RMD-203 subtasks. It does not claim RMD-603 superseded-request orchestration or later UI/E2E acceptance.

## RMD-204 Android runtime FFI evidence

- `app/src/androidTest/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGatewayInstrumentedTest.kt` is an installed-app instrumentation smoke against the app-owned UniFFI core gateway.
- It creates an isolated app-private temporary database and media root, opens `GeneratedUniffiCoreGateway`, runs startup reconciliation, library listing, and durable queue listing through generated services, verifies representative returned records, closes the gateway, and verifies the private storage boundary.
- `.github/workflows/android-smoke.yml` executes the packaged gateway smoke in the bounded API-29 runtime lane introduced by the Android qualification acceleration plan.
- PR #334 added the installed-app gateway smoke and merged as `a7508362a781ca664f9000795319c87b948bc1bb`. Post-merge master CI `35859587774` and Android smoke `35859587715` passed on that exact merge SHA.

The current smoke proves packaged native loading and representative success-record round trips. The canonical RMD-204 error-round-trip subtask must remain unchecked until a deterministic installed-app error result is exercised through the same generated gateway boundary.

## Qualification evidence

- Exact master `41cec8e44aa6c56272aaa76e675e886043b94d04` passed CI `35861440508`, Android smoke `35861440339`, and API-35 Android FGS-timeout qualification `35861440404`.
- Audit head `af514ca8525e32800476f1b5093257b8ed117f15` passed PR CI `35864123630`, PR Android smoke `35864123399`, PR API-35 FGS-timeout `35864123641`, push CI `35864106413`, and push Android smoke `35864106335`. Its duplicate push API-35 lane `35864106231` reported an instrumentation failure while the same-head PR API-35 lane passed; the audit remains subject to fresh exact-head qualification after this evidence update.
- The CI fast gate includes Rust fmt/clippy/tests, Android lint/JVM/build, reproducible UniFFI Kotlin generation, both Android Rust ABI builds, and APK native-library verification.
- The Android smoke lane supplies the acceleration-plan runtime tier without conflating it with later full Compose/golden/E2E qualification.

## Current reconciliation assessment

RMD-201, RMD-202, and RMD-203 now have audited implementation and deterministic qualification evidence. RMD-204 has three satisfied runtime-smoke requirements but its representative error round-trip remains open. Canonical TODO reconciliation must preserve that distinction.

## Boundaries

This note does not close RMD-300 production YouTube behavior, RMD-500 durable worker execution, RMD-600 repository completeness, RMD-1400 full Android behavioral qualification, RMD-1500 deterministic E2E, or final RMD-1800 exact-head closeout.
