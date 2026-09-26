# Offline YT Player — Comprehensive Remediation TODO

**Document:** `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`  
**Normative spec:** `docs/OFFLINE_YT_PLAYER_REMEDIATION_SPEC_2026-09-17.md`  
**Reviewed baseline:** `b8aebfd0467de67a2cc0b0a583d91f9a1783da7c`

This checklist repairs the implementation and qualification gaps found during the post-closeout code review. It is intentionally detailed and must remain detailed through closeout.

## Global completion rules

- [ ] **RMD-G01 — No checkbox-by-assertion.** A capability is not complete merely because a policy constant, enum, boolean, source string, or TODO entry says it exists.
- [ ] **RMD-G02 — Production-path proof.** Every user-facing capability must have working production wiring plus behavioral test/evidence.
- [ ] **RMD-G03 — Preserve detailed state.** Do not replace this file with a compressed all-checked summary. A separate summary may be added later.
- [ ] **RMD-G04 — Exact-head evidence.** Every milestone merge records the exact implementation SHA and relevant CI run(s).
- [ ] **RMD-G05 — No empty production actions.** No production button/action callback required by v1 may remain `onClick = {}` or equivalent no-op.
- [ ] **RMD-G06 — No fabricated production data.** Preview/test fixture data must not be used as production resolved metadata or repository state.
- [ ] **RMD-G07 — Fix newly discovered blockers.** Any new correctness/security/platform bug discovered while implementing this TODO is added here or to a linked follow-up before the affected milestone is closed.
- [ ] **RMD-G08 — External release gate remains separate.** Engineering work must not mark the human YouTube/service-policy/legal gate approved.

---

## RMD-000 — Freeze the remediation baseline and restore truthful tracking

### RMD-001 — Record reviewed baseline and issue inventory

- [x] Add the remediation spec and this TODO to `docs/`.
- [x] Record baseline SHA `b8aebfd0467de67a2cc0b0a583d91f9a1783da7c`.
- [x] Add a short `docs/REMEDIATION_BASELINE_AUDIT.md` mapping each code-review finding to a remediation task ID.
- [x] Link the prior detailed TODO from git history for historical acceptance criteria.
- [x] Explicitly document that the previous final reconciliation changed checklist state without corresponding implementation for all claimed items.

**Acceptance:** every review finding is mapped to at least one unchecked task in this file.

**Evidence (RMD-001):** implementation/docs in `docs/REMEDIATION_BASELINE_AUDIT.md` plus the remediation spec/TODO; implementation SHA `2b11de56304da835808016f8e67ad3709e11a55b`; CI run `35316105685` passed on that exact SHA. Historical detailed checklist: `docs/OFFLINE_YT_PLAYER_TODO.md` at `4c977462a1f0ff885484aa88f6936b8dcc2cbf2f`.

### RMD-002 — Add anti-false-closeout reconciliation rules

- [x] Document evidence required before checking a task: implementation path, behavioral test path, exact SHA/run.
- [x] Require task reconciliation to reference evidence rather than narrative assertions.
- [x] Add a final script/test that fails if the detailed remediation TODO contains unchecked items during a release-closeout workflow.
- [x] Ensure that script does not mutate or auto-check the TODO.

**Acceptance:** a documentation-only summary cannot make unresolved detailed tasks appear complete.

**Evidence (RMD-002):** `scripts/check_remediation_closeout.py`, `tests/test_check_remediation_closeout.py`, `.github/workflows/remediation-closeout.yml`, and the regular CI governance job; implementation SHA `2b11de56304da835808016f8e67ad3709e11a55b`; CI run `35316105685` passed on that exact SHA. The guard is read-only and fails closeout while any unchecked detailed checklist item remains.

---

## RMD-100 — Android manifest and platform compliance

### RMD-101 — Restore network capability

- [x] Add `android.permission.INTERNET` to `app/src/main/AndroidManifest.xml`.
- [x] Retain only permissions actually needed by implemented runtime paths.
- [x] Add a manifest-level test/assertion for required network permission.
- [x] Add an Android runtime fixture test that performs a local deterministic HTTP request through the packaged app/core path.

**Acceptance:** the Android app can open the deterministic fixture network endpoint on an emulator/device.

**Evidence (RMD-101):** `app/src/main/AndroidManifest.xml` declares `android.permission.INTERNET` and avoids broad storage/location permissions that are not needed by implemented runtime paths. `app/src/test/java/com/ekkus/offlineytplayer/ManifestPermissionTest.kt` asserts required network permissions and rejects unneeded broad permissions. `app/src/androidTest/java/com/ekkus/offlineytplayer/NetworkCapabilityInstrumentedTest.kt` runs a deterministic loopback HTTP fixture through the packaged app process. Supporting reconciliation is recorded in `docs/RMD_101_NETWORK_CAPABILITY_RECONCILIATION_2026-09-23.md`. Qualified/merged evidence: PR #323 exact head `83c158817e012df8b53d183ebb231b95548a4841` passed PR CI `35819180161`, PR Android smoke `35819180151`, push CI `35819168370`, and push Android smoke `35819168389`; PR #323 merged as `ea39061a3f2864a360a2912d6a23785190b92a99`; post-merge master CI `35820668106` and Android smoke `35820668108` passed on exact merge SHA `ea39061a3f2864a360a2912d6a23785190b92a99`.

### RMD-102 — Replace illegal/fragile boot-start behavior

- [x] Stop launching a `dataSync` foreground service directly from `BOOT_COMPLETED` on target SDK 35+.
- [x] Define boot recovery as durable-state reconciliation plus legal future scheduling.
- [x] Decide whether `LOCKED_BOOT_COMPLETED` is actually required.
- [x] If not required, remove it.
- [x] If required, mark the receiver Direct-Boot-aware and move only necessary pre-unlock state to device-protected storage.
- [x] Ensure credential-encrypted DB/media are not opened before unlock.
- [x] Add API-appropriate boot/restart tests.

**Acceptance:** reboot recovery never throws `ForegroundServiceStartNotAllowedException` and never accesses unavailable credential-protected state.

**Evidence (RMD-102):** `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadRebootReceiver.kt` treats `BOOT_COMPLETED` as a durable-state signal only, does not call service/scheduler launch APIs from the receiver, does not declare or require `LOCKED_BOOT_COMPLETED`, and defers recovery to `MainActivity.bootstrapProductionUi -> GeneratedUniffiCoreGateway.reconcileStartup` after credential-protected storage is available. JVM coverage in `DownloadBootRecoveryPolicyTest` and `DownloadRebootRecoveryPolicyTest` proves no `dataSync` FGS boot launch, no pre-unlock credential-protected storage access, ignored non-boot broadcasts, and legal deferred recovery. Supporting reconciliation is recorded in `docs/RMD_102_BOOT_RECOVERY_RECONCILIATION_2026-09-23.md`. Qualified/merged evidence includes exact master `4a36792f866bc596b7470d5f0328205d75c7f898` passing CI `35822366166` and Android smoke `35822366114`, plus later exact master `99dfcb811b26c7f9d102ac87c646b88031fdc7dc` passing CI `35835796521` and Android smoke `35835796558`.

### RMD-103 — Adopt a compliant download runtime by API level

- [x] Introduce an Android `DownloadExecutionScheduler` abstraction.
- [x] On API 34+, implement User-Initiated Data Transfer jobs for user-requested downloads.
- [x] Add `android.permission.RUN_USER_INITIATED_JOBS` for the API 34+ path.
- [x] Supply required network constraints and estimated bytes when known.
- [x] Attach/update the required UIDT notification.
- [x] On API 26-33, implement and document a compatible foreground/background transfer fallback.
- [x] Keep both paths on the same durable queue/control model.
- [x] Add tests for scheduler selection by SDK.

**Acceptance:** a user-triggered download is scheduled legally on API 26-33 and API 34+ without duplicating domain state.

**Evidence (RMD-103):** `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadExecutionScheduler.kt` introduces `DownloadExecutionScheduler`, selects UIDT jobs on SDK 34+ and foreground-service fallback on SDK 26-33, wires `DownloadUserInitiatedJobService`, supplies durable queue identity extras, network constraints, estimated bytes, `setUserInitiated(true)`, and UIDT notification ownership, while the fallback starts `DownloadForegroundService` with the same durable queue id. `SchedulingDownloadControlGateway.kt` keeps enqueue/schedule flows on the durable control path. Coverage is in `DownloadExecutionSchedulerPolicyTest` and `SchedulingDownloadControlGatewayTest`; `docs/ANDROID_BACKGROUND_EXECUTION.md` documents the API-specific runtime boundaries. Supporting reconciliation is recorded in `docs/RMD_103_ANDROID_SCHEDULER_RECONCILIATION_2026-09-23.md`. Qualified/merged evidence: PR #326 exact head `93ecb78670c178853173f3d83e145512e28d0d37` passed PR CI `35827300313`, PR Android smoke `35827300329`, push CI `35827278438`, and push Android smoke `35827278467`; PR #326 merged as `cf5f5e588118273d3dd88eff42ca97994513e232`; post-merge master CI `35830038817` and Android smoke `35830038810` passed on exact merge SHA `cf5f5e588118273d3dd88eff42ca97994513e232`. The scheduler remains separate from RMD-500's durable worker-loop closeout.

