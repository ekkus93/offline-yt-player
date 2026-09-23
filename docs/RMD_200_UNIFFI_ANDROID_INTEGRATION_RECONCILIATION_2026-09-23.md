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

- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt` defines the stable generated-binding-agnostic `AppCoreGateway` interface and app-owned `CoreLibraryItem`, `CoreDownloadSnapshot`, `CoreGatewayResult`, and `CoreGatewayError` models. Generated UniFFI reflection and model/error conversion are centralized in `GeneratedUniffiCoreGateway`.
- `CoreCallDispatcher` owns a lifecycle-scoped executor. Production synchronous gateway methods reject main-thread execution through `checkNotMainThread()`, async entry points return cancellable `Future` handles, and `close()` calls `shutdownNow()` so lifecycle teardown interrupts queued/running dispatcher work. Rust long-running operations separately expose cooperative `FfiCancellationToken`.
- `AppDownloadControlGateway.kt` applies the same app-owned boundary to enqueue/pause/resume/cancel/retry, with off-main-thread checks and async futures. `SchedulingDownloadControlGateway` keeps durable queue control and Android scheduling behind that boundary.
- `AppSourceAnalysisGateway.kt` keeps generated YouTube source objects behind an app-owned analysis interface and uses a generated cooperative cancellation token for blocking source calls. Superseded-analysis orchestration remains an RMD-603 concern rather than a missing RMD-203 gateway primitive.
- The gateway exposes library list/get/delete, durable download queue state, startup reconciliation, and durable download controls suitable for repository/ViewModel consumption without leaking generated record types.
- `FakeCoreGateway` and `FakeDownloadControlGateway` provide deterministic fake implementations. `AppCoreGatewayPolicyTest` exercises fake repository state, generated-state conversion, generated-service isolation, async/off-main-thread policy surface, and the absence of empty production callbacks.
- `MainActivity.bootstrapProductionUi` opens the file-backed app-private database and app-owned gateways off the main thread, invokes startup reconciliation, and consumes library/download state through the gateway models.

This audit satisfies all six RMD-203 subtasks. It does not claim RMD-603 superseded-request orchestration or later UI/E2E acceptance.

## RMD-204 Android runtime FFI evidence

- `app/src/androidTest/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGatewayInstrumentedTest.kt` is an installed-app instrumentation smoke against the app-owned UniFFI core gateway.
- Its success-path test creates an isolated app-private temporary database and media root, opens `GeneratedUniffiCoreGateway`, runs startup reconciliation, library listing, and durable queue listing through generated services, verifies representative returned records, closes the gateway, and verifies the private storage boundary.
- Its deterministic error-path test opens the same packaged generated gateway against an isolated app-private database, deliberately removes the temporary `library_items` table, invokes `listLibrary()`, and verifies the generated persistence error round-trips through `CoreGatewayError` with a non-empty message and non-retryable classification.
- `.github/workflows/android-smoke.yml` executes the packaged gateway smoke in the bounded API-29 runtime lane introduced by the Android qualification acceleration plan.
- PR #334 added the installed-app gateway success smoke and merged as `a7508362a781ca664f9000795319c87b948bc1bb`; post-merge master CI `35859587774` and Android smoke `35859587715` passed on that exact merge SHA.
- PR #337 added the installed-app generated-error round trip at exact head `e6f5e265d571a2aba1e9885754e260e13d0e8f8f`. That exact head passed PR CI `35877487952`, PR Android smoke `35877487924`, PR API-35 FGS timeout `35877488006`, push CI `35877460401`, push Android smoke `35877460207`, and push API-35 FGS timeout `35877460350`; PR #337 merged as `6ee86b081ab10131c03c6cd894d69fc83a6dc642`, whose post-merge master CI `35879643138`, Android smoke `35879643144`, and API-35 FGS timeout `35879643231` passed.

All four RMD-204 runtime-smoke subtasks now have production-path installed-app evidence: packaged native loading, a real representative FFI call, representative success records plus generated error conversion, and isolated app-private DB/media storage.

## Qualification evidence

- RMD-201/RMD-202 packaging and generated-binding checks are enforced by the regular exact-head CI fast gate: Rust fmt/clippy/tests, Android lint/JVM/build, reproducible UniFFI Kotlin generation, both Android Rust ABI builds, and APK native-library verification.
- RMD-203 audit PR #336 exact head `66156b6727b8768070a41277bfa98a55009128b9` passed PR CI `35872154642`, PR Android smoke `35872154651`, PR API-35 FGS timeout `35872154649`, push CI `35872148716`, push Android smoke `35872148720`, and push API-35 FGS timeout `35872148693`; PR #336 merged as `e2ea622d6b6470a4417370f8d7a6a4142424af61`, whose post-merge CI `35874527897`, Android smoke `35874527873`, and API-35 FGS timeout `35874527911` passed.
- RMD-204 error-smoke PR #337 exact head and qualification are recorded above. The Android smoke lane supplies the acceleration-plan runtime tier without conflating it with later full Compose/golden/E2E qualification.

## Current reconciliation assessment

RMD-201, RMD-202, RMD-203, and RMD-204 now have audited implementation and deterministic qualification evidence sufficient for canonical TODO reconciliation. This does not close later application behavior or full qualification milestones.

## Boundaries

This note does not close RMD-300 production YouTube behavior, RMD-500 durable worker execution, RMD-600 repository completeness, RMD-1400 full Android behavioral qualification, RMD-1500 deterministic E2E, or final RMD-1800 exact-head closeout.