### RMD-104 — Handle foreground-service timeout paths if retained

- [x] Inventory every remaining `dataSync`/`mediaProcessing` foreground service.
- [x] Implement `Service.onTimeout(...)` for any path subject to Android 15+ time limits.
- [x] Persist resumable state before stopping.
- [x] Add ADB/emulator qualification using shortened foreground-service timeout where applicable.

**Acceptance:** forced timeout ends cleanly without a fatal `RemoteServiceException` and without corrupting download state.

**Evidence (RMD-104):** `app/src/main/AndroidManifest.xml` retains one download `dataSync` service and one media playback service, with no retained media-processing foreground service; `DownloadForegroundServiceInventory` records the retained-service inventory and service-type rationale. `DownloadForegroundService.onTimeout()` delegates to `DownloadForegroundTimeoutHandler`, which routes the timeout through `AppDownloadControlGateway.pause()` so resumable durable state is persisted before the service stops. `DownloadForegroundServiceTimeoutPolicyTest`, `DownloadForegroundTimeoutPolicyTest`, and `DownloadForegroundServicePolicyTest` cover the production source and timeout-control contract. Emulator qualification exists in `.github/workflows/android-fgs-timeout.yml` and `app/src/androidTest/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundTimeoutInstrumentedTest.kt`, using shortened dataSync timeout settings and asserting the packaged service exits cleanly without a fatal crash while a queued download remains paused/recoverable. Supporting reconciliation is recorded in `docs/RMD_104_FOREGROUND_SERVICE_TIMEOUT_RECONCILIATION_2026-09-23.md`. Qualified/merged evidence: PR #327 exact head `99dfcb811b26c7f9d102ac87c646b88031fdc7dc` passed CI `35835796521`, Android smoke `35835796558`, and API-35 FGS timeout qualification `35835796527`; PR #327 merged as `6af2628bdf7f208587c39dc11314f4ee91c371d8`; post-merge master CI `35837772237`, Android smoke `35837772239`, and Android FGS timeout `35837772235` passed on exact merge SHA `6af2628bdf7f208587c39dc11314f4ee91c371d8`.

### RMD-105 — Notification permission and degraded path

- [x] On Android 13+, request/handle `POST_NOTIFICATIONS` before relying on foreground notifications.
- [x] If denied, still maintain durable in-app queue state and clear user messaging.
- [x] Ensure notification denial does not silently break downloads.
- [x] Add tests for permission denied/granted behavior.

**Acceptance:** notification permission state cannot cause an invisible uncontrolled transfer.

**Evidence (RMD-105):** `NotificationPermissionCoordinator` performs the Android 13+ permission request/recording flow, `DownloadNotificationPermissionPolicy` defines granted vs denied behavior, and `DownloadNotificationPermissionStateStore` records grant/denial state without mutating durable queue state. `NotificationPermissionBanner` and `AppShell` surface clear in-app degraded-mode messaging when notification permission is denied; downloads remain represented by durable in-app queue state rather than relying on notification visibility. Tests cover SDK gating, granted/denied behavior, persistent grant-state storage, and the instrumentation contract in `DownloadNotificationPermissionPolicyTest`, `NotificationPermissionCoordinatorTest`, `NotificationPermissionUiTest`, and `NotificationPermissionInstrumentationContractTest`. Supporting reconciliation is recorded in `docs/RMD_105_NOTIFICATION_PERMISSION_RECONCILIATION_2026-09-23.md`. Qualified/merged evidence: PR #328 exact head `8f0bf5f8eff64bde7d601871061c75d15a6958c9` passed PR CI `35842547874`, PR Android smoke `35842547734`, PR Android FGS timeout `35842547742`, push CI `35842394994`, push Android smoke `35842394999`, and push Android FGS timeout `35842394966`; PR #328 merged as `76b2d0ebd3c2a6c00118d58c72840e48ef4e7d12`; post-merge master CI `35844050343`, Android smoke `35844050277`, and Android FGS timeout `35844050251` passed on exact merge SHA `76b2d0ebd3c2a6c00118d58c72840e48ef4e7d12`.

---

## RMD-200 — Rust/Android boundary and packaging

### RMD-201 — Decide generated UniFFI vs JNI and commit to one path

- [x] Record the chosen FFI architecture.
- [x] Remove or quarantine competing unused bridge code.
- [x] Ensure Android calls through the chosen bridge only.
- [x] Add a build check that fails if the generated bindings/native library are missing.

**Acceptance:** the Android APK packages and invokes the same Rust core used by CI tests.

**Evidence (RMD-201):** `docs/ANDROID_RUST_FFI_BUILD.md` records UniFFI as the chosen bridge and explicitly rejects a parallel manual JNI bridge. Android production gateway classes in `app/src/main/java/com/ekkus/offlineytplayer/coregateway/` load and invoke generated `com.ekkus.offlineytplayer.core.*` UniFFI classes reflectively; no production Java/Kotlin JNI bridge remains. `tools/uniffi-bindgen` generates Kotlin bindings from `core/src/offline_yt_core.udl`; the Android Gradle source set includes `build/generated/uniffi/kotlin`; and `app/src/main/jniLibs/.gitkeep` plus generated JNI library checks keep the package structure explicit. `.github/workflows/ci.yml` regenerates UniFFI bindings, builds Android Rust libraries for `arm64-v8a` and `x86_64`, asserts generated bindings are current, asserts both packaged native libraries are nonempty ELF shared objects, and runs Android lint/JVM/package checks against those artifacts. Qualified/merged evidence: PR #302 exact head `fb9ba59a83a3925856a03c180ac49793bff8c531` passed PR CI `35738447571` and push CI `35738283756`; PR #302 merged as `44432905ca628a4278d18c48d91cf5f6910825ab`; exact post-merge master `13d04880e38ea63543df4dc551f781a02730eb5d` passed CI `35744352571`.

### RMD-202 — Stabilize generated bindings in CI

- [x] Add a deterministic binding generation command/script.
- [x] Make CI run generation and fail on dirty generated output if checked in.
- [x] Verify Android source sets use generated bindings rather than handwritten placeholder APIs.
- [x] Verify native libraries are packaged for target ABIs.

**Acceptance:** deleting generated/native artifacts causes CI or build to fail clearly.

**Evidence (RMD-202):** `docs/ANDROID_RUST_FFI_BUILD.md` defines deterministic binding generation with `cargo run --manifest-path tools/uniffi-bindgen/Cargo.toml -- core/src/offline_yt_core.udl app/build/generated/uniffi/kotlin`, and `.github/workflows/ci.yml` runs that command then fails on dirty generated output via `git diff --exit-code app/build/generated/uniffi/kotlin`. The Android Gradle configuration includes only `build/generated/uniffi/kotlin` plus normal Kotlin sources, and `GeneratedUniffiCoreGatewayTest`/`GeneratedUniffiSourceAnalysisGatewayTest` assert the runtime gateways reference generated UniFFI service classes rather than handwritten placeholders. The CI Android job builds `liboffline_yt_core.so` for `arm64-v8a` and `x86_64`, copies them into `app/src/main/jniLibs`, builds the APK, then checks both ABI libraries are present, non-empty ELF shared objects. Qualified/merged evidence: PR #303 exact head `bcebe03f4837505471d5d955df8fd53dc5967055` passed PR CI `35746479855` and push CI `35746297414`; PR #303 merged as `fce2666e65a859bc43a5486e46fe47541c2a8360`; exact post-merge master `58e6a6fa8b16ce36fc11b45da8812560dbfe7c4f` passed CI `35752089677`.

### RMD-203 — Production Android core gateway

- [x] Open a real core/database handle from Android production startup.
- [x] Route source analysis/download/library/playback operations through generated FFI.
- [x] Convert Rust result/error types into Android models without panics.
- [x] Keep long-running core work off main thread.
- [x] Add Android JVM tests for gateway behavior using generated bindings or a fake generated service.

**Acceptance:** Android production code can call the packaged Rust core for source analysis and library state without blocking the main thread.

**Evidence (RMD-203):** `MainActivity.bootstrapProductionUi()` now opens production gateway handles against the app database path and stores them on the activity lifecycle. Source analysis, library snapshots, download queue snapshots, playback lookup, playback position, library rename/remove/detail, and startup reconciliation route through generated UniFFI gateway classes in `app/src/main/java/com/ekkus/offlineytplayer/coregateway/`; those gateways convert generated Rust records/errors to Android models and call `checkNotMainThread()` before FFI entry. App-level state refresh uses `AppStateRefresher` on an executor, and source analysis uses `Dispatchers.IO`; `CoreCallDispatcher` provides the shared off-main dispatcher for gateway calls. JVM coverage includes `GeneratedUniffiCoreGatewayTest`, `GeneratedUniffiSourceAnalysisGatewayTest`, `GeneratedUniffiPlaybackGatewayTest`, `CoreCallDispatcherTest`, `BootstrapTest`, `StartupReconciliationProductionPathTest`, `AppStateRefresherTest`, `LibraryActionGatewayTest`, and `LibraryItemDetailsGatewayTest`. Qualified/merged evidence: PR #304 exact head `7fa97af75f2b2facf03c418fd4ed71f018143c3d` passed PR CI `35754327543` and push CI `35753982019`; PR #304 merged as `9bdf0fd11602c79fc99628660e3249e8d5956e37`; post-merge master CI `35757158245` passed on exact merge SHA `9bdf0fd11602c79fc99628660e3249e8d5956e37`.

### RMD-204 — Native packaging/runtime smoke

- [x] Add an Android instrumentation smoke test that loads the native library.
- [x] Call a trivial deterministic core function.
- [x] Run it on at least one emulator/device API level in CI or a documented equivalent.
- [x] Record required ABI/API coverage.

**Acceptance:** a representative Android runtime proves the packaged native core can load and answer.

**Evidence (RMD-204):** `app/src/androidTest/java/com/ekkus/offlineytplayer/AndroidRuntimeSmokeTest.kt` loads the generated UniFFI native core through `FfiCoreService.open(...)`, calls `librarySnapshot()`, and asserts a successful deterministic empty snapshot. `.github/workflows/android-smoke.yml` builds packaged Rust libraries, regenerates bindings, assembles debug + androidTest APKs, and runs the smoke on API 29 x86_64 with a pinned Linux emulator lane; `README.md` and `docs/ANDROID_RUST_FFI_BUILD.md` record the ABI/API coverage. Qualified/merged evidence: PR #320 exact head `4ed72db3ae552db482ab68c598509c36662926ff` passed PR CI `35792894805`, PR Android smoke `35792894793`, push CI `35792870789`, and push Android smoke `35792870848`; PR #320 merged as `297f1d43cb1d63d39249613ce8674785e7762458`; post-merge master CI `35794770298` and Android smoke `35794770234` passed on exact merge SHA `297f1d43cb1d63d39249613ce8674785e7762458`.

---

## RMD-300 — Real YouTube/source provider path

### RMD-301 — Register a production source provider

- [x] Add a production `SourceProvider` registry.
- [x] Route supported URLs to a YouTube-capable provider or documented provider adapter.
- [x] Reject unsupported URLs with structured user-visible errors.
- [x] Remove any UI flow that marks arbitrary URLs as successfully resolved.

### RMD-302 — Replace fake metadata with provider output

- [x] Return real provider title, duration, thumbnail, formats, subtitles when available.
- [x] Bound and sanitize provider strings.
- [x] Handle unavailable/private/region/source-changed cases.
- [x] Persist provider/source identity.

### RMD-303 — Build deterministic provider fixtures

- [x] Fixture response for supported URL with combined A/V.
- [x] Fixture response for separate video/audio.
- [x] Fixture response for unavailable/private/source-changed.
- [x] Fixture response for malformed/oversized metadata.
- [x] Tests exercise the production adapter using those fixtures.

### RMD-304 — Isolate live-provider qualification

- [x] Add opt-in/manual or CI-secret-gated live-source smoke.
- [x] Keep live-source smoke out of normal PR gates unless enabled.
- [x] Document YouTube/provider terms and release approval gate.
- [x] Do not claim release approval from fixture tests.

**Acceptance:** Add/Share analysis succeeds for a supported real URL; fixture-only resolution is insufficient.

**Evidence (RMD-300):** RMD-301 through RMD-304 are implemented and qualified. RMD-303 deterministic production-adapter coverage is in `core/tests/fixtures/youtube/` and `core/src/youtube_source.rs`: bounded sanitized combined-A/V, split-A/V, unavailable/private/source-change, malformed, and oversized cases exercise metadata, formats, subtitles, fail-closed provider status, malformed extraction, and provider response-size enforcement. PR #367 merged as exact master `2dfc302b9073775f801d229dcfa663542eb72f3c`; post-merge CI `36077221939`, Android smoke `36077221904`, and API-35 FGS timeout `36077221881` all passed on that exact SHA. Live-provider proof remains isolated under RMD-304. Production registration and provider isolation are in `core/src/source.rs::SourceRegistry::production`, `core/src/youtube.rs`, `core/src/youtube_source.rs`, and `core/src/youtube_extract.rs`; Android consumes provider-neutral source results through `GeneratedUniffiSourceAnalysisGateway`. The production adapter resolves canonical source identity, bounded title/duration/thumbnail metadata, stream formats, provider-neutral download plans, and subtitles with bounded response/URL/metadata policies and structured diagnostics. Controlled live qualification is the ignored/manual `live_youtube_resolves_real_metadata_and_formats` test using only validated `OYP_LIVE_YOUTUBE_VIDEO_ID`; `docs/YOUTUBE_LIVE_QUALIFICATION.md` and `docs/YOUTUBE_POLICY_RELEASE_GATE.md` keep the policy/legal prerequisites explicit and the external release gate unresolved. Supporting reconciliation is `docs/RMD_300_YOUTUBE_SOURCE_RECONCILIATION_2026-09-20.md`, which records exact master `0f5efc619ec8c700dedeb897ab9bb659466804a3` passing CI `35507540263`. The same production code is present on later exact master `601654bf7bf063f014bb8ba5fe7fa368a77a00be`, which passed CI `36060145525`, Android smoke `36060145407`, and API-35 FGS timeout `36060145427`. RMD-300 acceptance is now satisfied by production registration plus deterministic provider fixtures and isolated live-source qualification.

---

## RMD-400 — Rust core correctness fixes

### RMD-401 — Make retryability authoritative

- [x] Refactor retry classification so `CoreError.retryable == false` cannot become retryable due only to `ErrorKind`.
- [x] Add regression test: HTTP 404/nonretryable status does not retry.
- [x] Add regression test: source-change/nonretryable provider failure does not enter generic retry loop.
- [x] Add regression tests for retryable transient statuses/network errors.

### RMD-402 — Unify retry policy with production scheduler

- [x] Make `DownloadPolicy.max_attempts` authoritative in production or remove it in favor of one authoritative retry policy.
- [x] Persist attempt count/next eligible retry time.
- [x] Ensure process death preserves retry semantics.
- [x] Bound exponential backoff and jitter.
- [x] Inject/abstract clock/randomness where needed for deterministic tests.

### RMD-403 — Separate network-read I/O from filesystem I/O

- [x] Do not send remote response-body read errors through the generic local `io_error` storage mapper.
- [x] Classify socket reset/timeout/truncation correctly.
- [x] Preserve retryability where appropriate.
- [x] Add fixture tests for mid-body disconnect and timeout.
- [x] Verify local ENOSPC/write failures still map to storage errors.

### RMD-404 — Make deletion remove owned assets

- [x] Define deletion transaction/state machine for metadata plus files.
- [x] Delete video/audio/thumbnail/subtitle/partial assets owned by the item.
- [x] Prevent traversal/out-of-root deletion.
- [x] Surface file-delete failures explicitly.
- [x] Add interrupted-deletion reconciliation.
- [x] Add tests proving files are gone after successful delete.

### RMD-405 — Use stored hashes for corruption detection

- [x] Preserve cheap existence/size checks where appropriate.
- [x] Add SHA-256 verification when a stored hash exists during explicit/deep validation or suspected corruption.
- [x] Add same-length corruption regression test.
- [x] Map corruption to a repairable/user-visible state.

### RMD-406 — Redact network diagnostics

- [x] Replace raw `reqwest::Error` user-facing text with structured safe diagnostics.
- [x] Strip/redact URLs, query strings, signed parameters, tokens, and sensitive filesystem details.
- [x] Add tests with synthetic signed URLs/secrets.
- [x] Verify logs and FFI error messages contain no injected secret markers.

### RMD-407 — Deterministic quality ranking

- [x] Rank formats by compatibility before deduplicating equal heights.
- [x] Prefer direct-play combined streams where product policy says so.
- [x] Prefer compatible split A/V over mux-required variants where appropriate.
- [x] Add deterministic tie-breakers for codec/bitrate/fps/format ID.
- [x] Add tests where provider order is intentionally adversarial.

### RMD-408 — Reconcile concurrency/resource-policy duplication

- [x] Identify conflicting core/Android concurrency constants.
- [x] Establish one maximum enforced by core and one user preference bounded by it.
- [x] Ensure Android cannot request a value above the core bound.
- [x] Add mapping tests.

**Evidence (RMD-400):** Current-master production behavior and deterministic regression coverage satisfy RMD-401 through RMD-408. RMD-401/RMD-402 use `core/src/retry.rs`, `core/src/worker.rs`, persistence, and `core/src/rmd_402_retry_policy_tests.rs` so explicit `CoreError.retryable` remains authoritative, `DownloadPolicy.max_attempts` is the bounded production policy, retry attempt/deadline state survives restart, and timing/jitter can be deterministic. RMD-403 uses `core/src/download.rs::remote_body_read_error` rather than local `io_error`, with disconnect/truncation/timeout coverage in `core/tests/download_disconnect_matrix.rs` and `core/tests/download_http_matrix.rs` while local write failures retain persistence/storage semantics. RMD-404 is `core/src/deletion.rs::delete_library_item_owned_assets`, which deletes owned media/thumbnail/subtitle/partial/resume assets before metadata, rejects traversal/symlink escape, surfaces delete failures, and remains safely retryable after partial deletion; its tests prove assets are gone after success and interrupted deletion is recoverable. RMD-405 is `core/src/asset_validation.rs`, preserving cheap existence/size checks while deep validation verifies stored SHA-256 and detects same-length corruption as explicit repairable `Missing`/`Corrupt` state. RMD-406 uses structured network diagnostics and `core/src/security.rs::redact_sensitive`, with synthetic secret/signed-URL tests in `core/tests/network_diagnostic_redaction.rs` and the FFI boundary. RMD-407 is `core/src/youtube_extract.rs::curate_quality_choices`, which ranks before equal-height deduplication and deterministically prefers direct-play combined, then compatible split, with bitrate/FPS/codec/container/format-ID tie-breakers and adversarial-order tests. RMD-408 centralizes the hard ceiling in `core/src/concurrency.rs::MAX_CONCURRENT_DOWNLOADS`, clamps platform/user preferences with `bounded_download_concurrency`, enforces the bound in the worker/gate, and exposes/tests the mapping through UniFFI and Android. Supporting reconciliation is `docs/RMD_400_CORE_CORRECTNESS_RECONCILIATION_2026-09-20.md` plus the later focused RMD-402/RMD-403/RMD-405/RMD-406/RMD-407/RMD-408 notes. PR #245 evidence head `39ad72bcbe51e60eeb7c19fbfd9e3c4121996de0` passed CI runs `35515774802` and `35516082761`; the same current production/test paths are present on exact master `601654bf7bf063f014bb8ba5fe7fa368a77a00be`, which passed CI `36060145525`, Android smoke `36060145407`, and API-35 FGS timeout `36060145427`.

---

## RMD-500 — Durable download orchestration

### RMD-501 — Define queue state as the source of truth

- [x] Expose durable queued/active/paused/waiting/retrying/failed/cancelled/completed states through the core gateway.
- [x] Ensure state survives process death.
- [x] Eliminate service-local booleans/constants as authoritative queue state.

### RMD-502 — Implement worker execution loop

- [x] Claim eligible durable work safely.
- [x] Enforce configured concurrency.
- [x] Execute the real core download plan.
- [x] Emit/persist progress at bounded cadence.
- [x] Commit completion only after integrity and asset promotion succeed.
- [x] Release/repair claimed work after cancellation/process death.

### RMD-503 — Implement Pause

- [x] UI action calls real control gateway.
- [x] Notification action calls same control path.
- [x] Worker reaches a bounded cancellation point.
- [x] Durable resumable state is persisted.
- [x] Partial asset is retained only according to resume policy.
- [x] Add behavioral tests.

### RMD-504 — Implement Resume

- [x] Resume transitions a paused item to eligible work.
- [x] Revalidate continuation metadata before range append.
- [x] Honor current network/settings policy.
- [x] Add process-death + resume regression test.

### RMD-505 — Implement Cancel

- [x] Cancel stops active work.
- [x] Remove/quarantine partial assets according to policy.
- [x] Persist terminal cancelled state.
- [x] Cancel from notification and UI uses same code path.

### RMD-506 — Implement Retry

- [x] Retry is available only for eligible failed states.
- [x] Do not create duplicate library/source identities.
- [x] Reset only appropriate attempt/error fields.
- [x] Honor maximum-attempt/user-action semantics.

### RMD-507 — Implement real progress/speed/ETA

- [x] Propagate transferred/total bytes.
- [x] Calculate speed from bounded recent samples.
- [x] Show ETA only when meaningful.
- [x] Never fabricate numeric progress for unknown-length responses.

**Evidence (RMD-501 through RMD-508):** Current `master` implements durable queue/control/source-of-truth behavior in `core/src/state.rs`, `core/src/ffi.rs`, `core/src/ffi_download_control.rs`, `core/src/worker.rs`, `core/src/worker_pause.rs`, `core/src/resume.rs`, `core/src/resume_http.rs`, `core/src/process_death_tests.rs`, and the Android control/scheduler/connectivity bridge under `app/src/main/java/com/ekkus/offlineytplayer/coregateway/`, `app/src/main/java/com/ekkus/offlineytplayer/downloads/`, and `app/src/main/java/com/ekkus/offlineytplayer/ui/DownloadRowPolicy.kt`. Behavioral coverage includes durable queue reopen/state exposure, worker claiming/concurrency/real transfer/promotion, retry wait/attempt deadlines, process-death reconstruction, durable pause propagation, cancel terminal-state persistence, explicit failed-only retry without duplicate identity, UI/notification control bindings, resume scheduler/network policy, Android connectivity mapping/gating, and RMD-508 device-side connectivity transition instrumentation. RMD-507 progress evidence remains the bounded real-byte/speed/ETA path through `core/src/events.rs::TransferMetricEstimator`, `DownloadWorker`, `DownloadProgressPresentationMapper`, and `DownloadRowPresentation`; unknown-length transfers still do not fabricate percentage or ETA. Supporting reconciliation is `docs/RMD_500_DURABLE_DOWNLOAD_ORCHESTRATION_RECONCILIATION_2026-09-25.md`. Qualified/merged exact-master evidence: `517ecccd2af481ca639921ab4ec0c4b36ccf2c81` passed master CI `36214435901`, master Android smoke `36214435877`, and master Android FGS timeout `36214435892`. Earlier focused progress evidence remains PR #354 (`72b2af7a85576250655eb10fdd08de48923fec46`) and PR #355 (`ebb66af299320c6d03d1de44a5efb5ba1352627e`) with the CI run IDs recorded in the prior RMD-507 evidence.

### RMD-508 — Connectivity integration

- [x] Observe Android network capability changes.
- [x] Map to core/app connectivity state.
- [x] Pause/wait when no usable network exists.
- [x] Enforce Wi-Fi/unmetered preference.
- [x] Automatically make waiting work eligible when constraints return.
- [x] Add instrumentation tests for transitions.

---

## RMD-600 — Production repositories and app state

### RMD-601 — Library repository wiring

- [ ] Replace `emptyList<LibraryRowModel>()` production data with repository-backed state.
- [ ] Implement list/search/detail observation.
- [ ] Map persisted metadata/assets to UI models.
- [ ] Provide loading/empty/error/populated states.

### RMD-602 — Download repository wiring

- [ ] Replace hard-coded empty Downloads data with durable queue state.
- [ ] Implement filters against actual states.
- [ ] Update rows from actual progress/events.

### RMD-603 — Source-analysis repository/use case

- [ ] Centralize URL validation + source registry resolution.
- [ ] Expose loading/resolved/unsupported/network/source-changed states.
- [ ] Cancel superseded analysis requests safely.

### RMD-604 — ViewModel architecture

- [ ] Add ViewModels for main feature screens or an equivalent lifecycle-aware state holder.
- [ ] Collect repository flows lifecycle-safely.
- [ ] Keep blocking FFI/network work off main thread.
- [ ] Restore relevant UI state across configuration/process recreation where appropriate.

---

## RMD-700 — Add and Share workflows

### RMD-701 — Make Paste operational

- [ ] Read bounded clipboard text.
- [ ] Put text into URL field.
- [ ] Handle missing/nontext clipboard gracefully.
- [ ] Add Compose/instrumentation coverage.

### RMD-702 — Make Analyze use the production source pipeline

- [ ] Remove fabricated preview metadata from production path.
- [ ] Validate via shared hardened policy.
- [ ] Call production source-analysis gateway.
- [ ] Show real metadata/quality/error state.

### RMD-703 — Make Download Setup operational

- [ ] Display real title/duration/thumbnail/source identity.
- [ ] Display actual curated quality options.
- [ ] Display estimated size only when known/derivable.
- [ ] Persist selected options.
- [ ] Download button schedules real durable work.

### RMD-704 — Make Advanced Options operational

- [ ] Populate actual subtitle languages/tracks.
- [ ] Populate audio choices where multiple tracks are supported.
- [ ] Limit container/format choices to real supported paths.
- [ ] Apply changes back to Download Setup state.

### RMD-705 — Make Android Share enter the same pipeline

- [ ] Bound incoming share text.
- [ ] Extract/validate supported URL consistently.
- [ ] Route to real Analyze/Setup state.
- [ ] Define and test back-stack behavior.
- [ ] Handle unsupported/multiple/no URL safely.

---

## RMD-800 — Thumbnail, subtitle, and metadata pipelines

### RMD-801 — Thumbnail lifecycle

- [x] Discover thumbnail URL through source adapter.
- [x] Download through bounded safe transfer path.
- [x] Store as managed local asset.
- [x] Persist asset metadata.
- [x] Render from local file in Library/Setup/Details while offline.
- [x] Delete thumbnail with owning item.
- [x] Detect/recover missing/corrupt thumbnail.

**Evidence (RMD-801):** source thumbnail discovery is represented on `MediaInfo.thumbnail_url` and provider-neutral thumbnail download assets are produced by `core/src/thumbnail.rs::thumbnail_download_asset`; bounded transfer/managed persistence is through `DownloadPlanAsset`, `DownloadWorker`, `LocalAsset`, and `LibraryStore` in `core/src/worker.rs` and `core/src/persistence.rs`; offline rendering uses persisted local thumbnail assets via `offline_thumbnail_asset`; owned deletion is covered by `core/src/deletion.rs`; missing/corrupt recovery is covered by `thumbnail_recovery_action` and deep validation in `core/src/asset_validation.rs`. Qualified/merged evidence: PR #260 (`fcbb4642f5bda0d1bdb8156f8e714536798f53b5`), PR #261 (`0bf076b426f4719024494d61d47ba314a7b5f803`), and PR #262 (`989db30e8ac48574d1b5d1a6d1115df0d72bdcb6`) with exact-head CI run `35558561926` and PR CI run `35559751692` passing.

### RMD-802 — Subtitle model and lifecycle

- [x] Persist language identity.
- [x] Persist subtitle format/MIME identity.
- [x] Download selected subtitle track as managed local asset.
- [x] Validate supported format.
- [x] Delete with owning item.
- [x] Attach to local playback.

**Evidence (RMD-802):** subtitle selection and managed download assets are implemented in `core/src/subtitle.rs` with supported VTT/SRT/TTML validation, safe relative paths, language/format/track identity, and local subtitle asset discovery; explicit persisted identity was added through `SubtitleAssetIdentity` and derived from persisted subtitle assets; owned subtitle deletion is exercised through `core/src/deletion.rs`; playback attachment descriptors for persisted local subtitles are in `core/src/offline_assets.rs`. Qualified/merged evidence: PR #263 (`829189e47fd9711a15be70eff05a3c0a0ec7cef1`), PR #264 (`18cf4027bacbf826f08a658d989abf8a3f58c8a0`) with push CI `35584389351` and PR CI `35584857816`, and PR #266 (`d605960b5ddc433366a6e29a300308ad2a62702f`) with push CI `35585593326` and PR CI `35587847797` passing on exact head `3efdf01208c60b8ad6940e93b52d58d8a3ca5c37`.

### RMD-803 — Metadata presentation

- [ ] Use persisted/resolved production metadata in all screens.
- [ ] Bound title/channel/description-like fields.
- [ ] Remove hard-coded/fake production metadata values.
- [ ] Test malformed/very-long metadata rendering.

---

## RMD-900 — Unified Media3 playback

### RMD-901 — Make `PlaybackSessionService` own the canonical player

- [ ] Move canonical ExoPlayer ownership to `PlaybackSessionService`.
- [ ] Create one `MediaSession` over that player.
- [ ] Configure audio focus/noisy handling on the canonical player.
- [ ] Ensure service lifecycle releases player/session correctly.

### RMD-902 — Connect Compose through `MediaController`

- [ ] Remove independent player construction from production `PlaybackScreen`.
- [ ] Connect/disconnect MediaController lifecycle-safely.
- [ ] Render controller/session state.
- [ ] Send play/pause/seek/speed actions through controller.

### RMD-903 — Implement local split A/V playback

- [ ] Use both `videoPath` and `audioPath` when an item has separate assets.
- [ ] Build the appropriate merged Media3 source.
- [ ] Keep all URIs local for completed offline items.
- [ ] Add deterministic split-A/V fixture playback test.

### RMD-904 — Implement subtitle playback controls

- [ ] Attach persisted local subtitle tracks.
- [ ] Expose actual available subtitle tracks in UI.
- [ ] Switch/disable subtitle selection through Media3 track APIs.
- [ ] Test offline subtitle rendering/selection where automation permits.

### RMD-905 — Implement audio-track controls if applicable

- [ ] Populate actual audio tracks when multiple are supported.
- [ ] Connect UI selection to Media3 track selection.
- [ ] Hide/disable control when only one track exists.

### RMD-906 — Playback position persistence

- [ ] Load persisted position before starting an item.
- [ ] Persist periodically at bounded cadence.
- [ ] Persist on appropriate stop/session transitions.
- [ ] Apply documented completion threshold/reset behavior.
- [ ] Add restart/resume tests.

### RMD-907 — MediaSession behavioral qualification

- [ ] Prove UI controls manipulate the session player.
- [ ] Prove MediaSession/controller commands manipulate the same player.
- [ ] Test headset/system play-pause/seek where emulator APIs permit.
- [ ] Prove current item/position agree between UI and session.

---

## RMD-1000 — Library and Downloads UX completion

### RMD-1001 — Operational Library screen

- [ ] Render real repository items.
- [ ] Search actual persisted records.
- [ ] Implement list/grid behavior if both remain advertised.
- [ ] Preserve selected layout setting.
- [ ] Provide empty/loading/error states.

### RMD-1002 — Library Play action

- [ ] Open canonical playback session for selected completed item.
- [ ] Reject/disable play for incomplete/corrupt items with explanation.

### RMD-1003 — Library Details action

- [ ] Add real details screen/sheet.
- [ ] Show local assets, source identity, duration, size, subtitle info, and integrity/recovery state as appropriate.

### RMD-1004 — Library Rename action

- [ ] Implement bounded rename in persistence.
- [ ] Decide whether rename changes display title only or filename; prefer metadata-only unless product requires file rename.
- [ ] Add validation/tests.

### RMD-1005 — Library Remove action

- [ ] Add destructive confirmation.
- [ ] Invoke real delete/asset lifecycle path.
- [ ] Update list only after durable outcome.
- [ ] Surface partial failure/recovery state.


### RMD-1006 — Operational Downloads screen

- [ ] Render durable queue.
- [ ] Apply real filters.
- [ ] Show actual progress/state/error/speed/ETA.
- [ ] Wire pause/resume/cancel/retry.
- [ ] Disable actions illegal for current state.

---

## RMD-1100 — Durable settings

### RMD-1101 — Add settings persistence

- [ ] Add DataStore or another documented durable settings store.
- [ ] Expose observable typed settings.
- [ ] Add migration/default strategy.
- [ ] Add persistence tests.

### RMD-1102 — Download settings

- [ ] Persist default quality.
- [ ] Persist network/Wi-Fi-only preference.
- [ ] Persist concurrency within core bounds.
- [ ] Persist subtitle default where applicable.
- [ ] Persist only retry controls that truly affect runtime.
- [ ] Prove runtime consumes each setting.

### RMD-1103 — Playback settings

- [ ] Persist supported defaults such as speed/resume behavior if retained in product spec.
- [ ] Apply to canonical playback session.
- [ ] Remove decorative settings with no runtime meaning.

### RMD-1104 — Storage settings

- [ ] Calculate actual managed-media usage.
- [ ] Show DB/partial/cache breakdown where useful.
- [ ] Implement safe cleanup actions.
- [ ] Confirm destructive cleanup.
- [ ] Add tests against temporary storage.

### RMD-1105 — Appearance settings

- [ ] Persist System/Light/Dark selection.
- [ ] Apply immediately to Compose theme.
- [ ] Persist Library layout preference if offered.

### RMD-1106 — About

- [ ] Replace hard-coded `0.1.0` with real version/build metadata.
- [ ] Show source revision when available.
- [ ] Link/render licenses/privacy/legal/support diagnostics as actually supported.

---

## RMD-1200 — Process death, reboot, and recovery

### RMD-1201 — Startup reconciliation is actually invoked

- [x] Call core startup reconciliation from an appropriate production initialization path.
- [x] Reconcile interrupted active transfers.
- [x] Reconcile staged/partial/orphan files.
- [x] Reconcile metadata/file mismatches.
- [x] Surface repairable failures.

**Evidence (RMD-1201):** production startup now invokes `GeneratedUniffiCoreGateway.reconcileStartup()` before the first Library/Downloads reads in `MainActivity.bootstrapProductionUi()`, using `FfiStartupReconciliationService` and `GeneratedUniffiCoreGateway` to expose the Rust startup reconciliation path off the Android main thread. Interrupted live-worker states are requeued by `reconcile_startup_downloads`; staged metadata without durable queue state, metadata/file mismatches, and safe orphan partial/resume cleanup are handled by `reconcile_startup_with_library_root`; repairable failures are surfaced as durable retryable/failed queue records and Android startup failure UI state rather than silently presenting stale state. Qualified/merged evidence: PR #295 merged as `7e3ecdf007675af021ffa4a190dd78d1bc6eeebc` with exact head `226f938f37b1f36fa853454e52506f985c30f8d4`, push CI `35717226706`, and PR CI `35717885355`; PR #296 merged as `b3249849618909e875b7e25f2b1e1c8e9baf415f` with exact head `f061a889f3c0427de149ea8badcb46f0cd1bbe98`, push CI `35723493720`, and PR CI `35724149169`.

### RMD-1202 — Process-death test

- [x] Start fixture download.
- [x] Kill/restart app or core service equivalent.
- [x] Verify durable queue and partial assets recover correctly.
- [x] Verify no duplicate/completed-false item appears.

**Evidence (RMD-1202):** `core/src/process_death_tests.rs::process_death_relaunch_reconstructs_queue_and_completes_one_fixture_item` starts from an interrupted durable `Downloading` snapshot with partial byte state, runs startup reconciliation through `FfiStartupReconciliationService`, verifies the same durable job identity is requeued instead of duplicated or marked complete, then runs `DownloadWorker` against a deterministic fixture to produce a single completed item. `StartupReconciliationProductionPathTest` proves Android production startup routes through `GeneratedUniffiCoreGateway.reconcileStartup`, and `app/src/test/java/com/ekkus/offlineytplayer/AppStateRefresherTest.kt` proves the app publishes durable library and download state after startup. Qualified/merged evidence: PR #295 merged as `7e3ecdf007675af021ffa4a190dd78d1bc6eeebc` with exact head `226f938f37b1f36fa853454e52506f985c30f8d4`, push CI `35717226706`, and PR CI `35717885355`; PR #296 merged as `b3249849618909e875b7e25f2b1e1c8e9baf415f` with exact head `f061a889f3c0427de149ea8badcb46f0cd1bbe98`, push CI `35723493720`, and PR CI `35724149169`.

### RMD-1203 — Reboot recovery qualification

- [x] Add boot-completed simulation or policy test.
- [x] Verify no illegal foreground-service launch.
- [x] Verify recovery scheduling/state reconciliation happens through legal path.

**Evidence (RMD-1203):** reboot handling is policy-only at boot and production reconciliation is deferred to legal app startup: `DownloadRebootReceiver` handles only `BOOT_COMPLETED`, does not launch a foreground service, does not open credential-protected storage, and records `MainActivity.bootstrapProductionUi -> GeneratedUniffiCoreGateway.reconcileStartup` as the recovery path. `DownloadBootRecoveryPolicyTest` and `DownloadRebootRecoveryPolicyTest` cover the boot-completed simulation, ignored broadcasts, no FGS launch, no pre-unlock credential storage access, and the legal deferred reconciliation path. Qualified/merged evidence is the same RMD-102 reboot recovery closeout: exact master `4a36792f866bc596b7470d5f0328205d75c7f898` passed CI `35822366166` and Android smoke `35822366114`, and exact master `99dfcb811b26c7f9d102ac87c646b88031fdc7dc` passed CI `35835796521` and Android smoke `35835796558`.

---

## RMD-1300 — Security/resource/policy hardening

### RMD-1301 — URL/input bounds

- [x] Bound URL length in Add and Share flows.
- [x] Reject non-http(s), javascript/data/file/content schemes.
- [x] Normalize consistently before provider dispatch.
- [x] Add tests for oversized and malicious inputs.

**Evidence (RMD-1301):** `app/src/main/java/com/ekkus/offlineytplayer/SupportedUrlPolicy.kt`, `ShareInput.kt`, and `ShareToDownload.kt` enforce bounded HTTP/HTTPS-only input for Add and Android Share entrypoints, rejecting oversized, non-web, javascript/data/file/content, ambiguous, and blank inputs before source analysis/provider dispatch. Rust provider-side URL enforcement remains in `core/src/youtube_source.rs` and `core/src/source.rs`. JVM/Rust coverage includes `SupportedUrlPolicyTest`, `ShareInputTest`, `ShareIntentPolicyTest`, `ShareToDownloadPolicyTest`, and provider URL validation/source tests. Supporting reconciliation is recorded in `docs/RMD_1301_URL_INPUT_BOUNDS_RECONCILIATION_2026-09-24.md`. Qualified/merged evidence: PR #343 exact head `643b42d0f820da9251109b80724deb3ce3b74d26` passed push CI `35929058324`, push Android smoke `35929058281`, PR CI `35929560109`, and PR Android smoke `35929560128`; PR #343 merged as `b17e78dd38dd8c352ef1c29d631c8e604214bbf2`; post-merge master CI `35931226109` and Android smoke `35931226120` passed on exact merge SHA `b17e78dd38dd8c352ef1c29d631c8e604214bbf2`.

### RMD-1302 — Filesystem/path hardening

- [x] Constrain all media writes to app/library root.
- [x] Reject traversal/symlink escape.
- [x] Validate MIME/container expectations where feasible.
- [x] Ensure delete/cleanup cannot remove non-owned paths.
- [x] Add adversarial path tests.

**Evidence (RMD-1302):** `core/src/download.rs::resolve_under_root` rejects absolute paths, traversal components, and prefix-escape writes before transfer; `core/src/asset_validation.rs::local_asset_path` and `core/src/offline_assets.rs::offline_*` keep persisted local asset reads rooted under the library root; `core/src/deletion.rs::remove_owned_file` canonicalizes the library root, rejects symlink/escape paths, and deletes only owned media/thumbnail/subtitle/partial/resume paths. Container/MIME expectations are enforced where current metadata supports them through `core/src/subtitle.rs::validate_subtitle_format` and persisted MIME/container fields on local assets. Adversarial coverage exists in `download_rejects_relative_path_traversal`, `rejects_absolute_and_prefix_escape_paths`, `delete_rejects_symlink_escape_and_leaves_metadata`, `deletes_only_paths_under_library_root`, `subtitle_download_asset_rejects_path_traversal`, and offline asset path tests. Supporting reconciliation is recorded in `docs/RMD_1302_FILESYSTEM_PATH_HARDENING_RECONCILIATION_2026-09-22.md`. Qualified/merged evidence: PR #309 exact head `297ff4419619c9b8be3727d36d8154d2e86fdd08` passed push CI `35764646601` and PR CI `35764697880`; PR #309 merged as `4cf8bc9cc23a9ede851dd4f7d01ef1f2c6b6a904` with post-merge master CI `35768674078`; PR #310 exact head `552dbdef4624eecaa00f5395f190f1fda0734130` passed push CI `35769004207` and PR CI `35769464430`; PR #310 merged as `05d9759b18cfad0170cb1e3536f26ad69661cad3` with post-merge master CI `35772641986`.

### RMD-1303 — Resource limits

- [x] Bound concurrent downloads.
- [x] Bound provider response sizes.
- [x] Bound database query/list sizes.
- [x] Bound thumbnail/subtitle downloads.
- [x] Add tests for large/unbounded inputs.

**Evidence (RMD-1303):** `core/src/concurrency.rs::MAX_CONCURRENT_DOWNLOADS` and `bounded_download_concurrency` bound worker concurrency; `core/src/youtube_source.rs::YoutubeSourceProvider::analyze` and `core/src/resource_bounds.rs::bounded_body` enforce bounded provider response sizes; `core/src/persistence.rs` applies `BoundedQueryLimit` to library/download snapshot/list queries; `core/src/thumbnail.rs::thumbnail_download_asset` and `core/src/subtitle.rs::subtitle_download_asset` enforce thumbnail/subtitle asset byte limits before managed download. Tests cover concurrency clamping and worker enforcement, oversized provider bodies, malformed/oversized metadata, bounded query limits, thumbnail/subtitle size limits, and large/unbounded input rejection. Supporting reconciliation is recorded in `docs/RMD_1303_RESOURCE_LIMITS_RECONCILIATION_2026-09-24.md`. Qualified/merged evidence: PR #346 exact head `9e8bbdc3d5a52192d9da51e15f7a275c608209d5` passed push CI `35943902933`, push Android smoke `35943902962`, PR CI `35944532336`, and PR Android smoke `35944532308`; PR #346 merged as `0e65055d38edac9106aa6e58304cd8a09dc4fcf1`; post-merge master CI `35946113775` and Android smoke `35946113763` passed on exact merge SHA `0e65055d38edac9106aa6e58304cd8a09dc4fcf1`.

### RMD-1304 — Safe diagnostics

- [x] Ensure no signed URLs/tokens/paths are shown in user-facing errors.
- [x] Ensure crash/log diagnostics are redacted.
- [x] Add tests for representative secrets.

**Evidence (RMD-1304):** `core/src/security.rs::redact_sensitive` redacts signed URL query parameters, bearer/token/password/API key markers, filesystem-looking paths, and other credential-bearing diagnostics. Rust provider/network diagnostics use structured errors via `core/src/youtube_diagnostics.rs`, `core/src/youtube_source.rs`, `core/src/source.rs`, and network/download error mapping rather than surfacing raw signed URLs or local filesystem details. Android gateway conversion (`CoreGatewayError`, source-analysis/download-control gateways) preserves the sanitized message and does not append raw inputs. Tests include `core/tests/network_diagnostic_redaction.rs`, provider/source redaction tests, download redaction coverage, and Android error-mapping tests for source-analysis and core gateways. Supporting reconciliation is recorded in `docs/RMD_1304_SAFE_DIAGNOSTICS_RECONCILIATION_2026-09-24.md`. Qualified/merged evidence: PR #347 exact head `49e37ac5dacb21b2c89235b3e6204be73ae40e26` passed push CI `35949894971`, push Android smoke `35949894953`, PR CI `35950600737`, and PR Android smoke `35950600726`; PR #347 merged as `45c6d5ab87f9ee753fc4b15f40ef854003a624f2`; post-merge master CI `35952351349` and Android smoke `35952351291` passed on exact merge SHA `45c6d5ab87f9ee753fc4b15f40ef854003a624f2`.

### RMD-1305 — Release/legal/service-policy gates

- [x] Document YouTube/service-policy approval as external.
- [x] Keep gate unchecked/not approved unless the human/legal decision exists.
- [x] Ensure UI/docs do not imply unauthorized release readiness.

**Evidence (RMD-1305):** `docs/YOUTUBE_POLICY_RELEASE_GATE.md` states that human service-policy/legal approval is external and unresolved, and `docs/YOUTUBE_LIVE_QUALIFICATION.md` keeps live-provider qualification opt-in/manual without release approval claims. `README.md`, `docs/APP_STORE_RELEASE_NOTES_DRAFT.md`, and `docs/PRIVACY_SECURITY_MODEL.md` state that source/provider policy approval remains pending and that fixture/manual engineering qualification does not authorize distribution. The detailed remediation TODO still keeps the external release gate separate through RMD-G08 and RMD-1601 final checklist language. Qualified/merged evidence: PR #348 exact head `927ca36f58ee92de1109752317995940ef8c07f0` passed push CI `35954961219`, push Android smoke `35954961335`, PR CI `35955564126`, and PR Android smoke `35955564084`; PR #348 merged as `0c14ec8bdd0c00b84bc1c6d53db86506f0ce66ae`; post-merge master CI `35957527747` and Android smoke `35957527715` passed on exact merge SHA `0c14ec8bdd0c00b84bc1c6d53db86506f0ce66ae`.

---

## RMD-1400 — Android UI functional and visual qualification

### RMD-1401 — Minimum Android smoke lane

- [x] Build debug APK with native core packaged.
- [x] Launch app on representative emulator/device.
- [x] Verify main navigation renders without crash.
- [x] Verify Add, Library, Downloads, Player, Settings tabs are reachable.
- [x] Archive logs/artifacts.

**Evidence (RMD-1401):** `.github/workflows/android-smoke.yml` assembles the debug and androidTest APKs with packaged Rust core for x86_64, runs the smoke lane on API 29, archives the instrumentation report/logcat/runner logs, and runs `AndroidRuntimeSmokeTest` plus `AppNavigationSmokeTest`. The runtime tests load the generated UniFFI native core, call the deterministic library snapshot path, launch `MainActivity`, and navigate Add, Library, Downloads, Player, and Settings without crashing. Qualified/merged evidence: PR #329 exact head `44b08f91a2144ac2a0737b3839864344f68b3489` passed CI `35849307712`, PR Android smoke `35849307703`, push CI `35849273829`, and push Android smoke `35849273781`; PR #329 merged as `c7af20ebd49b28c05bfd9dfbf5f6562b73543a43`; post-merge master CI `35850678931` and Android smoke `35850678917` passed on exact merge SHA `c7af20ebd49b28c05bfd9dfbf5f6562b73543a43`.

### RMD-1402 — Compose behavior tests

- [x] Add Compose UI tests for each primary screen.
- [x] Test visible primary actions.
- [x] Test no empty no-op action callbacks for required v1 controls.
- [x] Ensure tests use production Composables or thin test harnesses around them.

**Evidence (RMD-1402):** `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt` exercises production Composables or thin harnesses around them for the Add, Library, Downloads, Player, and Settings surfaces. It verifies primary actions are visible, core v1 controls are reachable, Add Analyze and Download Setup scheduling call real fake gateways rather than empty callbacks, Library play/remove callbacks fire, Downloads pause/resume/retry/cancel route through `AppDownloadControlGateway`, Player play/pause is backed by a controller harness instead of an empty no-op, and Settings primary actions are actionable. `ComposeBehaviorInstrumentationContractTest` keeps the production Compose behavior test tracked from the JVM lane, and `.github/workflows/android-smoke.yml` includes the behavior test in the device-side smoke class list. Supporting reconciliation is recorded in `docs/RMD_1402_COMPOSE_BEHAVIOR_RECONCILIATION_2026-09-25.md`. Qualified/merged evidence: PR #370 exact head `0b0628bfc1827c396379fbfb14aedebc880c506c` passed PR CI `36121230101`, PR Android smoke `36121230066`, PR Android FGS timeout `36121230079`, push CI `36119815961`, push Android smoke `36119815972`, and push Android FGS timeout `36119815984`; PR #370 merged as `1468b209695301e385307d119904cfd1cd777d4c4`; post-merge master CI `36123137800`, Android smoke `36123137761`, and Android FGS timeout `36123137791` passed on exact merge SHA `1468b209695301e385307d119904cfd1cd777d4c4`.

### RMD-1403 — Screenshot/golden tests

- [ ] Add actual image/golden infrastructure.
- [ ] Capture Library empty/populated.
- [ ] Capture Add invalid/resolved.
- [ ] Capture Download Setup.
- [ ] Capture Downloads active/failure.
- [ ] Capture Player.
- [ ] Capture Settings hub.
- [ ] Capture at least one smallest-supported portrait case.
- [ ] Capture representative large-font cases.
- [ ] Fail tests on unintended golden changes.

### RMD-1404 — Layout qualification

- [ ] Render each primary screen at compact supported dimensions.
- [ ] Assert primary actions are visible/reachable without horizontal scrolling.
- [ ] Allow bounded vertical content scrolling only where designed.
- [ ] Remove/replace existing all-boolean policy as the sole acceptance proof.

### RMD-1405 — Accessibility qualification

- [ ] Verify semantic labels on actionable icons/controls.
- [ ] Verify logical traversal/focus order.
- [ ] Verify minimum touch target behavior.
- [ ] Verify state is not communicated by color alone.
- [ ] Verify representative TalkBack semantics using Compose semantics tests and documented manual checks where automation is insufficient.
- [ ] Verify large text does not hide primary actions.

---

## RMD-1500 — Deterministic end-to-end qualification

### RMD-1501 — Add fixture download E2E

- [ ] Start from clean app state.
- [ ] Analyze deterministic fixture through the same app pipeline used by production.
- [ ] Select quality/options.
- [ ] Schedule through real background runtime abstraction.
- [ ] Download real fixture assets.
- [ ] Verify completed Library item.
- [ ] Disable network.
- [ ] Kill/cold-start app.
- [ ] Play completed local item through MediaSession-owned player.
- [ ] Assert no network request is needed for completed playback.

### RMD-1502 — Split A/V offline E2E

- [ ] Download separate local video/audio fixture assets.
- [ ] Persist both assets.
- [ ] Cold-start offline.
- [ ] Play synchronized merged A/V through canonical session.

### RMD-1503 — Subtitle offline E2E

- [ ] Download fixture subtitle track.
- [ ] Persist language/format.
- [ ] Cold-start offline.
- [ ] Select/display local subtitle track.

### RMD-1504 — Share-intent E2E

- [ ] Send `ACTION_SEND text/plain` fixture/supported input.
- [ ] Enter real analysis/setup pipeline.
- [ ] Schedule/download.
- [ ] Verify Library state.
- [ ] Verify back-stack behavior.

### RMD-1505 — Storage-failure E2E

- [ ] Exercise insufficient-space preflight.
- [ ] Exercise write failure/ENOSPC path where infrastructure permits.
- [ ] Verify partial cleanup/recoverability.
- [ ] Verify user-visible actionable failure.
- [ ] Verify no false completed library record.

### RMD-1506 — Network-loss/resume E2E

- [ ] Start transfer.
- [ ] Remove network.
- [ ] Verify waiting/pause state.
- [ ] Restore eligible network.
- [ ] Verify legal resume.
- [ ] Repeat with Wi-Fi-only/metered policy where emulator controls permit.

### RMD-1507 — Notification action E2E

- [ ] Pause from notification.
- [ ] Resume from notification.
- [ ] Cancel from notification.
- [ ] Verify durable state/UI mirrors each action.

---

## RMD-1600 — Final closeout and release-candidate audit

### RMD-1601 — CI workflow coverage

- [x] Ensure normal PR CI runs Rust tests.
- [x] Ensure normal PR CI runs Android JVM/lint/build checks.
- [x] Ensure required Android instrumentation lanes are documented and run before closeout.
- [x] Ensure final closeout requires the complete matrix, not a subset.
- [x] Screenshot/golden tests.
- [x] Deterministic E2E fixture lane.

**Evidence (RMD-1601):** `.github/workflows/ci.yml` now runs Rust fmt/clippy/tests, generated UniFFI binding consistency, Android Rust ABI builds, native-library packaging checks, Android lint/JVM/package checks, `python3 -m unittest tests/test_check_remediation_closeout.py`, and `scripts/check_remediation_closeout.py --allow-pending` for normal PR evidence. `.github/workflows/android-smoke.yml` runs the API-29 instrumentation smoke lane with runtime/native-load/navigation, RMD-1402 behavior, notification/permission, connectivity, golden/layout/accessibility, and fixture-E2E instrumentation classes; `.github/workflows/android-fgs-timeout.yml` keeps API-35 dataSync timeout qualification available. `.github/workflows/remediation-closeout.yml` requires an explicit closeout dispatch with `confirm_complete=true`, runs `scripts/check_remediation_closeout.py` without `--allow-pending`, and depends on the expanded CI checks plus the Android smoke and FGS timeout workflows for final exact-candidate qualification. The normal CI and closeout wiring now names the screenshot/golden and deterministic E2E lanes explicitly, but their RMD-1403 and RMD-1500 task checkboxes remain unchecked until their production tests and exact-head evidence are separately implemented and reconciled. Supporting evidence is `docs/RMD_1601_CI_WORKFLOW_COVERAGE_RECONCILIATION_2026-09-25.md`. Qualified/merged evidence: PR #375 exact head `dc18f27c01620cd7b3254b1c5bb8d8f9b15b08eb` passed PR CI `36212795172`, PR Android smoke `36212795181`, PR Android FGS timeout `36212795151`, push CI `36212099673`, push Android smoke `36212099622`, and push Android FGS timeout `36212099659`; PR #375 merged as `517ecccd2af481ca639921ab4ec0c4b36ccf2c81`; post-merge master CI `36214435901`, Android smoke `36214435877`, and Android FGS timeout `36214435892` passed on exact merge SHA `517ecccd2af481ca639921ab4ec0c4b36ccf2c81`.

### RMD-1602 — Dependency/license/advisory review

- [ ] Add Rust vulnerability/advisory scanning with an explicitly reviewed exception mechanism.
- [ ] Add Android/Gradle dependency vulnerability/license review tooling where practical.
- [ ] Generate/reconcile OSS license notices for shipped dependencies.
- [ ] Fail release qualification on unresolved prohibited/license-incompatible dependencies.

### RMD-1603 — Artifacts and reports

- [ ] Ensure failures preserve logs/test reports/screenshots.
- [ ] Ensure emulator/E2E artifacts are bounded and useful.
- [ ] Record exact candidate SHA in final qualification report.

---

## RMD-1700 — Documentation and user-facing truthfulness

### RMD-1701 — README/status

- [ ] Update project status to match actual implementation after remediation.
- [ ] Describe supported/unsupported workflows.
- [ ] Document build/run prerequisites.
- [ ] Keep external release gate explicit.

### RMD-1702 — Build/developer docs

- [ ] Document Android ABI build flow.
- [ ] Document UniFFI generation and Gradle integration.
- [ ] Document emulator/device setup.
- [ ] Document common native-loading failures.

### RMD-1703 — Runtime/background behavior docs

- [ ] Document API 34+ UIDT path.
- [ ] Document API 26-33 fallback.
- [ ] Document notification behavior.
- [ ] Document reboot/process-death recovery.
- [ ] Document Android 15+ restrictions relevant to this app.

### RMD-1704 — User guide

- [ ] Rewrite workflow steps against operational UI.
- [ ] Remove descriptions of controls that remain unimplemented/removed.
- [ ] Document offline playback guarantees and limitations.
- [ ] Document recovery/error states.

### RMD-1705 — Privacy/security model

- [ ] Update input/security model.
- [ ] Update diagnostic/redaction guarantees.
- [ ] Update storage/deletion behavior.
- [ ] Keep YouTube/service-policy/legal approval external and unresolved unless separately approved by a human authority.

### RMD-1706 — Historical audit correction

- [ ] Add a remediation reconciliation document explaining which prior audit claims were corrected.
- [ ] Do not delete historical audit docs; mark/supersede them clearly where their closeout claims are no longer authoritative.

---

## RMD-1800 — Independent final review

### RMD-1801 — Full TODO reconciliation

- [ ] Review every RMD task and subtask against current `master` code.
- [ ] For each completed milestone, cite implementation paths and behavioral tests.
- [ ] Confirm zero unchecked engineering subtasks except explicitly external release approval, which must not be represented as engineering-complete approval.
- [ ] Do not collapse this TODO.

### RMD-1802 — Independent code review

- [ ] Perform a new independent code review of Rust and Android production paths.
- [ ] Search for remaining production no-op callbacks.
- [ ] Search for hard-coded empty/fabricated production data.
- [ ] Search for policy-only tests being cited as behavioral proof.
- [ ] Search for dead/declarative capability flags that disagree with runtime behavior.
- [ ] Resolve newly found release-blocking issues.

### RMD-1803 — Final exact-head qualification

- [ ] Freeze candidate SHA.
- [ ] Run the complete required CI matrix on that exact SHA.
- [ ] Confirm Rust/core tests pass.
- [ ] Confirm Android JVM tests pass.
- [ ] Confirm Android instrumentation/Compose tests pass.
- [ ] Confirm screenshot/goldens pass.
- [ ] Confirm deterministic fixture E2E passes.
- [ ] Confirm packaging/UniFFI ABI checks pass.
- [ ] Confirm dependency/license/advisory gates pass.
- [ ] Record CI run IDs in a closeout audit.

### RMD-1804 — Merge and post-merge verification

- [ ] Merge only the qualified exact head through the configured repository policy.
- [ ] Reload this TODO from current `master` after merge.
- [ ] Verify the merge contains the qualified implementation.
- [ ] Run/observe required `master` CI.
- [ ] Record final `master` SHA and run IDs.

---

## Final release acceptance checklist

Engineering release candidate may be proposed only when all of the following are true:

- [ ] Android APK packages and calls the Rust core.
- [ ] Production source adapter resolves supported YouTube input.
- [ ] Add and Share flows use real resolution.
- [ ] Download Setup schedules real work.
- [ ] Durable queue drives Downloads UI and runtime.
- [ ] Pause/resume/cancel/retry are operational from UI and notification where applicable.
- [ ] Network policy is operational.
- [ ] Reboot/process-death recovery is platform-compliant.
- [ ] Library uses real persisted state and all item actions work.
- [ ] Thumbnail/subtitle/metadata assets work offline as specified.
- [ ] One MediaSession-owned player powers both UI and system controls.
- [ ] Split A/V works offline.
- [ ] Playback position persists/resumes correctly.
- [ ] Settings are durable and affect runtime.
- [ ] Retry/I/O/deletion/hash/redaction/quality-ranking review defects are fixed.
- [ ] Real Android instrumentation, accessibility/layout, goldens, and E2E tests exist and pass.
- [ ] Documentation matches reality.
- [ ] Exact-head and post-merge CI evidence is recorded.
- [ ] Detailed TODO remains preserved.
- [ ] External policy/legal release approval is still treated as a separate gate.
