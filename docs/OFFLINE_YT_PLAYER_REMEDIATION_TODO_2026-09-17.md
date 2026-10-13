
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

**Evidence (RMD-104):** `app/src/main/AndroidManifest.xml` retains one download `dataSync` service and one media playback service, with no retained media-processing foreground service; `DownloadForegroundServiceInventory` records the retained-service inventory. `DownloadForegroundService.onTimeout(startId, fgsType)` persists timeout state through `DownloadForegroundTimeoutStore` before `stopForeground(STOP_FOREGROUND_REMOVE)` and `stopSelf(startId)`. JVM coverage in `DownloadForegroundServiceTimeoutPolicyTest` verifies inventory, manifest declarations, and source-order persistence-before-stop behavior; `DownloadForegroundTimeoutPolicyTest` covers the timeout policy surface. `app/src/androidTest/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundTimeoutInstrumentedTest.kt` verifies packaged-app persistence of start id, foreground-service type, and repeated timeout count. Supporting reconciliation is recorded in `docs/RMD_104_FOREGROUND_TIMEOUT_RECONCILIATION_2026-09-23.md`. Qualified/merged evidence: PR #328 exact head `4a717dc7d24d934b62bfce15e1792b95a03954b8` passed PR CI `35834845438`, PR Android smoke `35834845359`, push CI `35834653394`, and push Android smoke `35834653338`; PR #328 merged as `99dfcb811b26c7f9d102ac87c646b88031fdc7dc`; post-merge master CI `35835796521` and Android smoke `35835796558` passed on that exact merge SHA. The API-35 shortened-timeout requirement is qualified by `.github/workflows/android-fgs-timeout.yml` and `DownloadForegroundTimeoutAdbInstrumentedTest.kt`, which set `device_config put activity_manager data_sync_fgs_timeout_duration 1000`, start the production `dataSync` foreground service on API 35, background the app, and verify the production `Service.onTimeout(...)` path persists timeout state before cleanup. Supporting evidence is `docs/RMD_104_API35_TIMEOUT_QUALIFICATION_2026-09-23.md`: PR #331 exact head `eb00501cb0d657da3ed26678f589cd492818d7c9` passed PR CI `35839348685`, PR Android smoke `35839348689`, and PR Android FGS-timeout `35839348784`, merged as `241ecfc65db2a58e375aee00a9fa9712e6fd3ee9`, and post-merge master CI `35846090097`, Android smoke `35846090151`, and Android FGS-timeout `35846090095` passed. The same API-35 lane also passed on later exact master `601654bf7bf063f014bb8ba5fe7fa368a77a00be` as run `36060145427`.

### RMD-105 — Notification permission behavior

- [x] Add Android 13+ notification-permission UX where required.
- [x] Define behavior when permission is denied.
- [x] Ensure denial cannot corrupt or silently misreport queue state.
- [x] Add instrumentation coverage for granted/denied state where feasible.

**Evidence (RMD-105):** `app/src/main/AndroidManifest.xml` declares `android.permission.POST_NOTIFICATIONS`; `MainActivity.kt` requests `Manifest.permission.POST_NOTIFICATIONS` via `ActivityResultContracts.RequestPermission` and records outcomes through `DownloadNotificationPermissionStateStore.recordGrantState`. `DownloadNotificationPermissionPolicy` gates runtime permission at SDK 33+, maps denial to `QueueStateOnly`, and preserves the invariant that denial does not mutate durable queue state or falsely complete work. `DownloadNotificationPermissionPolicyTest` covers SDK gating, denied-state behavior, and `MainActivity` request wiring. `DownloadNotificationPermissionInstrumentedTest` and `NotificationPermissionStateInstrumentationTest` verify packaged-app granted, denied, and cleared permission-state persistence. Supporting reconciliation is recorded in `docs/RMD_105_NOTIFICATION_PERMISSION_RECONCILIATION_2026-09-23.md`. Qualified/merged evidence: PR #327 exact head `cbaab677cbd0fea0b63fe57a0fb5efdbf7a459b9` passed PR CI `35833272325`, PR Android smoke `35833272315`, push CI `35833267719`, and push Android smoke `35833267706`; PR #327 merged as `192d9f6f569a7483a44c88a132c24a2a32fa1c3c`. PR #328 retained the RMD-105 smoke coverage and merged as `99dfcb811b26c7f9d102ac87c646b88031fdc7dc`, with post-merge master CI `35835796521` and Android smoke `35835796558` passing on that exact SHA. Broader Android 13+ dialog-level behavior remains covered by later RMD-1400/RMD-1500 behavioral qualification rather than blocking this platform-policy item.

---

## RMD-200 — Real Rust/Android UniFFI integration

### RMD-201 — Package Rust native libraries into the APK

- [x] Define supported Android ABIs for v1.
- [x] Build Rust `cdylib` for each supported ABI.
- [x] Copy/package `.so` files through Gradle/JNI libs or an equivalent deterministic mechanism.
- [x] Verify packaged APK contains each required native library.
- [x] Fail CI when an expected ABI library is absent.

### RMD-202 — Compile generated UniFFI Kotlin bindings into the app

- [x] Make binding generation reproducible from the Rust interface.
- [x] Add generated sources to the Android compile source set or consume them from a generated module/artifact.
- [x] Prevent stale checked/generated bindings from silently diverging.
- [x] Add CI diff/consistency verification.

### RMD-203 — Create an app-owned core gateway

- [x] Add a stable Kotlin interface wrapping generated UniFFI services.
- [x] Centralize FFI model/error conversion.
- [x] Ensure blocking calls execute off the Android main thread.
- [x] Define cancellation/lifecycle semantics.
- [x] Expose repository/state APIs suitable for ViewModels.
- [x] Provide a fake implementation for deterministic Android UI tests.

### RMD-204 — Android runtime FFI smoke test

- [x] Add at least one `androidTest` that loads the packaged native library.
- [x] Execute a real representative FFI call.
- [x] Round-trip representative records/errors.
- [x] Exercise a temporary app-private DB/media root.

**Acceptance for RMD-200:** production Kotlin imports/calls the packaged generated core interface; FFI is no longer a separate CI-only artifact.

**Evidence (RMD-200):** RMD-201/RMD-202 packaging and binding-generation evidence is recorded in `docs/RMD_200_UNIFFI_CURRENT_MASTER_EVIDENCE_2026-09-24.md`: `app/build.gradle.kts` defines the v1 `arm64-v8a`/`aarch64-linux-android` and `x86_64`/`x86_64-linux-android` targets, packages generated JNI libraries, and wires generated UniFFI Kotlin into the Android source set; `.github/workflows/ci.yml` builds both Android Rust targets, verifies both packaged `liboffline_yt_core.so` paths, and runs reproducible binding consistency checks. RMD-204 runtime proof is `app/src/androidTest/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGatewaySmokeTest.kt`, which loads the packaged native library, opens a temporary app-private DB/root, executes representative generated-core calls, round-trips durable queue state and structured errors, and is included in the Android smoke workflow. PR #358 exact evidence head `0df83e1b9313f2e55486170a1c029e20c0cc4e86` passed push CI `35995849486`, push Android smoke `35995849463`, push Android FGS timeout `35995849548`, PR CI `35996192854`, PR Android smoke `35996192911`, and PR Android FGS timeout `35996192874`; it merged as `8b34e44a2645d161a629e1bf972dc7554324883b`, whose post-merge master CI `36002691070`, Android smoke `36002691159`, and Android FGS timeout `36002691152` passed. RMD-203 app-owned gateway evidence is recorded in `docs/RMD_203_APP_CORE_GATEWAY_RECONCILIATION_2026-09-24.md`: stable app-owned core/control interfaces, centralized model/error conversion, off-main-thread execution, lifecycle/cancellation semantics, repository/state APIs, and deterministic fakes are all present in the production gateway layer. PR #359 exact head `8094b8051f33d8ca68bb099bf14edc6bff1d5784` passed all six push/PR CI, Android smoke, and Android FGS-timeout runs and merged as `010228734192069d0fbbfd7b906fd9220911cd97`; post-merge master CI `36017284912`, Android smoke `36017284906`, and Android FGS timeout `36017284973` passed. The final reconciliation intent was merged by PR #360 as `601654bf7bf063f014bb8ba5fe7fa368a77a00be`; post-merge master CI `36060145525`, Android smoke `36060145407`, and Android FGS timeout `36060145427` passed on that exact SHA.

---

## RMD-300 — Production YouTube source adapter

### RMD-301 — Implement the live production `MediaSource`

- [x] Add a concrete YouTube `MediaSource` distinct from `DirectFixtureSource`.
- [x] Register it in the production `SourceRegistry`.
- [x] Support the URL forms documented by the original strategy decision.
- [x] Resolve canonical source identity.
- [x] Resolve real title/duration/thumbnail metadata.
- [x] Discover available media formats.
- [x] Produce executable provider-neutral download plans.
- [x] Discover subtitles where supported.
- [x] Apply strict response/metadata bounds.

### RMD-302 — Keep provider logic isolated

- [x] Keep provider response types/parsers inside the YouTube adapter.
- [x] Do not expose provider-specific payloads through Android UI models.
- [x] Convert failures to structured source diagnostics.
- [x] Distinguish unsupported URL, source changed/parser failure, network failure, and unavailable media.

### RMD-303 — Add deterministic production-adapter fixtures

- [x] Store bounded sanitized fixtures for representative provider responses.
- [x] Test metadata extraction.
- [x] Test combined A/V formats.
- [x] Test separate A/V formats.
- [x] Test unavailable/private/changed-source responses.
- [x] Test malformed/oversized responses.

### RMD-304 — Controlled live-source qualification

- [x] Add an opt-in/manual or appropriately isolated live-source smoke path that is not required to leak secrets into CI.
- [x] Document its policy/legal prerequisites.
- [x] Record expected failure behavior when provider structure changes.

**Acceptance:** OYP-703 can only be considered repaired when the production registry can resolve a supported real URL; fixture-only resolution is insufficient.

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
- [x] **RMD-502a — Attach Android runtime launch points to executable durable work.** Persist enough provider-neutral download-plan data for a queued job to be reconstructed after process death, and make both API 34+ `DownloadUserInitiatedJobService` and API 26–33 `DownloadForegroundService` invoke the real core worker loop instead of only owning notification/lifecycle state. Add deterministic Android qualification proving a scheduled fixture job advances through the runtime into completed durable Library state.

**Evidence (RMD-502a):** `docs/RMD_502A_ANDROID_RUNTIME_RECONCILIATION_2026-09-30.md` records provider-neutral executable-plan persistence, both production Android runtime launch points invoking `DownloadWorkerExecutor`, and deterministic API 26–33/API 34+ qualification through completed durable queue/Library/local-asset state. Exact master `682e7dda55987046ce3bb1694a377d98b0c9dd3b` passed CI `36676333860`, Android smoke `36676333845`, Android FGS timeout/API-35 UIDT `36676333767`, Supply chain `36676333779`, CI evidence `36676333747`, and Deterministic E2E fixture `36676333873`. This does not close the broader RMD-1500 E2E requirements.


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

**Evidence (RMD-501 through RMD-506):** Durable download orchestration evidence is recorded in `docs/RMD_500_DURABLE_DOWNLOAD_RECONCILIATION_2026-09-26.md` and `docs/RMD_500_CANONICAL_TODO_RECONCILIATION_PLAN_2026-09-27.md`. `AppCoreGateway.kt` maps generated durable queue records to `CoreDownloadSnapshot` and `CoreDownloadState` values including `QUEUED`, `RESOLVING`, `DOWNLOADING`, `PAUSED`, `RETRY_WAIT`, `FAILED`, `VERIFYING`, `COMPLETED`, and `CANCELED`; `RETRY_WAIT` is the durable retry/wait state, while connectivity-constrained waiting is handled under RMD-508 by durable `PAUSED`. State survival is covered by file-backed `FfiDownloadControlService` tests and `DownloadWorker.repair_interrupted_claims_at`, with RMD-1202 proving process-death queue reconstruction. `core/src/worker.rs` implements `DownloadWorker.claim_eligible`, `execute_one`, `transfer_with_durable_stop`, `finish_failed_or_retry`, and `repair_interrupted_claims_at`, with tests for fixture transfer, library promotion, bounded concurrency, retry-wait behavior, interrupted-claim repair, durable pause handling, and progress metrics. UI Downloads actions are covered by `ProductionComposeBehaviorTest.downloads_actions_invoke_the_real_control_boundary`; notification actions route through `DownloadForegroundService` and `DownloadForegroundControlDispatcher` to the same `AppDownloadControlGateway`; pause/cancel reach bounded durable stop points in `DownloadWorker.transfer_with_durable_stop`; partial retention/removal follows `DownloadPolicy.retain_partial_on_cancel`; resume transitions paused work to eligible queued work through `FfiDownloadControlService.resume`; range append revalidates continuation metadata with `prepare_partial_reuse`, persisted validator identity, and `Content-Range`; and retry is legal only from failed state, reuses the same durable identity, and resets only appropriate attempt/error fields while automatic attempt limits remain governed by `DownloadPolicy.max_attempts` and `DownloadWorker.finish_failed_or_retry`. Qualified/merged evidence: PR #386 exact head `4e8e821647d09c45043e4fa486857ae16c6c463e` passed push CI `36265626581`, push Android smoke `36265626585`, push Android FGS timeout `36265626644`, push Supply chain `36265626503`, PR CI `36265649176`, PR Android smoke `36265649174`, PR Android FGS timeout `36265649173`, and PR Supply chain `36265649200`; PR #386 merged as `67de1246443626d71a4ad81327dc23c3640d33a4`, whose post-merge master CI `36303602321`, Android smoke `36303602425`, Android FGS timeout `36303602346`, and Supply chain `36303602301` passed. RMD-504's current network/settings-policy subtask is additionally satisfied by the RMD-508 production connectivity coordinator from PR #387, merged as `5e7e90154fb2c57f4533d9d1f8f024b6755b13ee`, whose post-merge master CI `36306527110`, Supply chain `36306527188`, Android FGS timeout `36306527158`, and Android smoke `36306527131` passed. Full transfer-level deterministic E2E remains separate under RMD-1500/RMD-1506 and is not closed by this RMD-500 reconciliation. Current-master audit on `64c8e315fde6af580693e168fb8760e2248afd86` found a remaining Android execution attachment gap: `DownloadUserInitiatedJobService.onStartJob()` finishes immediately after notification setup and `DownloadForegroundService.onStartCommand()` starts the foreground lifecycle without invoking `DownloadWorker`; `FfiDownloadControlService.enqueue()` persists queue identity/state but not an executable `DownloadPlan`. RMD-502a is therefore an explicit RMD-G07 blocker and must be closed before RMD-1501 or final engineering closeout. Current-master implementation has since added durable executable-plan storage (`core/src/durable_work.rs`), the generated-UniFFI `FfiDownloadWorkerService`, Android worker attachment for both UIDT and foreground-service launch paths, and a shared app-private `offline-yt-player.sqlite3` database path. Exact master `1bd9f57bbcaebbafcd0ae9d28c2c0effd637b147` passed CI `36515029910`, Android smoke `36515029912`, API-35 FGS timeout `36515029927`, Supply chain `36515029925`, CI evidence `36515029901`, and Deterministic E2E fixture `36515029999`. RMD-502a remains unchecked because scheduling-time creation/persistence of the executable `DownloadPlan` and deterministic Android proof that a scheduled fixture reaches completed durable Library state are still outstanding.

### RMD-507 — Implement real progress/speed/ETA

- [x] Propagate transferred/total bytes.
- [x] Calculate speed from bounded recent samples.
- [x] Show ETA only when meaningful.
- [x] Never fabricate numeric progress for unknown-length responses.

**Evidence (RMD-507):** `core/src/events.rs::TransferMetricEstimator` computes speed from a bounded recent sample window and emits ETA only when a meaningful total is known. `core/src/worker.rs::DownloadWorker` seeds the estimator from durable transferred/total byte state, updates it from real transfer results, emits bounded-cadence `CoreEvent::DownloadProgress` events, and preserves unknown-length transfers as unknown-total/unknown-ETA rather than fabricating percentage or ETA. Android presentation mapping preserves the invariant through `DownloadProgressPresentationMapper` and `DownloadRowPresentation`. Behavioral coverage includes `worker_emits_real_progress_bytes_speed_and_eta_for_known_length_transfer`, `worker_does_not_fabricate_eta_for_unknown_length_transfer`, `DownloadRowPolicyTest.unknownLengthProgressDoesNotFabricatePercentageOrEta`, and `DownloadRowPolicyTest.progressMapperKeepsOnlyRealPositiveMetrics`. Supporting implementation/evidence docs are `docs/RMD_507_PROGRESS_METRICS_IMPLEMENTATION_2026-09-24.md` and `docs/RMD_507_PROGRESS_METRICS_RECONCILIATION_2026-09-24.md`. Qualified/merged evidence: PR #354 exact implementation head `4b881b06d60540b2a8c7e15e3dfdb1cb63b54f63` passed push CI `35983598837`, push Android smoke `35983599037`, push Android FGS timeout `35983599025`, PR CI `35984323724`, PR Android smoke `35984323707`, and PR Android FGS timeout `35984323690`; PR #354 merged as `72b2af7a85576250655eb10fdd08de48923fec46`; post-merge master CI `35986175688`, Android smoke `35986175695`, and Android FGS timeout `35986175679` passed on that exact merge SHA. Evidence PR #355 exact head `58bd8c1fed59d7f6a6a0672b0e8055a6164691b0` passed PR CI `35986430735`, PR Android smoke `35986430765`, PR Android FGS timeout `35986430829`, push CI `35986393668`, push Android smoke `35986393598`, and push Android FGS timeout `35986393706`; PR #355 merged as `ebb66af299320c6d03d1de44a5efb5ba1352627e`, with post-merge master CI `35988142447`, Android smoke `35988142417`, and Android FGS timeout `35988142407` passing on that exact merge SHA.

**RMD-507 independent review follow-up (2026-10-11; full exact-head qualification pending):** the previous worker only observed completed `TransferResult` sizes, so single-asset downloads persisted no incremental bytes while the transfer was active even though bounded speed/ETA policy tests passed. The production correction adds `DownloadEngine::transfer_with_progress` callbacks after each actual body-chunk write in `core/src/download.rs`, and `DownloadWorker::execute_one` now publishes coalesced chunk progress via atomic compare-and-swap durable updates in `core/src/worker.rs`, so concurrent pause/cancel cannot be overwritten by stale progress. The final asset byte count is flushed before staging. Rust regression coverage: `progress_callback_reports_intermediate_written_bytes` and `worker_persists_chunk_progress_before_single_asset_completion`, alongside existing known- and unknown-length speed/ETA tests. The correction is on `master` at `e3089161c730f1d8d21037ae1b0dae312767c201`; its fast Rust CI qualified, while the full exact-head matrix and RMD-1802 independent-review signoff remain open. See `docs/RMD_1802_PROGRESS_STREAMING_REVIEW_2026-10-11.md`. This does not waive any RMD-1803 final frozen-head qualification.

### RMD-508 — Connectivity integration

- [x] Observe Android network capability changes.
- [x] Map to core/app connectivity state.
- [x] Pause/wait when no usable network exists.
- [x] Enforce Wi-Fi/unmetered preference.
- [x] Automatically make waiting work eligible when constraints return.
- [x] Add instrumentation tests for transitions.

**Evidence (RMD-508):** Android connectivity observation and queue policy are implemented by `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadConnectivityObserver.kt`. `AndroidDownloadConnectivityObserver` registers a default-network callback and emits normalized `DownloadConnectivity` states; `DownloadConnectivityMapper` maps Android `NetworkCapabilities` into `None`, `Metered`, and `Unmetered`; and `DownloadConnectivityCoordinator` applies `DownloadNetworkPolicy` plus the current download preference to the durable queue through the same `AppDownloadControlGateway` pause/resume methods used by UI and notification controls. Active `RESOLVING`, `DOWNLOADING`, and `VERIFYING` jobs are paused when connectivity is unusable or violates Wi-Fi-only policy, while durable `PAUSED` jobs become eligible again through resume when constraints return. JVM coverage is in `DownloadConnectivityCoordinatorTest` and existing network-policy tests; packaged Android framework mapping coverage is in `DownloadConnectivityObserverInstrumentedTest`, which is included in `.github/workflows/android-smoke.yml`. Qualified/merged evidence: PR #387 exact head `22fcaa4287f7739d17e075404b0eec1e1e697def` passed push CI `36304610292`, push Android smoke `36304610289`, push Android FGS timeout `36304610297`, push Supply chain `36304610324`, PR CI `36305482450`, PR Android smoke `36305482422`, PR Android FGS timeout `36305482427`, and PR Supply chain `36305482402`; PR #387 merged as `5e7e90154fb2c57f4533d9d1f8f024b6755b13ee`, whose post-merge master CI `36306527110`, Supply chain `36306527188`, Android FGS timeout `36306527158`, and Android smoke `36306527131` passed. RMD-1506 connectivity E2E remains separate and unchecked; RMD-508 is closed only for production connectivity observation, policy mapping, durable queue pause/resume integration, and JVM/instrumentation transition coverage.

---

## RMD-600 — Production repositories and app state

### RMD-601 — Library repository wiring



- [x] Replace `emptyList<LibraryRowModel>()` production data with repository-backed state.

- [x] Implement list/search/detail observation.
- [x] Map persisted metadata/assets to UI models.
- [x] Provide loading/empty/error/populated states.

### RMD-602 — Download repository wiring

- [x] Replace hard-coded empty Downloads data with durable queue state.
- [x] Implement filters against actual states.
- [x] Update rows from actual progress/events.

**Evidence (RMD-602):** Production Downloads state is repository-backed: `MainActivity.bootstrapProductionUi()` opens `GeneratedUniffiCoreGateway` against the app-private SQLite database and obtains the durable queue through `listDownloadQueue()`; `AppStateRefresher` refreshes that queue off the UI thread and republishes it into Compose state. `DownloadsScreen` applies `DownloadScreenPolicy.matchesFilter` to real mapped `DownloadUiState` values for All, Active, Paused, Failed, and Completed, and row presentation maps durable transferred/total bytes, state, bounded progress, speed/ETA, and sanitized errors without fabricating unknown-length progress. JVM coverage includes `DownloadsOperationalScreenTest` and `DownloadRowPolicyTest`. Detailed reconciliation is recorded in `docs/RMD_602_DOWNLOAD_REPOSITORY_RECONCILIATION_2026-09-27.md`. Exact evidence head `6adf218414e7fff4213aa1c75b64196888fbea85` passed CI runs `36313946270` / `36313962835`, Android smoke `36313946262` / `36313962935`, Android FGS timeout `36313946320` / `36313962832`, and Supply chain `36313946304` / `36313962756`. RMD-601, RMD-603, and RMD-604 remain fail-closed and unchecked.

### RMD-603 — Source-analysis repository/use case

- [x] Centralize URL validation + source registry resolution.
- [x] Expose loading/resolved/unsupported/network/source-changed states.
- [x] Cancel superseded analysis requests safely.

**Evidence (RMD-603):** `SourceAnalysisUseCase` centralizes `SupportedUrlPolicy` validation, delegates resolution through `AppSourceAnalysisGateway`, exposes explicit `Loading`, `Resolved`, `Unsupported`, `NetworkFailure`, `SourceChanged`, `Failed`, and `Superseded` states, and prevents stale/superseded tickets from publishing. Production `AddScreen` owns the use case, performs blocking analysis on `Dispatchers.IO`, cancels active work on input replacement/disposal, and ignores `Superseded` completion. `SourceAnalysisUseCaseTest` behaviorally covers pre-gateway validation, resolved source choices, structured error-state classification, and stale-result suppression. The production path and these tests were present on exact master `f62aa7aa5d72d3dccd87b9d4e50f60ec62ce7754`, which passed CI `37675821415`, Android smoke `37675821448`, Android FGS timeout `37675821387`, Supply chain `37675821421`, CI evidence `37675821384`, and Deterministic E2E fixture `37675821443`. RMD-604 remains separate and unchecked because lifecycle-aware screen state ownership/process recreation requires independent qualification.

### RMD-604 — ViewModel architecture

- [x] Add ViewModels for main feature screens or an equivalent lifecycle-aware state holder.
- [x] Collect repository flows lifecycle-safely.
- [x] Keep blocking FFI/network work off main thread.
- [x] Restore relevant UI state across configuration/process recreation where appropriate.

**Evidence (RMD-604):** `AppUiStateViewModel` owns lifecycle-retained production Library/Downloads Compose presentation state and the bounded Library search query; `MainActivity` obtains the holder through AndroidX `by viewModels()`. The query is persisted through `SavedStateHandle` and Activity instance state, and production durable Library/queue state is rehydrated from SQLite on process recreation instead of persisting transient presentation snapshots. `AppStateRefresher` starts/stops with Activity lifecycle, runs blocking gateway/FFI reads on a dedicated executor, suppresses stale lifecycle epochs and stale query results, and uses `LifecyclePublicationGate` to reject queued UI publications after stop/restart; `SourceAnalysisUseCase` performs blocking analysis on `Dispatchers.IO`. Behavioral coverage: `AppUiStateViewModelTest` (state ownership and SavedStateHandle rehydration), `AppStateRefresherTest` (off-calling-thread refresh, stop/restart, stale-query and stale-lifecycle suppression), `LifecyclePublicationGateTest`, `MainActivityQueryRecreationInstrumentedTest` (real Activity recreation with preserved visible search), and `LifecycleStateRestorationComposeTest` (Compose saved-state restoration). This proves the relevant state-restoration contract through unit plus real Activity recreation, not a literal OS process-kill E2E; RMD-1500 separately retains its explicit kill/cold-start acceptance. Exact implementation master `0f2c40e2e436735f6ef771ad7c96e111d2ff3eff` passed CI `37756514845`, Android smoke `37756514811`, Android FGS timeout `37756514765`, Supply chain `37756514854`, CI evidence `37756514812`, and Deterministic E2E fixture `37756514869`. No external policy/legal gate is implied.

---

## RMD-700 — Add and Share workflows

### RMD-701 — Make Paste operational

- [x] Read bounded clipboard text.
- [x] Put text into URL field.
- [x] Handle missing/nontext clipboard gracefully.
- [x] Add Compose/instrumentation coverage.

**Evidence (RMD-701):** the production Add-screen Paste action reads Android clipboard text, bounds it through `AddWorkflowPolicy.MaxClipboardChars`, places the bounded value into the URL field, and reports a defined missing/nontext status instead of failing. Behavioral Compose instrumentation in `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/AddPasteComposeTest.kt` drives `OfflineYTPlayerApp` through the real Paste button and proves both bounded insertion and graceful blank/missing-text handling. Exact master `08f4137f81af0d5796a5edcb84b98affc33d59f2` passed CI `36390464189`, Android smoke `36390464079`, Android FGS timeout `36390464063`, Supply chain `36390464029`, and CI evidence `36390464037`.

### RMD-702 — Make Analyze use the production source pipeline



- [x] Remove fabricated preview metadata from production path.
- [x] Validate via shared hardened policy.
- [x] Call production source-analysis gateway.
- [x] Show real metadata/quality/error state.


**Evidence (RMD-702):** production `MainActivity.bootstrapProductionUi()` opens `GeneratedUniffiSourceAnalysisGateway` and passes it into `OfflineYTPlayerApp`; `AddScreen` executes `gateway.analyze(requestUrl)` on `Dispatchers.IO`, cancels/suppresses superseded requests, and renders the returned source URL, bounded title, duration, thumbnail, quality choices, estimated bytes, and structured error state. `GeneratedUniffiSourceAnalysisGateway.analyze` normalizes input through the shared `SupportedUrlPolicy`, calls the generated `FfiYouTubeSourceService.resolve/listChoices` boundary, and maps provider output through `SourceMetadataPolicy` rather than fixture/preview data. JVM coverage includes the source-analysis gateway/policy tests and `AddWorkflowPolicyTest`; production consumption is guarded by source-shape/runtime-consumption tests. Qualified exact master `a9564e7eac154d0ba7383308bc949063fa33c792` passed CI `36387320305`, Android smoke `36387320281`, Android FGS timeout `36387320226`, Supply chain `36387320273`, and CI evidence `36387320255`. RMD-703/RMD-704 remain unchecked because setup option persistence/selection and advanced-option mutation are not yet complete.

### RMD-703 — Make Download Setup operational

- [x] Display real title/duration/thumbnail/source identity.
- [x] Display actual curated quality options.
- [x] Display estimated size only when known/derivable.
- [x] Persist selected options.
- [x] Download button schedules real durable work.

### RMD-704 — Make Advanced Options operational


- [x] Populate actual subtitle languages/tracks.
- [x] Populate audio choices where multiple tracks are supported.
- [x] Limit container/format choices to real supported paths.

- [x] Apply changes back to Download Setup state.


**Evidence (RMD-703/RMD-704):** production `AddScreen` builds `DownloadSetupState` exclusively from `CoreSourceAnalysis`: real title/duration/thumbnail/source identity, curated quality choices, source-reported subtitle/audio/container options, and nullable estimated sizes. `AdvancedDownloadOptions` edits the active setup state, limits audio selection to split-A/V choices, exposes source subtitle tracks, treats container as source-quality-derived rather than offering unsupported conversion, and applies the edited state back through `onApply(edited)`. The Download action passes selected quality/subtitle/audio IDs through `DownloadSelectionOptions` to the real `AppDownloadControlGateway.enqueue` path on `Dispatchers.IO`. `AddScreenPolicyTest` guards production use-case wiring, resolved setup presentation, selected-option propagation, scheduling, and advanced-option application; source option mapping is implemented in `GeneratedUniffiSourceAnalysisGateway`. These production paths/tests were present on exact master `f62aa7aa5d72d3dccd87b9d4e50f60ec62ce7754`, which passed CI `37675821415`, Android smoke `37675821448`, Android FGS timeout `37675821387`, Supply chain `37675821421`, CI evidence `37675821384`, and Deterministic E2E fixture `37675821443`.

### RMD-705 — Make Android Share enter the same pipeline

- [x] Bound incoming share text.
- [x] Extract/validate supported URL consistently.
- [x] Route to real Analyze/Setup state.
- [x] Define and test back-stack behavior.
- [x] Handle unsupported/multiple/no URL safely.

**Evidence (RMD-705):** `docs/RMD_705_SHARE_WORKFLOW_RECONCILIATION_2026-09-30.md` records bounded `ACTION_SEND text/plain` parsing through the shared supported-URL policy, routing accepted input into the normal Add → Analyze/Setup production path, back-stack qualification, and fail-closed handling for unsupported/ambiguous input. The implementation was present on exact master `682e7dda55987046ce3bb1694a377d98b0c9dd3b`, which passed the same six-workflow matrix recorded above. Full Share → runtime download → Library proof remains RMD-1504.


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

- [x] Use persisted/resolved production metadata in all screens.
- [x] Bound title/channel/description-like fields.
- [x] Remove hard-coded/fake production metadata values.
- [x] Test malformed/very-long metadata rendering.

**Evidence (RMD-803):** production metadata presentation is reconciled in `docs/RMD_803_METADATA_PRESENTATION_RECONCILIATION_2026-09-30.md`; durable resolved titles flow through the persisted download-presentation record, UniFFI worker service, `DownloadPresentationGateway`, and production `MainActivity`, while `MetadataPresentationPolicy` bounds user-visible metadata and JVM tests cover malformed/blank/oversized values. The implementation/fix chain was qualified on exact head `b51cb3ad839e6f235755f1789609e395fac16b0c`, and the current master lineage through `fecfdf48ccbe419caca9c20af18a8dbaef4bdc9d` passed CI `36771268234`, Android smoke `36771268149`, Android FGS timeout `36771268018`, Supply chain `36771268121`, CI evidence `36771268179`, and Deterministic E2E fixture `36771268071`.


---

## RMD-900 — Unified Media3 playback

### RMD-901 — Make `PlaybackSessionService` own the canonical player

- [x] Move canonical ExoPlayer ownership to `PlaybackSessionService`.
- [x] Create one `MediaSession` over that player.
- [x] Configure audio focus/noisy handling on the canonical player.
- [x] Ensure service lifecycle releases player/session correctly.

### RMD-902 — Connect Compose through `MediaController`

- [x] Remove independent player construction from production `PlaybackScreen`.
- [x] Connect/disconnect MediaController lifecycle-safely.
- [x] Render controller/session state.
- [x] Send play/pause/seek/speed actions through controller.

### RMD-903 — Implement local split A/V playback

- [x] Use both `videoPath` and `audioPath` when an item has separate assets.
- [x] Build the appropriate merged Media3 source.
- [x] Keep all URIs local for completed offline items.
- [x] Add deterministic split-A/V fixture playback test.

### RMD-904 — Implement subtitle playback controls

- [x] Attach persisted local subtitle tracks.
- [x] Expose actual available subtitle tracks in UI.

- [x] Switch/disable subtitle selection through Media3 track APIs.
- [x] Test offline subtitle rendering/selection where automation permits.

### RMD-905 — Implement audio-track controls if applicable


- [x] Populate actual audio tracks when multiple are supported.

- [x] Connect UI selection to Media3 track selection.
- [x] Hide/disable control when only one track exists.


### RMD-906 — Playback position persistence

- [x] Load persisted position before starting an item.

- [x] Persist periodically at bounded cadence.

- [x] Persist on appropriate stop/session transitions.
- [x] Apply documented completion threshold/reset behavior.
- [x] Add restart/resume tests.
- [x] **RMD-906a — Prove packaged unsigned playback-position UniFFI calls.** Android cold-start run `37913457733` reproduced a production crash: reflective lookup of `ffiSavePlaybackPosition` by an unmangled JVM name fails when generated Kotlin parameters include `ULong`. The production gateway now calls the typed binding directly (implementation `a8f741c01f92ac2bf201ad5ffaf763b1339ba00e`). Require a non-skipped packaged Android smoke regression, successful offline cold-start playback/position persistence, and passing exact-head CI before checking; source-text assertions alone are not qualification evidence.

**Evidence (RMD-901 through RMD-906):** `PlaybackSessionService` is the sole production `ExoPlayer` owner, builds one `MediaSession`, enables audio-focus handling and audio-becoming-noisy handling, and releases both player/session in service destruction. Production `PortraitPlayerScreen` constructs no `ExoPlayer`; it connects to the service through `SessionToken`/`MediaController.Builder`, binds `PlayerView` to that controller, routes seek/play-pause/speed and Media3 text/audio track selection through the controller, and releases/cancels the controller lifecycle-safely. `SplitAudioMediaSourceFactory` creates a `MergingMediaSource` when the local playback request carries a separate audio asset, while `LocalPlaybackPolicy` rejects remote playback URIs. Persisted subtitle/audio tracks are carried in `LocalPlaybackAsset`, exposed as real labels, selected through Media3 track parameters, and audio selection is disabled unless multiple tracks exist. Playback position is restored from persisted library state, periodically persisted at `PositionPersistCadenceMs`, persisted on disposal, and reset at the documented near-end completion threshold through `persistedPositionForStop`. `LocalPlaybackPolicyTest` covers split A/V, local-only paths, subtitle/audio track propagation, selection eligibility, restored position, bounded persistence cadence, and completion reset; `OfflinePlaybackQualificationTest` covers cold-start/local-only playback planning. These paths/tests were present on exact master `f62aa7aa5d72d3dccd87b9d4e50f60ec62ce7754`, which passed CI `37675821415`, Android smoke `37675821448`, Android FGS timeout `37675821387`, Supply chain `37675821421`, CI evidence `37675821384`, and Deterministic E2E fixture `37675821443`. RMD-907 remains unchecked because controller/system-command behavioral qualification is a distinct device-level acceptance boundary.

**RMD-906a packaged-runtime acceptance:** `GeneratedUniffiPlaybackPositionGateway` invokes typed unsigned `ffiSavePlaybackPosition`; `GeneratedUniffiCoreGatewaySmokeTest` in the packaged Android APK saves position `1234` for a **completed Library record**, then reopens it through the real generated core gateway and verifies the durable position. The separate host-driven RMD-1501 cold-start phase also verifies playable offline MediaSession state after force-stop and shutdown of the source fixture. JVM position cadence/reset/restart policy tests remain. Exact `master` SHA `a9044a1415239f84cef1a90412b9099aeab2d8c4` passed CI `37984663297`, Android smoke `37984663480`, Android FGS timeout `37984663324`, Supply chain `37984663294`, CI evidence `37984663332`, deterministic fixture `37984663412`, Android cold start `37984663429`, and Android real network E2E `37984663317`.

### RMD-907 — MediaSession behavioral qualification

- [x] Prove UI controls manipulate the session player.
- [x] Prove MediaSession/controller commands manipulate the same player.
- [x] Test headset/system play-pause/seek where emulator APIs permit.
- [x] Prove current item/position agree between UI and session.

**Evidence (RMD-907 complete):** `PlaybackSessionBehaviorInstrumentedTest` proves Compose `PortraitPlayerScreen` speed actions reach the service-owned canonical player, independent `MediaController`s observe and manipulate the same media item/playback state, and Android `AudioManager.dispatchMediaKeyEvent` play/pause/fast-forward/rewind commands reach that same session. `composeSeekControlsReachTheCanonicalSessionPosition` uses a real locally generated WAV fixture, waits for Media3 `STATE_READY`, verifies the Compose-displayed title agrees with `MediaController.currentMediaItem.mediaMetadata.title`, checks that Compose +10s/-10s controls change the observer controller's actual playback position, and verifies that an independent controller `seekTo(20_000L)` updates the displayed Compose position to `0:20` while the local media URI remains identical. `PortraitPlayerScreen` derives displayed title/position from its connected canonical `MediaController` and only shows timeline text after local media is ready. The media-key path was independently qualified on exact master `7475bc07afed86ee1e4d7846ca0bfd5401a3e303` with CI `37707460711`, Android smoke `37707460692`, Android FGS timeout `37707460738`, Supply chain `37707460662`, CI evidence `37707460660`, and Deterministic E2E fixture `37707460706`; see `docs/RMD_907_SYSTEM_MEDIA_KEY_QUALIFICATION_2026-10-07.md`. All four RMD-907 subtasks are retained on exact master `98214ce4b3cc95cf19eade0a758683d3065aecbb`, which passed CI `37713073950`, Android smoke `37713074029` (including the Compose seek/position and system-key instrumentation), Android FGS timeout `37713073960`, Supply chain `37713074047`, CI evidence `37713073925`, and Deterministic E2E fixture `37713073942`. This is device-side MediaSession behavioral qualification, not a substitute for the complete RMD-1500 app-to-worker-to-offline-playback E2E matrix.

---

## RMD-1000 — Library and Downloads UX completion

### RMD-1001 — Operational Library screen

- [x] Render real repository items.
- [x] Search actual persisted records.
- [x] Implement list/grid behavior if both remain advertised.

- [x] Preserve selected layout setting.
- [x] Provide empty/loading/error states.

**Evidence (RMD-1001):** production Library state is repository-backed through `MainActivity.bootstrapProductionUi()`, `GeneratedUniffiCoreGateway.listLibrary()`, and `AppStateRefresher`; persisted records are mapped to `LibraryRowModel`, and the active Library query is forwarded to the repository rather than filtering fabricated data. `LibraryScreen` consumes the durable `LibraryLayoutSetting` from the typed settings store for advertised list/grid presentation, and the setting persists across reopen. The production surface represents loading, failed, empty, and populated states, with behavioral Compose/golden/accessibility qualification under RMD-1402 through RMD-1405. Detailed reconciliation is `docs/RMD_1001_OPERATIONAL_LIBRARY_RECONCILIATION_2026-10-07.md`. The documentation/evidence head `516cb4ae03516b0a085ffb8ed06ae1b727cc6aae` passed CI `37680961218`, Android smoke `37680961234`, Android FGS timeout `37680961176`, Supply chain `37680961237`, CI evidence `37680961299`, and Deterministic E2E fixture `37680961326`. RMD-1002 through RMD-1005 remain separate action requirements.

### RMD-1002 — Library Play action

- [x] Open canonical playback session for selected completed item.
- [x] Reject/disable play for incomplete/corrupt items with explanation.
- [x] **RMD-1002a — Library Play begins canonical local playback.** Host-driven cold-start run `37915151203` showed that tapping Library `Play` launched `PortraitPlayerScreen`, but it called `MediaController.prepare()` without `play()`, leaving `playWhenReady=false`. Qualify the explicit production playback transition using `Rmd1501ProductionPipelineColdStartInstrumentedTest.verifyAfterHostForceStopPlaysPersistedLocalItemWithoutSourceNetwork` with network disabled, together with passing exact-head CI, before checking.

**Evidence (RMD-1002 partial):** `LibraryPlaybackRoute` now fail-closes invalid/incomplete/remote playback rows, exposes an unavailable reason, and the Library UI renders that explanation. JVM policy coverage and the intentionally changed Library goldens were qualified on current master `fecfdf48ccbe419caca9c20af18a8dbaef4bdc9d` with all six exact-head workflows green. The canonical playback launch requirement is now reconciled from current-master production wiring and exact-head qualification; see `docs/RMD_1002_1006_LIBRARY_DOWNLOADS_UX_RECONCILIATION_2026-10-07.md`.



**RMD-1002a accepted playback transition:** `PlaybackScreen.kt` explicitly invokes `MediaController.play()` after preparation, and `Rmd1501ProductionPipelineColdStartInstrumentedTest.verifyAfterHostForceStopPlaysPersistedLocalItemWithoutSourceNetwork` taps Library `Play` after OS force-stop/network shutdown and asserts `STATE_READY`, `playWhenReady=true`, and the persisted local media URI on the canonical MediaSession. Exact `master` SHA `a9044a1415239f84cef1a90412b9099aeab2d8c4` passed CI `37984663297`, Android smoke `37984663480`, Android FGS timeout `37984663324`, Supply chain `37984663294`, CI evidence `37984663332`, deterministic fixture `37984663412`, Android cold start `37984663429`, and Android real network E2E `37984663317`.

### RMD-1003 — Library Details action

- [x] Add real details screen/sheet.
- [x] Show local assets, source identity, duration, size, subtitle info, and integrity/recovery state as appropriate.

### RMD-1004 — Library Rename action

- [x] Implement bounded rename in persistence.
- [x] Decide whether rename changes display title only or filename; prefer metadata-only unless product requires file rename.
- [x] Add validation/tests.

### RMD-1005 — Library Remove action

- [x] Add destructive confirmation.
- [x] Invoke real delete/asset lifecycle path.
- [x] Update list only after durable outcome.
- [x] Surface partial failure/recovery state.


### RMD-1006 — Operational Downloads screen

- [x] Render durable queue.
- [x] Apply real filters.
- [x] Show actual progress/state/error/speed/ETA.
- [x] Wire pause/resume/cancel/retry.
- [x] Disable actions illegal for current state.

**Evidence (RMD-1002 through RMD-1006):** `docs/RMD_1002_1006_LIBRARY_DOWNLOADS_UX_RECONCILIATION_2026-10-07.md` records current-master production wiring and behavioral evidence for canonical Library playback launch, real details presentation, bounded metadata-only rename with validation, confirmed durable remove/delete lifecycle behavior, and the durable Downloads queue with real filters/progress/error/speed/ETA and state-legal pause/resume/cancel/retry controls. The reconciliation documentation head `467d7d56345e0a63814212fd2932cecd9f9aae67` passed all six exact-head workflows: CI `37688172137`, Android smoke `37688172353`, Android FGS timeout `37688171876`, Supply chain `37688172055`, CI evidence `37688171914`, and Deterministic E2E fixture `37688172625`. This reconciliation does not close RMD-907, RMD-1500, RMD-1800, or the external policy/legal release gate.

---

## RMD-1100 — Durable settings

### RMD-1101 — Add settings persistence

- [x] Add DataStore or another documented durable settings store.
- [x] Expose observable typed settings.
- [x] Add migration/default strategy.
- [x] Add persistence tests.

### RMD-1102 — Download settings

- [x] Persist default quality.
- [x] Persist network/Wi-Fi-only preference.
- [x] Persist concurrency within core bounds.
- [x] Persist subtitle default where applicable.
- [x] Persist only retry controls that truly affect runtime.
- [x] Prove runtime consumes each setting.

### RMD-1103 — Playback settings

- [x] Persist supported defaults such as speed/resume behavior if retained in product spec.
- [x] Apply to canonical playback session.
- [x] Remove decorative settings with no runtime meaning.

### RMD-1104 — Storage settings

- [x] Calculate actual managed-media usage.
- [x] Show DB/partial/cache breakdown where useful.
- [x] Implement safe cleanup actions.
- [x] Confirm destructive cleanup.
- [x] Add tests against temporary storage.

**RMD-1104 independent review follow-up (2026-10-09, qualified on exact master):** Managed storage usage/cleanup used `File.walkTopDown` and recursive cache deletion without checking nested symlinks, potentially traversing app-private symlinks into external paths, and ignored failed deletion return values while reporting bytes removed. `StorageSettingsManager` now skips symlink contents during accounting, unlinks instead of following links during cleanup, performs per-entry root checks, and propagates deletion failures. `StorageSettingsManagerTest` includes adversarial cache-directory and incomplete-asset symlink tests. The storage UI must surface bounded errors. Safe-cleanup acceptance is qualified by `StorageSettingsManagerTest` (cache/incomplete symlink targets, no traversal, unenumerable cache fail-closed) and `StorageSettingsPage` (confirmation plus bounded error reporting), on exact `master` `4d9700bfebd0c572950815f43422916b2eaa110c`: normal CI `38008449558` and API-29 Android smoke `38008449784` passed; FGS timeout `38008449592`, Supply chain `38008449534`, CI evidence `38008449672`, deterministic fixture `38008449801`, cold start `38008449583`, and real network E2E `38008449466` also passed. This closes only RMD-1104 safe cleanup; it does not qualify the distinct RMD-1506 host metered-network case.

### RMD-1105 — Appearance settings

- [x] Persist System/Light/Dark selection.
- [x] Apply immediately to Compose theme.
- [x] Persist Library layout preference if offered.

### RMD-1106 — About


- [x] Replace hard-coded `0.1.0` with real version/build metadata.
- [x] Show source revision when available.
- [x] Link/render licenses/privacy/legal/support diagnostics as actually supported.

**Evidence (RMD-1100 canonical reconciliation):** `docs/RMD_1100_DURABLE_SETTINGS_CANONICAL_RECONCILIATION_2026-09-30.md` records the completed RMD-1101 through RMD-1106 implementation and qualification. Supporting evidence is in `docs/RMD_1101_1103_SETTINGS_QUALIFICATION_2026-09-30.md`, `docs/RMD_1102_RUNTIME_CONCURRENCY_IMPLEMENTATION_2026-09-30.md`, `docs/RMD_1102_RUNTIME_SETTINGS_QUALIFICATION_2026-09-30.md`, `docs/RMD_1104_STORAGE_SETTINGS_RECONCILIATION_2026-09-30.md`, `docs/RMD_1105_APPEARANCE_SETTINGS_RECONCILIATION_2026-09-30.md`, and `docs/RMD_1106_ABOUT_RECONCILIATION_2026-09-30.md`. Exact master `ad2474d169508f085cfbc5e7d147fcbb8d31ef46` passed CI `36705782171`, Android smoke `36705782260`, Android FGS/API-35 UIDT `36705782190`, Supply chain `36705782245`, CI evidence `36705782364`, and Deterministic E2E `36705782156`.


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
- [x] Persist partial progress.
- [x] Kill process abruptly.
- [x] Relaunch.
- [x] Reconstruct durable queue.
- [x] Resume/restart according to validator policy.
- [x] Complete with correct integrity and one library item.

**Evidence (RMD-1202):** `core/src/process_death_tests.rs` adds a deterministic process-death/relaunch regression that starts fixture-backed work, persists an in-progress `Downloading` snapshot plus staged partial asset, drops/reopens the file-backed database to simulate relaunch, runs startup reconciliation, verifies the durable queue is reconstructed as retryable queued work, completes the fixture through `DownloadWorker`, validates final asset integrity and exactly one completed library item, and verifies the orphan partial cleanup path after completion. Qualified/merged evidence: PR #297 merged as `a91fcbac38ce52d85c8a11696048f16025c555c2` with exact implementation head `8824be5390bfdc4c79356b95b71ba35e8eb648d8`, push CI `35727329811`, and PR CI runs `35728138533` and `35731510001` passing.

### RMD-1203 — Boot-recovery test

- [x] Prepare durable interrupted work.
- [x] Simulate/reboot emulator where CI infrastructure supports it.
- [x] Verify receiver/reconciliation path.
- [x] Verify no forbidden `dataSync` FGS boot launch.
- [x] Verify work remains recoverable and is scheduled only when legal.

**Evidence (RMD-1203):** `DownloadBootRecovery` now models `BOOT_COMPLETED` as a legal deferred-startup reconciliation decision: it does not start `DownloadForegroundService`, does not open credential-protected state, preserves durable work for the same `GeneratedUniffiCoreGateway.reconcileStartup` path used by normal startup, and reports that work may only be scheduled when a legal scheduler path is available. `DownloadBootRecoveryPolicyTest` and `DownloadRebootRecoveryPolicyTest` verify the receiver policy, manifest shape, ignored non-boot broadcasts, no `LOCKED_BOOT_COMPLETED`, no direct `dataSync` foreground-service launch, no direct scheduler launch from boot, and the deferred recovery path. Deterministic process-death durable-work preparation and completion is covered by RMD-1202's file-backed recovery test; current CI does not include emulator reboot instrumentation, so RMD-1203's reboot simulation is the JVM receiver-policy path until RMD-1401 adds emulator infrastructure. Qualified implementation evidence: exact head `2852f47bf45e2f9627c104b9ef5b55093a8a1a47` passed push CI `35738352843`.

### RMD-1204 — Corrupt-state recovery UI

- [x] Convert corruption policy into real application state/actions.
- [x] Handle missing media.
- [x] Handle size/hash mismatch.
- [x] Handle unsupported/newer DB schema.
- [x] Handle damaged DB according to documented strategy.
- [x] Provide safe diagnostic/export/reset choices as applicable.

**Evidence (RMD-1204):** `CorruptionRecoveryPolicy` classifies real startup/core failure shapes into actionable recovery states for missing media, integrity size/hash failures, unsupported/newer schemas, damaged databases, interrupted migrations, and interrupted transfers. Production `LibraryScreen` renders those states through `RecoveryStatus` with concrete retry, diagnostics export, support, upgrade, and explicitly confirmed local-database reset actions; reset is offered only for damaged-database recovery and is never automatic. Behavioral policy coverage is in `CorruptionRecoveryPolicyTest`. Qualified implementation evidence: exact head `d4f75d09752a24d6af2007cb8f55b009b18d82c0` passed push CI `35746328575` and PR CI `35747141330`; PR #301 merged as `81e334925390997c3dd5da9fbc7a62353f29c8a8`.

---

## RMD-1300 — Security, privacy, and resource bounds


### RMD-1301 — Unify Android/core URL validation


- [x] Define one supported URL contract.
- [x] Align Share/Add validation with core source recognition.
- [x] Reject unsupported schemes/hosts/oversized inputs consistently.
- [x] Add adversarial tests.

**Evidence (RMD-1301):** production core recognition is defined by `recognize_youtube_video_url` / `SourceRegistry::production`; Android `SupportedUrlPolicy` is the fail-closed Share/Add mirror and `ShareInput` routes shared text through it. Both sides accept the same supported YouTube watch/short-link host forms and reject unsupported schemes/pages, spoofed or trailing-dot hosts, credential-bearing URLs, invalid video IDs, and oversized inputs before source resolution. Adversarial coverage is in Rust `youtube::tests` and Android `SupportedUrlPolicyTest`. Qualified implementation evidence: exact head `60b868a372d02b5847128a421256424d32437309` passed push CI `35759715267` and PR CI `35759961662`; PR #305 merged as `aa23e675f90ffda31131d9105ee2de37f1031b45`.

### RMD-1302 — Secret/log hygiene end to end

- [x] Audit Android logs.
- [x] Audit Rust logs/errors.
- [x] Audit notifications/user-visible diagnostics.
- [x] Inject synthetic tokens/signed query parameters in tests.

- [x] Assert they never appear in CI-visible outputs.

**Evidence (RMD-1302):** Rust/core diagnostic redaction is implemented in `core/src/security.rs` and covered by `core/tests/network_diagnostic_redaction.rs`; Android gateway/user-visible diagnostic sanitization is implemented through `app/src/main/java/com/ekkus/offlineytplayer/coregateway/SourceMetadataPolicy.kt` and related diagnostic paths, with synthetic signed URL/token marker tests to prove sensitive material is not emitted in user-visible diagnostics or CI-visible regression output. Qualified/merged evidence: PR #310 merged as `726beb739c9c01f5f8cedfb6bd58877dc7d9a12f` from exact implementation head `cefb16438c0b97f00e6c5445cb83771c70541db2`, with push CI `35784983375` passing; supporting reconciliation is recorded in `docs/RMD_1302_SECRET_LOG_HYGIENE_RECONCILIATION_2026-09-22.md`. The Android qualification acceleration plan was then merged through PR #311 as `c6766239ddd549b05283946bb2bddbd94db7683b` from exact documentation head `7a835f4f4aa5fb0738cc9adedd8005d16aa34e62`, with push CI `35787557579` and PR CI `35788158549` passing.

### RMD-1303 — File/path safety for all mutations

- [x] Apply safe-root/path validation to download, delete, rename, thumbnail, subtitle, cleanup, and recovery operations.
- [x] Add traversal/symlink/adversarial path tests appropriate to platform/filesystem semantics.

**Evidence (RMD-1303):** safe-root/path validation is enforced by `core/src/security.rs::validate_relative_library_path`, owned asset deletion in `core/src/deletion.rs`, safe thumbnail/subtitle path construction in `core/src/thumbnail.rs` and `core/src/subtitle.rs`, metadata-only rename in `core/src/ffi_library_rename.rs`, and final download/orphan-partial hardening in `core/src/download.rs` that canonicalizes library roots, rejects symlink escape paths, validates parent/final/partial paths before writes and promotion, and avoids symlink-following cleanup traversal. Traversal/symlink/adversarial coverage includes the path-safety audit plus Unix regression tests for transfer-parent and orphan-partial escape attempts. Qualified/merged evidence: PR #313 merged as `6024f8933e3040e50d9d1baeebdb6df16ef0b6ea` from exact head `2411bb1e8ffbb8e807778cb9d9491994074ebaf7`, with push CI `35791357376` and PR CI `35792116432` passing; PR #314 merged as `05d53e92cef91a34056abea3ccbc100ab86c561b` from exact implementation head `74f1e814ac893cc4a01642c245bb211bf11273ed`, with push CI `35793406251`, PR CI `35794146016`, and post-merge master CI `35794658300` passing. Supporting evidence is recorded in `docs/RMD_1303_PATH_SAFETY_AUDIT_2026-09-22.md` and `docs/RMD_1303_PATH_SAFETY_RECONCILIATION_2026-09-22.md`.

### RMD-1304 — Provider/resource bounds

**Independent review follow-up (2026-10-09, qualified exact-head Rust/normal CI):** the previous response-size assertion only rejected oversized declared `Content-Length` before reading, and rejected observed length *after* `Response::bytes()` had already buffered the entire untrusted body. Chunked/undeclared responses could therefore exhaust memory despite the declared 4 MiB limit. `core/src/youtube_source.rs::read_bounded_watch_body` now caps the actual stream at 4 MiB + 1 byte before parsing, with a deterministic infinite-reader regression test. The streamed-read fix is merged to `master` at `fbf20aa026bca559941ba5cb77391bfa2c73e42a` and qualified by exact-head normal CI `37997810191`, with follow-up exact-head normal CI `37998974551` on `727218b749b6dcb34d85ee96688674f02bfbb838` and `38000150931` on `f2556d75de59d9e92fd48b9af1ecf36bb7786e41` all passing. `core/src/youtube_source.rs::read_bounded_watch_body` bounds the actual untrusted read to `MAX_PROVIDER_RESPONSE_BYTES + 1` before parsing; `read_bounded_watch_body`'s infinite-reader and exact-limit tests prove undeclared/chunked bodies cannot cause unbounded buffering. The provider-response-size checkbox is reconciled from this merged implementation and exact-head Rust/normal CI evidence; it does not assert that the separately pending Android golden or metered-network gates passed. The prior RMD-1304 historical evidence remains valid for its other bounds, but not for the former unbounded allocation.

- [x] Bound provider response size.
- [x] Bound URL/metadata lengths.
- [x] Bound redirects/timeouts/asset sizes.
- [x] Bound concurrent downloads.
- [x] Bound retry attempts.
- [x] Add tests for each enforced limit.

**Evidence (RMD-1304):** resource bounds are centralized in `core/src/resource_bounds.rs` for provider response size, provider/media/caption/thumbnail URL lengths, metadata length, redirect count, and provider HTTP timeout policy. `core/src/youtube_source.rs` applies those bounds to production YouTube parsing/fetch behavior; `core/src/download.rs` enforces declared/observed transfer asset-size limits; `core/src/concurrency.rs` enforces `MAX_CONCURRENT_DOWNLOADS` and preference clamping; and `core/src/worker.rs` consumes `DownloadPolicy.max_attempts` as the bounded retry policy. Deterministic Rust tests cover provider response-size rejection, provider URL bounds, title truncation, redirect/timeout policy, stream/subtitle/thumbnail/metadata parsing limits, asset-size enforcement, concurrency ceilings, and retry-attempt limits. Qualified/merged evidence: PR #316 merged as `da8ea3050809a975cada87f8ad785e236783c94d` from exact implementation head `a21040f9745e3cc943178f7723b7d193e1f04ae7`, with push CI `35800979863`, PR CI `35801483348`, and post-merge master CI `35801968234` passing; supporting reconciliation is recorded in `docs/RMD_1304_RESOURCE_BOUNDS_RECONCILIATION_2026-09-22.md`.

---

### RMD-1305 — Explicit private-library backup exclusion (independent review)

- [x] Disable implicit Android app-data cloud backup, verify both the tracked manifest and packaged Android application flag, and document the privacy behavior and remaining device-migration limitations. Qualify via exact-head JVM and API-29 Android-smoke evidence before closing.

**Review finding (2026-10-09):** the prior manifest shipped `android:allowBackup="true"` without an established backup privacy policy, potentially allowing the OS to copy private SQLite state and downloaded assets outside the local-only media store. The manifest now disables automatic backup; `ManifestPermissionTest` checks the tracked declaration and `AndroidRuntimeSmokeTest` checks the packaged app flag. Qualified on exact `master` `4d9700bfebd0c572950815f43422916b2eaa110c`: `ManifestPermissionTest.privateLibraryIsExcludedFromImplicitAndroidCloudBackup` ran in normal CI `38008449558`, and packaged `AndroidRuntimeSmokeTest.packagedAppDoesNotPermitImplicitCloudBackupOfPrivateMedia` ran in API-29 Android smoke `38008449784`; both workflows passed. `docs/SECURITY_PRIVACY_LEGAL.md` documents that `android:allowBackup="false"` excludes implicit cloud backup of private media/SQLite/preferences but cannot guarantee every OEM-controlled device-to-device migration or user-initiated export. This is an engineering privacy control, not an external release approval.

---

## RMD-1400 — Real Android UI qualification

### RMD-1401 — Establish `androidTest` infrastructure

- [x] Add required AndroidX test/Compose test dependencies.
- [x] Create emulator-compatible instrumentation setup.
- [x] Ensure CI executes `connected...AndroidTest` or managed-device equivalent.
- [x] Upload useful failure artifacts/screenshots.
- [x] **RMD-1401a — Make instrumentation execution observable.** Require the Android smoke workflow to fail if generated-UniFFI gateway, production Share E2E, or real-worker connectivity instrumented classes did not execute non-skipped test cases; unit-test the report guard, and record exact-head Android smoke plus normal CI proof before checking this item. This prevents a green smoke workflow with absent/skipped required tests from qualifying production runtime behavior.
- [x] **RMD-1401b — Correct generated-UniFFI unsigned number mapping.** The newly exposed packaged Android gateway test fails because generated Kotlin `ULong` values are not `java.lang.Number`. Convert all numeric FFI record fields (including optional fields) without unsafe casts or silent unsigned overflow, cover the conversion in deterministic JVM tests, and require passing exact-head CI plus the packaged Android smoke path before checking this item. This is a release-blocking production boundary defect, not a test-only workaround.

- [x] **RMD-1401c — Preserve exact-SHA Android qualification across master updates.** Long-running Android smoke, cold-start, foreground-service timeout, and real-network E2E runs must not be cancelled solely because another `master` commit arrives. Key each lane's concurrency group by exact `github.sha`, retain duplicate-SHA cancellation, and enforce the configuration in normal CI. Before closing, verify a previously started exact-SHA Android run finishes after a newer master commit, without treating that older run as final closeout evidence for the newer head.

**RMD-1401c exact-SHA concurrency acceptance (2026-10-09):** `.github/workflows/android-smoke.yml`, `android-cold-start.yml`, `android-fgs-timeout.yml`, and `android-real-network-e2e.yml` now use lane-specific `${{ github.sha }}` concurrency groups (with same-SHA cancellation), enforced by `tests/test_android_workflow_concurrency.py` in the normal CI governance job. The Android cold-start run `38001588642` for older exact `master` SHA `cb1a969950ef7c15e910e46edd9059b6b4d2f669` was created at `2026-10-09T22:53:07Z`, and **completed successfully** at `2026-10-09T23:05:37Z`, despite the newer `master` SHA `d58be1daa37521c1c0db2eadfa4ae9d9b91c730a` being committed at `2026-10-09T22:53:58Z`. Exact newer-head CI `38001667673` passed, including the workflow-policy regression test. This proves an older Android run survives a newer `master` push; it is not substituted for newer-head Android closeout evidence.

**Evidence (RMD-1401):** Android instrumentation infrastructure is established by `app/src/androidTest/java/com/ekkus/offlineytplayer/AndroidRuntimeSmokeTest.kt`, Gradle packaging/build support for emulator-compatible `x86_64` native Rust libraries alongside `arm64-v8a`, fast-gate APK/native-library verification, and `.github/workflows/android-smoke.yml`. The smoke workflow runs `connectedDebugAndroidTest` on an API-29 AOSP x86_64 emulator scoped to `com.ekkus.offlineytplayer.AndroidRuntimeSmokeTest`, preserving bounded Gradle reports and logcat/artifact evidence on failure. This is intentionally the smallest runtime smoke lane required by the acceleration plan; behavioral Compose, golden, accessibility/layout, and deterministic E2E qualification remain open under RMD-1402 through RMD-1507 and final RMD-1803 closeout. Qualified/merged evidence: PR #318 merged as `751b1762861f795aed1245c1fe48b9e521019856` from exact implementation head `a0f3fd7970ce7dbe3cb697ac082f902ba4c2c5e9`, with PR CI `35809009062` and Android-smoke run `35809009026` passing on exact head, then post-merge master CI `35812405751` and post-merge Android-smoke run `35812405807` passing on merge commit `751b1762861f795aed1245c1fe48b9e521019856`.

**RMD-1401a/b executable/unsigned binding acceptance:** `scripts/run_required_android_smoke.sh` runs packaged generated-UniFFI, Share, connectivity mapping, and real-worker RMD-1506 classes separately and rejects missing/skipped XML evidence using `scripts/assert_android_smoke_execution.py`. Normal CI runs `tests/test_assert_android_smoke_execution.py` in its governance job. The typed unsigned `ULong/UInt/UShort/UByte` conversion with overflow checks is covered by `GeneratedNumericConversionTest`; the Android smoke tests exercise packaged Rust binding outputs, durable queue, and actual worker. Exact `master` SHA `a9044a1415239f84cef1a90412b9099aeab2d8c4` passed CI `37984663297`, Android smoke `37984663480`, Android FGS timeout `37984663324`, Supply chain `37984663294`, CI evidence `37984663332`, deterministic fixture `37984663412`, Android cold start `37984663429`, and Android real network E2E `37984663317`.

### RMD-1402 — Replace policy-only screen qualification with behavioral Compose tests

- [x] Library empty/populated behavior.
- [x] Add input/paste/analyze behavior.
- [x] Download Setup choices/actions.
- [x] Downloads state/actions.
- [x] Player controls.
- [x] Settings persistence/interaction.
- [x] Share navigation/back stack.


**Evidence (RMD-1402):** production Compose instrumentation coverage is in `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt`, with detailed reconciliation in `docs/RMD_1402_BEHAVIORAL_COMPOSE_RECONCILIATION_2026-09-25.md`. The behavior was delivered incrementally by PRs #371–#373 and is merged on master through exact SHA `c6a1b80d033285c2bb4ceb7209b589467ab454bf`. Post-merge exact-head master CI `36181189252`, Android smoke `36181189235`, and Android FGS-timeout `36181189239` all passed. The tests exercise Library empty/populated state, Add paste/analyze, Download Setup options/actions, Downloads actions, Player controls, Settings interactions, and Share navigation/back-stack behavior through production Compose surfaces and app-owned gateway boundaries.

### RMD-1403 — Deterministic screenshot/golden tests

- [x] Add actual image/golden infrastructure.
- [x] Capture Library empty/populated.
- [x] Capture Add invalid/resolved.
- [x] Capture Download Setup.
- [x] Capture Downloads active/failure.
- [x] Capture Player.
- [x] Capture Settings hub.
- [x] Capture at least one smallest-supported portrait case.


- [x] Capture representative large-font cases.
- [x] Fail tests on unintended golden changes.

- [x] **RMD-1403c — Eliminate CI-local golden-test source mutation.** Pin reviewed Download Setup hashes and the Compose idle barrier in tracked Kotlin source; convert the CI preparation script into a read-only validator; assert the source has no CI worktree diff; add host-side regression tests; verify every expected raster test method executed non-skipped (not merely that the suite started); remove outdated policy flags claiming bitmap goldens are deferred; qualify with exact-head normal CI and Android golden instrumentation before closing. Historical RMD-1403 green runs that patched Kotlin inside CI do not by themselves prove exact-head golden acceptance. **Exact-head acceptance (2026-10-09):** `ProductionComposeGoldenTest.kt` checks in reviewed Download Setup raster hashes and the post-Options `compose.waitForIdle()` barrier; `scripts/register_download_setup_golden_variant.py` is read-only with respect to Kotlin source; `tests/test_register_download_setup_golden_variant.py` guards missing hashes, missing barrier, and source immutability; `.github/workflows/android-smoke.yml` asserts `git diff --exit-code` and requires all 12 named golden raster test methods non-skipped using `scripts/assert_android_smoke_execution.py`. Obsolete bitmap-deferred policy flags are absent from `GoldenCoveragePolicy`/its JVM test. Exact `master` `4d9700bfebd0c572950815f43422916b2eaa110c` passed CI `38008449558` and Android smoke `38008449784`; the smoke job logged `ANDROID_SMOKE_REQUIRED_TESTS_OK count=12`. These are tracked-source, not CI-mutated, goldens.

### RMD-1404 — No-hidden-controls behavioral gate


- [x] Render each primary screen at compact supported dimensions.
- [x] Assert primary actions are visible/reachable without horizontal scrolling.
- [x] Allow bounded vertical content scrolling only where designed.

- [x] Remove/replace existing all-boolean policy as the sole acceptance proof.

### RMD-1405 — Accessibility qualification


- [x] Verify semantic labels on actionable icons/controls.
- [x] Verify logical traversal/focus order.

- [x] Verify minimum touch target behavior.
- [x] Verify state is not communicated by color alone.
- [x] Verify representative TalkBack semantics using Compose semantics tests and documented manual checks where automation is insufficient.
- [x] Verify large text does not hide primary actions.

**Evidence (RMD-1403 through RMD-1405):** `ProductionComposeGoldenTest.kt` provides production-Compose raster capture with pinned SHA-256 golden hashes for Library empty/populated, Add invalid/resolved, Download Setup, Downloads active/failure, Player, Settings, compact portrait, and representative large-font states. `ProductionComposeLayoutAccessibilityTest.kt` provides device-side compact-layout, reachability, vertical-scroll, semantics, traversal, minimum-touch-target, non-color-only state, and large-text assertions. `.github/workflows/android-smoke.yml` executes both suites and uploads bounded golden/instrumentation evidence. Detailed evidence is in `docs/RMD_1403_1405_UI_QUALIFICATION_RECONCILIATION_2026-09-25.md`. Implementation merged by PR #376 as `6d21f1db5e5a5444793aeb1ddf7eb62a3ed100bd`; post-merge CI `36219448765`, Android smoke `36219448785`, and Android FGS timeout `36219448766` passed. Later exact master `58f240c4dec8f699540f06552dcbfa6e8407d815` retains this implementation and passed CI `36235854097`, Android smoke `36235854115`, Android FGS timeout `36235854212`, and Supply chain `36235854130`.

---

## RMD-1500 — Real end-to-end qualification

### RMD-1501 — Offline fixture E2E

- [x] Start from clean app state.
- [x] Analyze deterministic fixture through the same app pipeline used by production.
- [x] Select quality/options.
- [x] Schedule through real background runtime abstraction.
- [x] Download real fixture assets.
- [x] Verify completed Library item.
- [x] Disable network.

- [x] Kill/cold-start app.
- [x] Play completed local item through MediaSession-owned player.
- [x] Assert no network request is needed for completed playback.

**RMD-1501 full deterministic pipeline acceptance:** `Rmd1501ProductionPipelineColdStartInstrumentedTest` resets app-private state; drives production Compose Add/Analyze/Download Setup, Options, selection away from and back to `Fixture WAV`, and Apply; verifies the actual chosen enqueue option; schedules via `AndroidDownloadExecutionScheduler` into the foreground service and generated Rust worker; confirms byte-for-byte fixture asset, completed durable queue and Library descriptor. Host CI then force-stops the app, disables Wi-Fi/data, enables airplane mode, and runs a new instrumentation process. It taps Library Play and verifies canonical MediaSession `STATE_READY`/`playWhenReady` from the local file after the HTTP fixture server has exited. Both RMD-1501 phases logged `OK (1 test)` in cold-start run `37984663429`. Exact `master` SHA `a9044a1415239f84cef1a90412b9099aeab2d8c4` passed CI `37984663297`, Android smoke `37984663480`, Android FGS timeout `37984663324`, Supply chain `37984663294`, CI evidence `37984663332`, deterministic fixture `37984663412`, Android cold start `37984663429`, and Android real network E2E `37984663317`. Live YouTube provider/service-policy approval remains separately gated.

### RMD-1502 — Split A/V offline E2E

- [x] Download separate local video/audio fixture assets.
- [x] Persist both assets.
- [x] Cold-start offline.
- [x] Play synchronized merged A/V through canonical session.

**RMD-1502 packaged split-A/V E2E increment (qualified partial path):** `Rmd1502SplitAvOfflineInstrumentedTest` uses deterministic four-second H.264 Baseline MP4 video-only and PCM WAV audio assets, schedules both through the production API-29 background scheduler and generated core worker, shuts down the fixture server after both assets persist, verifies the production Library playback descriptor retains distinct local video/audio paths, opens the item through production `MainActivity`, and verifies the canonical MediaSession reaches `STATE_READY` with both persisted local video and audio tracks selected while the source server is unavailable. Exact master `61ede07f770ed1283b3cf1b2fa514b544895ed70` passed CI `37777571178`, Android smoke `37777571126`, Android FGS timeout `37777571176`, Supply chain `37777571113`, CI evidence `37777571166`, and Deterministic E2E fixture `37777571246`. The download, persistence, and synchronized merged-offline-playback subtasks are qualified; true OS process-kill/cold-start remains open.

**RMD-1502 real process-death acceptance:** Host-driven `Rmd1502SplitAvOfflineInstrumentedTest` passed separate video-only MP4 + PCM WAV download/persistence and network-disabled verification after `adb am force-stop`. `LocalPlaybackPolicy` preserves the separate audio identity through the MediaSession-transmitted media ID; `SplitAudioMediaSourceFactory` merges the sources, and the restored controller observes selected AUDIO and VIDEO at `STATE_READY`. Both phases report `OK (1 test)` in `37984663429`. Exact `master` SHA `a9044a1415239f84cef1a90412b9099aeab2d8c4` passed CI `37984663297`, Android smoke `37984663480`, Android FGS timeout `37984663324`, Supply chain `37984663294`, CI evidence `37984663332`, deterministic fixture `37984663412`, Android cold start `37984663429`, and Android real network E2E `37984663317`.

### RMD-1503 — Subtitle offline E2E

- [x] Download fixture subtitle track.

- [x] Persist language/format.
- [x] Cold-start offline.
- [x] Select/display local subtitle track.

**RMD-1503 production playback-descriptor repair (pending exact-head qualification):** remediation review found that persisted subtitle assets retained provider-neutral identity in Rust (`subtitle:<language>:<track_id>` plus MIME) but `FfiLibraryPlaybackAsset` exported only video/audio paths, so production Android Library playback silently dropped subtitles before constructing `LocalPlaybackAsset`. The playback descriptor now exports validated local subtitle path/language/format/track/MIME records, Android maps them to app-private `LocalSubtitleTrack` entries, and `LibraryPlaybackRoute` carries them into the canonical player. Rust and JVM tests cover the descriptor and route. RMD-1503 remains unchecked until packaged Android offline download/reopen/player evidence qualifies the behavior.

**RMD-1503 packaged offline subtitle E2E increment (qualified partial path):** `Rmd1503SubtitleOfflineInstrumentedTest` schedules a two-asset WAV + WebVTT fixture through the production API-29 background scheduler and generated core worker, shuts down the only HTTP fixture server, reopens persisted Library/playback descriptors, verifies subtitle path/language/format/track/MIME identity, launches production `MainActivity`, opens the canonical MediaSession-owned player, verifies the local subtitle configuration is exposed offline, and verifies Media3 selects the persisted `en` text track before the UI disables it. Exact master `61ede07f770ed1283b3cf1b2fa514b544895ed70` passed CI `37777571178`, Android smoke `37777571126`, Android FGS timeout `37777571176`, Supply chain `37777571113`, CI evidence `37777571166`, and Deterministic E2E fixture `37777571246`. The subtitle download, persisted language/format, and local select/display subtasks are qualified; actual OS process-kill/cold-start evidence remains open.

**RMD-1503 real process-death acceptance:** `Rmd1503SubtitleOfflineInstrumentedTest` passed subtitle seed/persistence and host-driven force-stop/network-disabled verification in separate instrumentation processes. The cold-start production Library/MediaSession playback route selects the retained local English WebVTT text track; both phases report `OK (1 test)` in `37984663429`. Exact `master` SHA `a9044a1415239f84cef1a90412b9099aeab2d8c4` passed CI `37984663297`, Android smoke `37984663480`, Android FGS timeout `37984663324`, Supply chain `37984663294`, CI evidence `37984663332`, deterministic fixture `37984663412`, Android cold start `37984663429`, and Android real network E2E `37984663317`.

### RMD-1504 — Share E2E

- [x] Send `ACTION_SEND text/plain` fixture/supported input.
- [x] Enter real analysis/setup pipeline.

- [x] Schedule/download.
- [x] Verify Library state.
- [x] Verify back-stack behavior.

**Evidence (RMD-1504):** `app/src/androidTest/java/com/ekkus/offlineytplayer/Rmd1504ShareE2EInstrumentedTest.kt` runs `actionSendFlowsThroughAnalyzeSetupRealSchedulerWorkerLibraryAndBackStack` in the packaged API-29 Android smoke suite (`.github/workflows/android-smoke.yml`). It sends a real `ACTION_SEND text/plain` intent containing a fixture-supported URL to production `MainActivity`, exercises its analysis and Download Setup screens through the injected deterministic source gateway, dispatches a planned fixture through the actual Android scheduler/core worker into persisted local media and the production Library screen, then verifies Back returns from Add to Library. Exact already-merged `master` SHA `e89912f5e9b16eb83cdc7cd3651f04d77fb97a67` passed Android smoke run `37826114126` (including this test), CI `37826114032`, Android FGS timeout `37826114046`, deterministic fixture E2E `37826113874`, Supply chain `37826114114`, and CI evidence `37826113973`. This qualifies only the five RMD-1504 Share E2E subtasks; the separately failing Android cold-start lane `37826113886` is **not** counted as passed, and RMD-1501/1502/1503 offline cold-start closeout remains open.

### RMD-1505 — Storage failure E2E

- [x] Exercise insufficient-space preflight.
- [x] Exercise write failure/ENOSPC path where infrastructure permits.
- [x] Verify partial cleanup/recoverability.
- [x] Verify user-visible actionable failure.
- [x] Verify no false completed library record.

**RMD-1505 storage-preflight production repair (qualified incremental E2E):** independent review found that `DownloadEngine.preflight_space(...)` existed but the production transfer path never called it. The transfer engine now checks actual filesystem availability with `statvfs` before opening the network request, subtracts any resumable partial bytes from the required capacity, and returns permanent `InsufficientStorage` before writing when the remaining expected bytes exceed available space. Rust coverage proves this preflight wins over an unreachable-network URL. `Rmd1505StorageFailureInstrumentedTest` drives the packaged generated worker with a deliberately oversized deterministic plan, verifies durable `FAILED` state plus a non-retryable storage diagnostic, verifies no partial/final asset and no completed Library item, and verifies production Downloads UI shows the actionable failure. Exact implementation `bcd298d7e87db55dee16d3ab485c9214998c8033` passed CI `37767072682`, Android smoke `37767072673`, Android FGS timeout `37767072664`, Supply chain `37767072641`, CI evidence `37767072745`, and deterministic fixture `37767072764`; see `docs/RMD_1505_1507_EXACT_HEAD_QUALIFICATION_2026-10-08.md`. The separate write-time ENOSPC/partial-recovery requirements are qualified below by production `DownloadEngine::transfer` with deterministic injected filesystem I/O failure; Android preflight coverage alone is not cited as proof of that failure path.


**RMD-1505 portable write-time ENOSPC and partial-recovery evidence:** `core/src/download.rs::write_time_enospc_preserves_partial_and_next_attempt_resumes_to_completion` exercises the real `DownloadEngine::transfer` against a local HTTP fixture, injects an OS-shaped ENOSPC (`raw_os_error(28)`) after 17 bytes are physically written, and verifies non-retryable `InsufficientStorage`, no promoted final asset, exact retained partial length, and retained HTTP representation identity. A second real transfer reuses the validated partial range, verifies the final bytes match the fixture, and asserts the partial file and resume sidecar are removed after successful promotion. The injection is `#[cfg(test)]` and does not affect production; it is the available deterministic filesystem-write qualification tier under `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`, **not** a claim of physically filling Android device storage. The separately qualified packaged Android `Rmd1505StorageFailureInstrumentedTest` covers production worker/queue/UI error behavior on insufficient-space preflight. Exact previously merged master `fb643860264808c6dd4b29bbe52021ad437b5ba0` passed CI `37845850441` (including Rust tests), Android smoke `37845850545`, Android FGS timeout `37845850558`, Supply chain `37845850532`, CI evidence `37845850440`, and Deterministic E2E fixture `37845850479`. Android cold-start `37845850445` failed in unrelated RMD-1501 and is **not** counted as passing. These two RMD-1505 subtasks are reconciled for portable write-time ENOSPC and recoverable partials, not for an additional physical-device disk-fill run.


### RMD-1506 — Connectivity E2E

- [x] **RMD-1506c — Foreground worker settlement must not race SQLite teardown/reset.** Prove the production settlement path handles an unavailable database without crashing the Android process, and ensure the real second-schedule instrumentation waits for service settlement before removing its fixture database. Require passing exact-head CI and Android smoke, preserving the separately unqualified host metered-network checkbox.

**RMD-1506c — Foreground-worker teardown/read race (2026-10-09, pending exact-head qualification):** Android smoke runs `38001588673` on `cb1a969950ef7c15e910e46edd9059b6b4d2f669` and `38001667506` on `d58be1daa37521c1c0db2eadfa4ae9d9b91c730a` failed `secondScheduleDuringActiveTransferDispatchesAfterFirstWorkerWithoutNewNetworkCallback`: the durable COM-PLETED state was visible before service worker settlement finished, then test teardown removed the SQLite DB and a background `currentDownloadState` open threw `FfiCoreServiceOpenException.Persistence`. The instrumentation now waits for foreground notification dismissal after the two completed fixture jobs before database cleanup. Production worker settlement handles a failed late DB reopen with a bounded, non-sensitive warning rather than an uncaught background exception. Both fixes qualified on exact `master` `4d9700bfebd0c572950815f43422916b2eaa110c`: normal CI `38008449558` and API-29 Android smoke `38008449784` passed. The smoke lane executed `Rmd1506ConnectivityE2EInstrumentedTest` (logged `ANDROID_SMOKE_EXECUTED`), including the real second-schedule/worker-settlement fixture, while the service's `currentDownloadState` catches late SQLite reopen failures without logging raw diagnostics. The separately unchecked host-controlled Wi-Fi-only/metered OS transition remains open; Android real-network E2E `38008449466` passed its default-network loss/resume case but explicitly reported `WIFI_ONLY_METERED_UNAVAILABLE`, not `WIFI_ONLY_METERED_QUALIFIED`.\n\n

- [x] Start transfer.
- [x] Remove network.
- [x] Verify waiting/pause state.
- [x] Restore eligible network.
- [x] Verify legal resume.
**RMD-1506 real OS network acceptance (five subtasks):** `hostDrivenOsConnectivityLossPausesAndRestoresForegroundTransfer` uses the real Android default-network observer, foreground-service scheduling, durable Rust queue and download worker, and a slow loopback fixture. `scripts/run_android_os_network_e2e.sh` waits for the live worker, disables Wi-Fi/data and enables airplane mode, requires a durable PAUSED state with retained partial bytes and no final file, restores the network, and requires COMPLETED, byte-equal local media, Library record and cleaned partial. Run `37984663317` passed a non-skipped `OK (1 test)` and uploaded the host evidence. Exact `master` SHA `a9044a1415239f84cef1a90412b9099aeab2d8c4` passed CI `37984663297`, Android smoke `37984663480`, Android FGS timeout `37984663324`, Supply chain `37984663294`, CI evidence `37984663332`, deterministic fixture `37984663412`, Android cold start `37984663429`, and Android real network E2E `37984663317`. The distinct Wi-Fi-only/metered OS case remains **unchecked**.

- [x] Repeat with Wi-Fi-only/metered policy where emulator controls permit.

**RMD-1506 host-control limitation (2026-10-09):** Exact-head master `6d50bed7c9be9b9c8c839f12a0dad6fb24a975bd` passed Android real-network E2E run `37995032928` with a non-skipped `OK (1 test)` for genuine default-network loss/resume, but the API-29 AOSP emulator reported `AndroidWifi;none` and rejected `cmd netpolicy set metered-network ... true`. That run recorded `WIFI_ONLY_METERED_UNAVAILABLE`, **not** `WIFI_ONLY_METERED_QUALIFIED`; it does not satisfy this checkbox. The host now attempts a bounded root-capability probe on the disposable CI emulator only. Until an exact-head run records a non-skipped metered/unmetered PAUSED→COMPLETED transition and `WIFI_ONLY_METERED_QUALIFIED`, this checkbox remains unchecked.

**RMD-1506 Wi-Fi-only/metered OS acceptance (qualified):** `hostDrivenWifiOnlyMeteredWifiPausesAndUnmeteredWifiResumes` drives a second real foreground-core fixture under Wi-Fi-only, while `scripts/run_android_os_network_e2e.sh` applies Android's documented per-SSID metered override and verifies the observed policy state instead of trusting the command exit code. Exact current-master implementation SHA `54f359c0468a6c242ef9b133c8ed384a477c14ad` passed normal CI `38011102689` and Android real-network E2E `38011102518`. The real-network job executed the default-network loss/resume fixture and the Wi-Fi-only metered fixture as non-skipped `OK (1 test)` phases; it observed `AndroidWifi;true` and emitted the required `WIFI_ONLY_METERED_QUALIFIED` marker after the metered transition paused durable work and the restored unmetered transition resumed it to completion. This closes the final RMD-1506 host-controlled OS-network checkbox without treating earlier `WIFI_ONLY_METERED_UNAVAILABLE` runs as acceptance evidence.

**RMD-1506 real OS connectivity qualification lane:** `Rmd1506ConnectivityE2EInstrumentedTest.hostDrivenOsConnectivityLossPausesAndRestoresForegroundTransfer` runs a real generated core foreground download while `scripts/run_android_os_network_e2e.sh` toggles Wi-Fi/data/airplane mode through adb. Its handshake requires a real active-network callback, durable `PAUSED` state with retained partial bytes, a restored callback, and `COMPLETED` Library/bytes with no leaked partials. The separate `.github/workflows/android-real-network-e2e.yml` uploads host/runtime evidence and rejects skipped/absent tests. Do not check OS transition subtasks until a passing exact-head run confirms the emulator actually exposes the requested default-network transitions; if the emulator cannot simulate a transition, record that limitation without falsely treating a skipped test as proof.

**RMD-1506b transport-boundary acceptance:** Production `DownloadConnectivityMapper.fromCapabilities` derives Wi-Fi from the actual `TRANSPORT_WIFI` flag, not from an unmetered-cost flag. `DownloadNetworkPolicy` denies unmetered non-Wi-Fi transport under Wi-Fi-only, while retaining it for unrestricted downloads; `DownloadConnectivityMapperTest` and `DownloadNetworkPolicyTest` cover policy, with packaged `DownloadConnectivityObserverInstrumentedTest` mapping real Android capabilities. Exact `master` SHA `a9044a1415239f84cef1a90412b9099aeab2d8c4` passed CI `37984663297`, Android smoke `37984663480`, Android FGS timeout `37984663324`, Supply chain `37984663294`, CI evidence `37984663332`, deterministic fixture `37984663412`, Android cold start `37984663429`, and Android real network E2E `37984663317`.

- [x] **RMD-1506b — Do not equate unmetered transports with Wi-Fi.** Production `DownloadConnectivityMapper` previously classified any unmetered network (including Ethernet, cellular, or VPN) as `Unmetered`, allowing a Wi-Fi-only download without a Wi-Fi transport. Require correct Wi-Fi-transport mapping, deterministic JVM policy tests, packaged Android capability mapping, and passing exact-head CI/Android smoke before checking. This fixes the policy-boundary bug but does not replace the six host-driven OS network transition requirements above.
- [x] **RMD-1506a — Foreground-service observer reuse.** Verify a second schedule/resume command dispatches newly queued durable work when the connectivity observer is already registered and no new network callback arrives; require packaged Android test and exact-head CI before checking.


**Evidence (RMD-1506a observer reuse):** `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt::ensureConnectivityObserver` explicitly dispatches durable work when an existing observer is reused; `settleWorkerAfterExecution` hands off to the newest queued item after an active worker exits. Packaged API-29 instrumentation `app/src/androidTest/java/com/ekkus/offlineytplayer/downloads/Rmd1506ConnectivityE2EInstrumentedTest.kt` exercises `secondScheduleIntentDispatchesWorkWithAlreadyRegisteredConnectivityObserver` (a single injected initial Unmetered callback, no second callback/registration, real worker and completed local fixture) and `secondScheduleDuringActiveTransferDispatchesAfterFirstWorkerWithoutNewNetworkCallback` (second schedule while the first transfer remains active, both actual core jobs complete, no extra callback/observer). The instrumentation seam replaces only the Android callback source, not the runtime service, scheduler, durable worker, or filesystem. Exact already-merged `master` SHA `fb643860264808c6dd4b29bbe52021ad437b5ba0` passed CI `37845850441`, Android smoke `37845850545`, Android FGS timeout `37845850558`, Supply chain `37845850532`, CI evidence `37845850440`, and Deterministic E2E fixture `37845850479`. Android cold-start `37845850445` failed in separate RMD-1501 and is not counted as passing. RMD-1506's other six Android connectivity transition subtasks remain open because synthetic coordinator transitions are not proof that the device's OS network-change callbacks performed the requested sequence.


### RMD-1507 — Notification-control E2E

- [x] Pause from notification.
- [x] Resume from notification.
- [x] Cancel from notification.
- [x] Verify durable state/UI mirrors each action.

**RMD-1507 correctness repair (qualified):** independent review found that durable `CoreDownloadState.CANCELED` fell through `MainActivity.toUiState()` to `DownloadUiState.Active`, so a successful Cancel could be misrepresented as active work. Production mapping now has a dedicated `Canceled` UI/filter state with no legal row actions, with JVM coverage in `CollectionLayoutTest` and `DownloadsOperationalScreenTest`. The RMD-1507 E2E checkboxes are qualified by the production notification `PendingIntent` instrumentation and exact-head CI evidence below.

**RMD-1507 notification-control E2E increment (qualified):** `Rmd1507NotificationControlInstrumentedTest` obtains the production foreground-download notification on API 29 and invokes its real Pause, Resume, and Cancel `PendingIntent` actions. It verifies the generated UniFFI/core durable queue transitions `QUEUED → PAUSED → QUEUED → CANCELED`, launches production `MainActivity`, and verifies Downloads UI presentation changes from `Paused` to `Active` to `Canceled`. The smoke workflow includes this test. Exact implementation `bcd298d7e87db55dee16d3ab485c9214998c8033` passed CI `37767072682`, Android smoke `37767072673`, Android FGS timeout `37767072664`, Supply chain `37767072641`, CI evidence `37767072745`, and deterministic fixture `37767072764`; see `docs/RMD_1505_1507_EXACT_HEAD_QUALIFICATION_2026-10-08.md`. The four RMD-1507 notification-control E2E subtasks are qualified and reconciled.

**Acceptance for RMD-1500:** policy enum sequence tests may remain, but they cannot be cited as the E2E evidence for these tasks.


**Evidence (RMD-1500 incremental qualification):** Exact master `43847bddef7e710854bb8ff248fb4f7227ba1696` adds `Rmd1500AppPipelineFixtureTest`, which drives `OfflineYTPlayerApp(initialSharedUrl=...)` with a deterministic fixture source through the production Compose Add/Share → Analyze → Download Setup surface and app-owned gateway boundary without live-provider access. The same exact head passed CI `36473005897`, Android smoke `36473005880`, Android FGS timeout `36473005836`, Supply chain `36473005927`, CI evidence `36473005988`, and Deterministic E2E fixture `36473005955`. The existing deterministic core fixture lane separately proves real fixture download, durable Library persistence, cold-start reopen, playback-asset export, and no post-download network dependency. RMD-1501 through RMD-1506 still require full integrated E2E; RMD-1507 notification-control E2E is qualified above.


**RMD-1501 Android runtime fixture increment (qualified, not E2E closeout):** `app/src/androidTest/java/com/ekkus/offlineytplayer/downloads/Rmd1501AndroidRuntimeFixtureInstrumentedTest.kt` executes deterministic loopback media work in a packaged Android app through the production `DownloadWorkerExecutor`, generated UniFFI/core worker, durable queue, persisted Library, and exported local playback descriptor. It verifies downloaded bytes and the stored asset path. Exact master `9f201842eb257b6b62d32d5d9fb146abee5f86a2` passed CI `37743139720`, Android smoke `37743139397`, API-35 FGS timeout `37743139461`, Supply chain `37743139572`, CI evidence `37743139391`, and Deterministic E2E fixture `37743139388`. The fixture seeds executable work directly into the durable database, so it does **not** prove production Add/Share analysis, quality selection, Android scheduler dispatch, offline process restart, or MediaSession playback. All RMD-1501 subtasks remain unchecked until their complete production-path E2E acceptance is qualified. The API-35 workflow readiness-shell correction was qualified in the same exact-head matrix.


**RMD-1501 scheduled offline MediaSession increment (qualified partial path):** `Rmd1501AndroidRuntimeFixtureInstrumentedTest.scheduledFixtureReopensOfflineAndPlaysViaCanonicalMediaSession` schedules a deterministic WAV media fixture through the production API-29 foreground-service scheduler and core worker, closes the only HTTP fixture server, reopens the durable Library/playback gateways, verifies the persisted local media bytes/descriptor, then uses an independent `MediaController` to prepare/play that downloaded file through production `PlaybackSessionService`/ExoPlayer without an available source server. This is intentionally an incremental end-to-end **runtime-to-playback** proof, not the full Add/Share → Analyze → Setup pipeline, actual OS process kill, or video/split-A/V/subtitle/notification matrix; RMD-1501 through RMD-1506 retain unchecked E2E subtasks; RMD-1507 is qualified above.

---

## RMD-1600 — CI and supply-chain qualification

### RMD-1601 — Expand CI matrix

- [x] Rust fmt.
- [x] Rust clippy with warnings denied.
- [x] Rust unit/integration tests.
- [x] Android lint.
- [x] Android JVM tests.
- [x] Android assemble/package.
- [x] UniFFI generation consistency.
- [x] Android Rust ABI builds.
- [x] APK native-library packaging verification.
- [x] Android instrumentation/Compose tests.
- [x] Screenshot/golden tests.
- [x] Deterministic E2E fixture lane.
- [x] Exact-head identity assertion.

**Evidence (RMD-1601 partial reconciliation):** The fast deterministic matrix is already implemented on current master. `.github/workflows/ci.yml` runs exact-commit identity checks, Rust fmt, clippy with `-D warnings`, workspace tests, Android lint/JVM tests/assemble, reproducible UniFFI generation, both supported Android Rust ABI builds, and APK native-library verification. `.github/workflows/android-smoke.yml` also asserts exact commit identity and runs the packaged instrumentation/Compose suite including `ProductionComposeBehaviorTest`. Exact master `dc18f27c01620cd7b3254b1c5bb8d8f9b15b08eb` passed CI run `36186074873` and Android smoke run `36186075209`. Screenshot/golden qualification is implemented and qualified under RMD-1403 through RMD-1405 and `.github/workflows/android-smoke.yml`. The deterministic fixture CI lane is now implemented by `.github/workflows/deterministic-e2e.yml` and qualified independently of full RMD-1500 closeout: exact master `43847bddef7e710854bb8ff248fb4f7227ba1696` passed Deterministic E2E fixture run `36473005955` together with CI `36473005897`, Android smoke `36473005880`, Android FGS timeout `36473005836`, Supply chain `36473005927`, and CI evidence `36473005988`. This checks the RMD-1601 lane requirement only; it does not mark any RMD-1500 end-to-end behavior complete.

### RMD-1602 — Dependency/advisory checks

- [x] Add Rust vulnerability/advisory scanning with an explicitly reviewed exception mechanism.
- [x] Add Android/Gradle dependency vulnerability/license review tooling where practical.
- [x] Generate/reconcile OSS license notices for shipped dependencies.
- [x] Fail release qualification on unresolved prohibited/license-incompatible dependencies.

**Evidence (RMD-1602):** `.github/workflows/supply-chain.yml` is an exact-head supply-chain gate: it generates the Rust lockfile, runs `cargo audit --deny warnings`, exports the Android debug runtime and instrumentation dependency graphs for review, checks the generated third-party notice inventory, and uploads bounded evidence artifacts. `docs/SUPPLY_CHAIN_POLICY.md` defines the explicit dated exception process and prohibits silently carrying unresolved advisories, prohibited licenses, or license-incompatible dependencies into release qualification; `scripts/generate_third_party_notices.py` and `docs/THIRD_PARTY_NOTICES.md` keep the shipped Gradle/Rust dependency inventory reconciled. PR #381 merged the implementation as exact master `58f240c4dec8f699540f06552dcbfa6e8407d815`; master Supply chain run `36235854130`, CI `36235854097`, Android smoke `36235854115`, and Android FGS timeout `36235854212` all passed on that exact SHA. The external YouTube/service-policy/legal gate remains separate.

### RMD-1603 — CI evidence quality

- [x] Ensure failures preserve logs/test reports/screenshots.
- [x] Ensure emulator/E2E artifacts are bounded and useful.
- [x] Record exact candidate SHA in final qualification report.

**Evidence (RMD-1603):** CI evidence retention and bounded artifact policy are implemented and documented in `docs/RMD_1603_CI_EVIDENCE_QUALITY_2026-09-26.md`, with the dedicated exact-head `.github/workflows/ci-evidence.yml` lane plus bounded failure evidence in the regular CI, Android smoke, Android FGS-timeout, and Supply-chain workflows. Exact master `8b37d2e5aa144e8e14becacd1eb6b817f47dc0f2` passed CI `36379432539`, Android smoke `36379432588`, Android FGS timeout `36379432566`, Supply chain `36379432543`, and CI evidence `36379432541`. The exact candidate SHA is recorded in the evidence report and asserted by the workflows.

---

## RMD-1700 — Documentation reconciliation

### RMD-1701 — README truthfulness

- [x] Update project status to match actual implementation after remediation.
- [x] Describe supported/unsupported workflows.
- [x] Document build/run prerequisites.
- [x] Keep external release gate explicit.


### RMD-1702 — Build and FFI docs

- [x] Document Android ABI build flow.
- [x] Document UniFFI generation and Gradle integration.
- [x] Document emulator/device setup.
- [x] Document common native-loading failures.

### RMD-1703 — Background execution docs

- [x] Document API 34+ UIDT path.
- [x] Document API 26-33 fallback.
- [x] Document notification behavior.
- [x] Document reboot/process-death recovery.
- [x] Document Android 15+ restrictions relevant to this app.

### RMD-1704 — User guide

- [x] Rewrite workflow steps against operational UI.
- [x] Remove descriptions of controls that remain unimplemented/removed.
- [x] Document offline playback guarantees and limitations.
- [x] Document recovery/error states.

### RMD-1705 — Security/privacy/legal docs

- [x] Update input/security model.
- [x] Update diagnostic/redaction guarantees.
- [x] Update storage/deletion behavior.
- [x] Keep YouTube/service-policy/legal approval external and unresolved unless separately approved by a human authority.

### RMD-1706 — Supersede misleading prior audits

- [x] Add a remediation reconciliation document explaining which prior audit claims were corrected.
- [x] Do not delete historical audit docs; mark/supersede them clearly where their closeout claims are no longer authoritative.

**Evidence (RMD-1700):** Documentation reconciliation is implemented on current `master`: `README.md` truthfully describes remediation status, supported/unsupported workflows, build/run prerequisites, and the unresolved external release gate; `docs/ANDROID_RUST_FFI_BUILD.md` documents supported ABIs, Rust/UniFFI generation and Gradle integration, emulator/device setup, and native-loading troubleshooting; `docs/ANDROID_BACKGROUND_EXECUTION.md` documents API 34+ UIDT, API 26-33 fallback, notification behavior, reboot/process-death recovery, and Android 15+ foreground-service restrictions; `docs/USER_GUIDE.md` documents the operational UI, offline guarantees/limits, and recovery/error states; `docs/SECURITY_PRIVACY_LEGAL.md` documents input security, diagnostic redaction, storage/deletion behavior, privacy, and keeps source-service/legal approval explicitly external; and `docs/REMEDIATION_RECONCILIATION.md` preserves historical audits while superseding misleading closeout claims. These documents explicitly retain RMD-1500/RMD-1800 limitations rather than claiming engineering closeout.

---

## RMD-1800 — Final engineering closeout

### RMD-1801 — Detailed TODO reconciliation

- [ ] Review every RMD task and subtask against current `master` code.
- [ ] For each completed milestone, cite implementation paths and behavioral tests.
- [ ] Confirm zero unchecked engineering subtasks except explicitly external release approval, which must not be represented as engineering-complete approval.
- [ ] Do not collapse this TODO.

### RMD-1802 — Code review after remediation

**RMD-1802 independent resume-partial ownership review (2026-10-12; final exact-head qualification pending):** `core/src/resume.rs::prepare_partial_reuse` previously used `Path::exists()` to decide whether a partial was present, which follows live symlinks and treats dangling symlinks as missing. A linked partial with matching resume metadata could therefore be accepted as resumable by this public helper without establishing ownership of its bytes; a dangling link could silently clear the sidecar. The helper now checks `symlink_metadata`, accepts only regular partial files, and fails closed for symbolic links and directories. Executable Unix regression `symlink_partial_never_reused` preserves external bytes and verifies rejection with matching metadata. Implementation `40de90ae7bf2512461f0b86b095e96d70fe9a5a3`, formatting correction `71c9d6c8b9f98c895bcce842cc756dc85cc01e0d`. This is defense in depth alongside the transfer-path guard; it does **not** close RMD-1802, RMD-1803, RMD-1805, or the external service/legal release gate. Record passing exact-head CI evidence before claiming qualification.

**RMD-1802 durable identity fail-closed review (2026-10-10; exact-head qualification pending):** independent production mapping review found that `MainActivity.toLibraryScreenState` used `associateBy` on playback-asset item IDs without validating duplicate/blank identities; malformed gateway records could silently overwrite one another or produce ambiguous Library rows. The production mapper now rejects blank/duplicate Library and playback-asset identities instead of presenting a misleading Ready state (implementation `19b8c272c22369c69b13307e8924847de930b0ae`), with five valid/malformed JVM regression cases in `LibraryIdentityMappingTest` (test commit `bd339e5de8df2894fcd2d15b2e49aa137ea19e4a`). A related review found `toDownloadsScreenState` accepted duplicate/blank durable queue job IDs, risking ambiguous UI rows; `DownloadQueueIdentityMappingTest` and fail-closed production validation were added in `163fc2ab276ba48ec9544c579067b3429fdc0225`. Keep all RMD-1802/RMD-1803 and closeout boxes unchecked until the latest exact-head CI matrix passes and the remaining independent review is complete.

**RMD-1802 stale declarative-test cleanup (2026-10-09, pending exact-head qualification):** after removing `LocalPlaybackPolicy.UsesNetworkUris`, one JVM test still referenced that deleted flag and broke Android unit-test compilation. The test now relies on its existing executable remote-URI rejection and local-route assertions. The same pass removed `ShareToDownloadRoute.DownloadSetup.preservesFixedControlLayout = true` and its assertion; Share routing continues to test the real URL and Library back-stack route while compact/fixed layout remains covered by packaged Compose layout qualification.


**RMD-1802 download-presentation binding-drift finding (2026-10-09, pending exact-head qualification):** independent review found that `DownloadPresentationGateway.titlesByJobId` converted a missing or wrong-typed generated `items` property into an empty list. That could hide UniFFI binding drift and fabricate fallback presentation state. The mapper now requires a generated list; production bootstrap and periodic refresh catch mapper failures and surface a bounded Downloads-unavailable state instead of crashing or treating the metadata as empty. `DownloadPresentationGatewayMappingTest` covers present, missing, and wrong-typed `items`. The same exact-head repair also applies the Rust formatting required by `cargo fmt --check` after the preceding YouTube parser-test hardening commit.


**RMD-1802 no-op/circular-policy cleanup (2026-10-09, pending exact-head qualification):** independent review found that production `OfflineYTPlayerApp` and `LibraryScreen` still supplied silent default no-op callbacks for settings mutations and repository-query synchronization. Those defaults are removed so production callers must provide explicit handlers; instrumentation callers now opt into explicit no-ops only when the callback is outside that test's scope. The same sweep removed three reboot-recovery capability constants and `CollectionLayoutPolicy.HasHorizontalControlScrolling`, whose JVM assertions duplicated stronger boot decision/manifest checks and packaged Compose layout qualification. This cleanup is not itself behavioral proof; RMD-1802 remains open until the exact-head matrix passes and the Rust/Android review is completed.



**RMD-1802 policy-claim cleanup and UIDT job-ID overflow (2026-10-09, pending exact-head qualification):** removed unused unconditional capability booleans from Android connectivity, resume, notification permission, UIDT scheduling, foreground-timeout, Share routing, About diagnostics, and portrait-coverage policy objects, along with circular assertions that only checked those booleans. Existing real gateway/coordinator JVM tests and packaged Android instrumentation remain the behavioral qualification evidence; this removal alone does not prove runtime capability. Independent review also found that `stableJobId` used `Int.absoluteValue` on a Java string hash; `Int.MIN_VALUE` overflows and yields an ID outside the reserved job range. The scheduler now widens to Long before taking the absolute value, with `minimumIntHashStillProducesJobIdInsideReservedRange` covering the known minimum-hash input. RMD-1802 and engineering closeout remain unchecked pending exact-head qualification and continued Rust/Android review.



**RMD-1802 playback/UI policy-only cleanup (2026-10-09, pending exact-head qualification):** `PlaybackSessionPolicy` previously exposed four unconditional `true` claims for lock-screen controls, headset controls, audio focus, and noisy-audio handling, with `PlaybackSessionPolicyTest.mediaSessionPolicyCoversPlatformPlaybackControls` merely asserting those booleans. The dead declarations and circular test are removed. Existing `PlaybackSessionBehaviorInstrumentedTest` instead exercises two real MediaControllers sharing a MediaSession, system media keys, seek, and playback transitions; production `PlaybackSessionService` configures Media3 audio focus and becoming-noisy handling. Also remove `SettingToggle`'s silent default no-op callback so every production settings toggle must receive an explicit mutation callback. Do not count these static edits as a substitute for Android behavioral qualification.



**Independent code-review finding (2026-10-09, pending final exact-head qualification):** legacy Android download policies contained unused `const val true` feature claims—`ReportsCompletionAndFailure`, `ReconcilesDurableQueueOnStart`, boot recovery, Wi-Fi preference, notification actions, and pause/failure behavior—asserted solely by policy-only JVM tests. The production foreground service does not itself post separate terminal notifications, and its `ACTION_RECONCILE_AFTER_REBOOT` branch was a no-op; recovery instead occurs in the production startup `GeneratedUniffiCoreGateway.reconcileStartup` path, while real notification Pause/Resume/Cancel is covered by `Rmd1507NotificationControlInstrumentedTest`. Those dead declarations and their circular tests have been removed, without treating this cleanup as proof of feature completion; existing behavioral/packaged CI remains the acceptance gate. Full Rust/Android audit and RMD-1800 closeout remain open.

**RMD-1802 library-mutation binding-drift finding (2026-10-09, pending exact-head qualification):** independent review found that the production Android library mutation gateway converted a missing or wrong-typed generated UniFFI boolean property into `false`. A binding-shape regression could therefore make Rename/Remove appear to have made no change instead of surfacing an integration failure. The mapper now requires an actual generated Boolean and fails closed for missing/wrong-typed properties; `AppLibraryMutationGatewayMappingTest` covers true/false, missing-property, and wrong-type cases. Do not mark the review closed until exact-head CI qualifies this fix.

**RMD-1802 Android smoke failure (2026-10-10; open):** exact `master` `f07033c291ae284437b26834441e3cacd6baf745` failed Android smoke run `38019697278` in `Rmd1504ShareE2EInstrumentedTest` with a Compose `SnapshotStateObserver` multithreaded-access exception, while seven other exact-head workflows passed. See `docs/RMD_1802_ANDROID_SMOKE_SNAPSHOT_FAILURE_2026-10-10.md` for evidence and required investigation. This blocks RMD-1802/RMD-1803 closeout; do not check them or infer a root cause from the exception alone.

**RMD-1802 Android smoke recovery and production-path review (2026-10-10, exact candidate `0488fa5eff0d4fea3c4651eab995a0cddbe02271`):** the new settings-observer regression was moved to the individually executed required instrumentation runner in `scripts/run_required_android_smoke.sh`; Android smoke `38071487987` (API-29 job `114269626991`) passed. Same exact-head passes: CI `38071487927`, Android FGS timeout `38071487896`, Android cold start `38071488055`, Android real-network E2E `38071488022`, deterministic fixture E2E `38071487926`, Supply chain `38071487969`, and CI evidence `38071488124`. This supersedes the earlier missing-test execution failure at `00f37d1c49e87990fc4d52fe62c0d98900ca828d`, but does not by itself establish the root cause of the earlier Compose snapshot exception or close the independent review. A subsequent production review found `MainActivity.toLibraryScreenState` silently mapped a missing/failed playback index to empty assets, causing completed media to appear unplayable instead of surfacing an integration failure; the next code change must fail closed and qualify this mapping with JVM tests. Keep RMD-1802/RMD-1803 and global closeout boxes open until the new exact head passes and review is complete.

**RMD-1802 additional fail-closed mapping audit (2026-10-10; pending new exact-head qualification):** a successful-but-null Rust/UniFFI library or download-queue payload previously rendered as an empty production list in `MainActivity.toLibraryScreenState` / `toDownloadsScreenState`. These now report bounded unavailable errors instead of fabricating empty state. `DownloadPresentationGateway.titlesByJobId` previously filtered null generated presentation items and silently overwrote duplicate job IDs; strict item/identity/title validation now rejects malformed, blank, and duplicate entries. JVM mapping tests exercise malformed and valid cases. Keep review/qualification/closeout checkboxes open until the new exact head passes all required gates.

**RMD-1802 durable worker/control/enqueue race review (2026-10-10; current-master exact-head qualification open):** independent Rust production review uncovered three race classes after the earlier atomic claim/startup recovery correction: stale Android pause/cancel/retry controls overwriting newer worker state, concurrent enqueue overwriting executable plans/queue state, and a pause/cancel between job claim and transfer startup being overwritten by an unconditional `Downloading` save. The corrections are in `core/src/ffi_download_control.rs`, `core/src/durable_work.rs`, `core/src/persistence.rs`, and `core/src/worker.rs`, with deterministic cross-connection and pause-after-claim regressions. See `docs/RMD_1802_WORKER_CONTROL_ENQUEUE_RACE_REVIEW_2026-10-10.md` for commit/evidence links and remaining qualification requirements. The prior clean SHA `79743096567e640d7d18220a169370c7f62e12c1` passed all eight required workflows, but these *new* fixes require their own exact-head full matrix. Do not check RMD-1802, RMD-1803, or final closeout solely on source/test presence.

**RMD-1802 repeat Android smoke qualification and streaming progress (2026-10-11; final closeout open):** exact `master` `a12947bbc6312b74152e2c7359d2e0e6eb01e3ea` passed seven of eight workflows but failed Android smoke `38120951391` from the recurring RMD-1504 Compose `SnapshotStateObserver` cross-thread exception. Commit `42f0765e775060cef6cf0eae5000e5d38e94becc` isolates the mandatory ACTION_SEND and settings-observer tests in fresh, exact-method-asserted instrumentation runs; **all eight exact-head workflows passed** on that commit (CI `38124113318`, Android smoke `38124113305`, FGS timeout `38124113335`, Supply chain `38124113307`, CI evidence `38124113291`, deterministic fixture E2E `38124113328`, Android cold start `38124113313`, Android real-network E2E `38124113299`). This proves the isolated test runs pass, not the root cause of the prior intermittent Compose failure. A subsequent Rust review found single-asset progress was emitted only at transfer completion. `core/src/download.rs` and `core/src/worker.rs` now support real chunk-progress callbacks and CAS-safe durable/UI progress; see the RMD-507 note and `docs/RMD_1802_PROGRESS_STREAMING_REVIEW_2026-10-11.md`. Keep RMD-1802/1803 and final closeout open until the latest exact-head full matrix qualifies and independent review is complete.

**RMD-1802 atomic verification/completion finding (2026-10-11; exact-head qualification open):** production review found `DownloadWorker::execute_one` unconditionally wrote `Verifying`, promoted Library metadata, and finally wrote `Completed` as separate non-CAS operations, potentially overwriting a concurrent user Pause/Cancel or promoting an item whose durable queue was canceled. Implementation in `core/src/worker.rs` now uses state-identity compare-and-swap for Downloading → Verifying and `core/src/persistence.rs::LibraryStore::promote_completed_if_unchanged` commits Verifying → Completed and Library metadata/staging cleanup in one SQLite transaction guarded by exact previously observed snapshot fields. `concurrent_cancel_cannot_promote_or_complete_a_stale_job` checks that cancellation prevents Library promotion and preserves the canceled durable queue, and a valid completion atomically promotes metadata. Implementation `27f82b4f6e26c27fadeb95fda9d5b148fb0e27e7`, rustfmt fix `ca91e6d887200daeac9fc603c42ea485e74371c3`; the new exact-head Android/CI matrix and remaining independent review are required before RMD-1802 or RMD-1803 may close. See `docs/RMD_1802_ATOMIC_COMPLETION_REVIEW_2026-10-11.md`.

**RMD-1802 stale error/cancel settlement qualified on exact implementation head (2026-10-11):** the stale worker settlement defect was corrected in `core/src/worker.rs` using same-attempt active-state reread and atomic compare-and-swap; Failed, RetryWait and Canceled reports require actual settlement or an authoritative same-attempt stop, preserving newer durable progress. Deterministic Rust regressions `stale_error_and_cancel_handlers_preserve_user_pause_and_cancel`, `reclaimed_attempt_cannot_receive_stale_settlement_or_stop_report`, and `settling_failure_or_cancel_keeps_latest_progress_and_total_bytes` passed. Exact implementation SHA `d8ce243cc347beaf88d1e29194520c0792c32286` passed all eight workflows: CI `38132996804`, Android smoke `38132996795`, Android FGS timeout `38132996780`, Supply chain `38132996799`, CI evidence `38132996815`, Deterministic E2E fixture `38132996757`, Android cold start `38132996776`, Android real-network E2E `38132996821`. Finding with details: `docs/RMD_1802_STALE_ERROR_SETTLEMENT_FINDING_2026-10-11.md` at evidence SHA `3a502ee2088206c65662111793527ed72ef32a83`. Complete independent Rust/Android review, frozen candidate qualification, final closeout and external service/legal approval remain separate and unchecked.

**RMD-1802 Android progress overflow independent review (2026-10-11; new exact-head qualification pending):** `MainActivity.toDownloadsScreenState` multiplied the persisted signed `Long` byte count by 100 before division, overflowing on sufficiently large downloads and presenting an invalid negative or truncated percentage. Production now calls `boundedDownloadPercent` using clamped byte values and a bounded ratio to avoid signed overflow; `DownloadQueueIdentityMappingTest.percentHandlesSignedLongBoundaryWithoutOverflow` and `percentBoundsUnknownAndInvalidDurableCounters` exercise Long.MAX_VALUE, over-total, negative, zero, and unknown counters. This is a real JVM/prod mapper correction and does not close RMD-1802 or RMD-1803 until the exact-head suite passes and the remaining independent review is complete.

**RMD-1802 resumed-transfer correctness follow-up (2026-10-11; latest exact-head matrix pending):** a production review of `core/src/download.rs` found the prior resumable HTTP `Content-Range` check accepted only a matching starting offset without validating the end/complete length, and could accept an unsolicited 206 as a complete asset. Implementation `59250fca455bac24fbc7a85fb5bdefab4fc7f12f` now validates start, end, complete size, declared body length, and the actual returned tail length before promotion; `malformed_resume_range_restarts_without_appending_unproven_bytes` exercises a misleading 206 fixture. Follow-up formatting `04a312972ed0cac6bc34b4ead46ff15d40e413bf` inadvertently truncated the last symlink-cleanup regression and module brace, restored by `6aa5cd7cdc16d47b9b182440869026e127e8937d`. Further independent transfer audits corrected storage preflight when a stale partial must be discarded (`43c67a923c70993fa99a62b022267708f6b441e1`; formatted `994be334455f69cc7a5ac3eaf8ec0811d2568e6e`), recovery from an HTTP 416 on a completed stale partial (`1a990c92ad794439f25761af64025ba157b2a03d`), and acceptance of weak ETags as byte-identity validators (`0df91a36bdcbbbe53e3eeec881cf6542098ec34e`). Corresponding Rust regressions include `stale_partial_requires_full_storage_capacity`, `range_416_for_completed_partial_restarts_from_zero`, `weak_etag_never_authorizes_byte_range_append`, and `etag_presence_change_cannot_silently_downgrade_to_date`. The RMD-1504 ACTION_SEND test now asserts real fixture transfer and durable Library completion rather than a transient toast (implementation `19d0475828b3f04825c712426520778dd99777d3`). These are implementation/test references **not** final qualification: concurrent newer commits canceled several preceding workflow matrices, and RMD-1802/RMD-1803/global engineering closeout remain unchecked until an exact final `master` head passes all required gates and the independent review is complete. External YouTube/service-policy/legal approval remains separate.

**RMD-1802 independent follow-up — corrupt work plans, cold-start fixture timing and symlinked asset validation (2026-10-11; final qualification pending):** Rust core/src/durable_work.rs now identifies malformed targeted executable JSON separately from database I/O errors; core/src/ffi_download_worker.rs claims and settles it as durable, nonretryable Failed (implementation 326c2976ef953db712bed10ecefab248e9cd6046; legacy assertion correction d5584ef70a4a29eb84ec1765c82273ce4432c7f9). core/src/ffi_download_control.rs lets an already-Queued corrupt plan through scheduling to that worker settlement, with an executable FFI integration test (1d9f2d3169897ac7d551c8d3f823ec334baccad6; rustfmt 33ad79d4a48703903094d971f966fe43b45aedd3). RMD-1501 cold-start instrumentation initially timed out after successful durable download; extending the WAV fixture to 90 seconds added state telemetry (9ad1c0e3be8b1aa9edb9749171565db55b6c27dc) but exact-head Android cold-start run 38158012446 still failed while Media3 reported STATE_READY (3), playWhenReady=true, 17,449 ms of progress, and a local file URI for the persisted asset. The remaining assertion compared a literal URI against Uri.fromFile(localFile), which can differ for equivalent Android app-private path aliases. The assertion now requires file: scheme plus matching canonical file identity while retaining STATE_READY and playWhenReady (1a477aa47893caf5a374e9196ee973b1c46799f4); this change still requires its own exact-head Android qualification. The sparse-partial storage preflight test now uses a larger shortage margin to avoid parallel CI workspace disk reclamation changing its expected failure classification (7af7cd3703f3f13dcf32f9077c6673b03744b65b). Independent security review found core/src/asset_validation.rs could follow symlinked persisted file/parent paths and validate an external file as healthy. Symlink components now fail closed with cheap/deep deterministic regressions (51cfc3b6fb6cb423f5519dd15b7b50d815b08034). These SHAs document implementation and test changes only: do not check RMD-1802/RMD-1803/RMD-1805/global closeout until a frozen exact master head passes the full matrix and independent review is finished. External service/legal approval remains separate. 

**RMD-1802 FGS timeout durability review (2026-10-11; exact-head qualification pending):** `DownloadForegroundTimeoutStore.persistTimeout` previously used asynchronous `SharedPreferences.Editor.apply()` immediately before `DownloadForegroundService.onTimeout` stops the service. The persisted timeout record could therefore lag process termination despite in-memory instrumentation assertions passing. Change to synchronous `commit()` and add a packaged Android instrumentation check that reads the actual backing XML after return. See `docs/RMD_1802_FGS_TIMEOUT_DURABILITY_REVIEW_2026-10-11.md`. This is not final RMD-1802/RMD-1803 qualification or a closeout checkbox.

**RMD-1802 prefixed diagnostic-header redaction finding (2026-10-11; exact-head qualification pending):** independent Rust security review found `core/src/security.rs::redact_sensitive` only recognized credential-bearing headers at the beginning of a line. Common log prefixes such as timestamps, levels, or `request failed:` could therefore leave Authorization/Cookie/X-Api-Key values in diagnostics. The redactor now finds sensitive header fields anywhere in the line, sanitizes any URL-bearing prefix, and drops the entire sensitive suffix. Deterministic regression `prefixed_sensitive_headers_never_leak_values` covers timestamp, error prefix, and combined signed-URL/header cases. Do not close RMD-1802/RMD-1803 before the new exact-head matrix passes and the independent review is complete.

**RMD-1802 Android UIDT lifecycle and collision follow-up (2026-10-11; exact-head qualification pending):** `DownloadUserInitiatedJobService` previously called `jobFinished()` only after normal return from `DownloadWorkerExecutor.execute`, so an unexpected escaping failure could strand job ownership. The production thread now executes work with `runAndSettleDownloadJob` using `finally`, and `jobIsSettledExactlyOnceWhenWorkerThrowsUnexpectedly` covers exception and success paths. `DownloadExecutionSchedulerPolicy.availableJobId` previously returned the first free hash-probe slot before finding an existing pending job for the same durable queue item, potentially scheduling duplicate jobs after a prior colliding occupant completed. It now prioritizes an existing matching job ID, with `reschedulingCollisionVictimReusesItsExistingJobAfterEarlierSlotBecomesFree` reproducing the case. Implementation and tests committed directly to master at `b3d0384857b12a6b5cf1937b2b438900cd7a411f`. These changes are not RMD-1802/RMD-1803 closeout: all eight exact-head workflows must qualify the final candidate and independent review must finish.

**RMD-1802 library-root symlink containment finding (2026-10-11; qualification pending):** independent Rust asset-integrity review found `validate_library_item_assets` rejected symlinks under an asset path but did not check whether `library_root` itself was a symlink. A caller with a symlinked root could therefore certify an external file as Healthy. The validator now fails closed on a symlinked or non-directory root, reports a missing root as Missing, and includes a deterministic Unix regression for Cheap/Deep modes and invalid roots in `core/src/asset_validation.rs`. Keep RMD-1802/RMD-1803 and engineering closeout open until this exact-head change passes required CI and the independent review is finished.

**RMD-1802 persistence validator symlink/hash bypass (2026-10-11; qualification pending):** `LibraryStore::validate_item_assets` separately used `std::fs::metadata` plus byte length and therefore followed symlinks and accepted same-length corruption despite recorded SHA-256. This independent production path now delegates to shared `validate_library_item_assets` at Deep depth, preserving explicit MissingAsset/CorruptAsset errors. `core/src/persistence.rs` adds executable regression coverage for a healthy asset, same-length tampering, a symlinked file, and a symlinked library root. Keep RMD-1802/RMD-1803 open pending exact-head CI and remaining review.

**RMD-1802 UIDT invalid-start double-settlement finding (2026-10-11; qualification pending):** `DownloadUserInitiatedJobService.onStartJob` previously called `jobFinished(params, false)` and then returned `false` for blank/missing durable queue IDs. Android's `JobService` contract already treats return `false` as completed, so the extra call could double-settle an invalid job. The invalid-ID branch now returns `false` before creating a notification without explicitly finishing; `DownloadExecutionSchedulerPolicyTest.invalidJobExtrasReturnAlreadyFinishedWithoutDoubleSettlement` guards the source-level framework contract. This check does not replace Android runtime or stop/cancellation E2E qualification; RMD-1802/RMD-1803 remain open pending exact-head evidence.

**RMD-1802 independent library-root containment follow-up (2026-10-11; qualification pending):** `core/src/download.rs::canonical_library_root` accepted a symlinked or non-directory library root before transfer/partial cleanup. Exact master implementation `a555cce60f6bdb18005b211f84db6ccfee990858` adds fail-closed root checks and deterministic transfer/cleanup regressions, with external partial bytes preserved; exact-head CI is pending. Independent review also found that `core/src/deletion.rs::delete_library_item_owned_assets` canonicalizes the root without rejecting a symlink first, potentially deleting external files and their persisted Library metadata. The deletion-path correction now rejects symlinked and non-directory roots before canonicalization, with deterministic Rust regressions proving external asset/partial/sidecar and durable metadata preservation. This change is **pending exact-head CI qualification and independent closeout review**; do not mark it complete merely because code exists. See `docs/RMD_1802_LIBRARY_ROOT_SYMLINK_TRANSFER_REVIEW_2026-10-11.md`. Keep RMD-1802/RMD-1803/RMD-1805 unchecked until both corrections and the final matrix qualify.

**RMD-1802 Android storage-cleanup root containment finding (2026-10-11; exact-head qualification pending):** `StorageSettingsManager.cleanup(Cache)` previously called `cacheRoot.listFiles()` without rejecting a symlinked root, thereby enumerating and deleting externally stored cache contents through an apparent app-private boundary. Both cache and incomplete cleanup now require an existing, non-symlink real directory before traversal; JVM tests `cacheCleanupRejectsSymlinkedRootWithoutDeletingExternalFiles` and `incompleteCleanupRejectsSymlinkedRootWithoutDeletingExternalPartials` assert external file preservation. The source fix and tests do not establish independent-review or final qualification completion. RMD-1802/RMD-1803/RMD-1805 remain unchecked until the final exact-head matrix passes and remaining review is complete.

**RMD-1802 concurrent startup-recovery finding (2026-10-11; exact-head qualification pending):** `reconcile_startup_downloads` previously rewrote a scanned active job unconditionally with `save_download_snapshot`, potentially reverting a newer user pause, cancel, or completion observed by another Android SQLite connection. Recovery now uses exact-state `replace_download_snapshot_if_unchanged` and counts only successful transitions. `reconcile_orphaned_staged_jobs` likewise now uses insert-if-absent to avoid overwriting a durable job enqueued after its scan. Cross-connection Rust regressions `stale_startup_requeue_cannot_override_newer_pause_or_completion` and `stale_staged_recovery_does_not_replace_concurrent_durable_enqueue` cover the races. RMD-1802/RMD-1803/RMD-1805 remain open pending exact-head CI and completion of the independent production audit.

**RMD-1802 startup asset-repair overwrite follow-up (2026-10-11; exact-head qualification pending):** completed-item asset mismatch reconciliation also used an unconditional upsert for `repair:<item_id>` and could regress an existing queued repair decision to Failed on every startup. The repair-marker path now inserts only if absent, and the existing file-backed regression verifies that a second reconciliation preserves an already queued repair row. This is an additional startup concurrency correction, not completion of the independent review or final qualification gates.

**RMD-1802 in-root linked-parent deletion finding (2026-10-11; exact-head qualification pending):** `delete_library_item_owned_assets` previously used canonical containment without rejecting a symlinked parent pointing to another item *inside* the same library root. This could remove the other item's asset despite passing the root check. `remove_owned_file` now rejects symlink components beneath the root, and `linked_parent_within_library_cannot_remove_another_items_asset` verifies that the unrelated file and target item's metadata survive. Independent review and RMD-1803/1805 closeout remain open pending exact-head qualification.

**RMD-1802 Android API-34+ UIDT platform-stop lifecycle finding (2026-10-11; runtime qualification pending):** `DownloadUserInitiatedJobService.onStopJob` previously left native work running and allowed late `jobFinished` after platform stop. The production correction now tracks exact `JobParameters` attempts, cooperatively signals the Rust worker, prevents stale settlement, and waits for a predecessor writer to exit before replacement. JVM coverage includes stop/replacement identity, user-stop rescheduling policy, and native-stop ownership races. The Android API-35 host-driven stop/restart scenario, actual transfer interruption, and full exact-head matrix remain unqualified; do not close RMD-1802/RMD-1803/RMD-1805. See `docs/RMD_1802_UIDT_PLATFORM_STOP_LIFECYCLE_REVIEW_2026-10-11.md`.

**RMD-1802 independent foreground-service timeout/native-worker ownership finding (2026-10-12; qualification pending):** `DownloadForegroundService.onTimeout`, `onDestroy`, and `ACTION_STOP` previously released Android service ownership without signaling the detached `DownloadWorkerExecutor` thread, so a native Rust transfer could continue after a dataSync timeout and a late connectivity callback could relaunch work. Production now retains an exact `DownloadWorkerExecution` stop callback behind `ForegroundWorkerOwnership`, closes the gate on service stop, and blocks late launches. JVM tests cover stop/begin races and timeout ordering. See `docs/RMD_1802_FGS_TIMEOUT_NATIVE_WORKER_STOP_REVIEW_2026-10-12.md`. Do not close RMD-1802/1803/1805 without exact-head CI and Android runtime qualification.

**RMD-1802 UIDT emulator-start qualification hardening (2026-10-12; exact-head qualification pending):** API-35 FGS/UIDT run `38276199963` accepted both UIDT schedules but left the first fixture QUEUED and the second fixture without a first byte; prior run `38275320867` observed the stop fixture COMPLETED before cancellation. The instrumentation now waits briefly for a natural start, then uses the emulator shell's `cmd jobscheduler run -f` to launch the *real* JobService when instrumentation-background scheduling is deferred; a paced first transfer preserves a meaningful stop-before-completion window. This is a deterministic worker/stop/retry test harness, **not** proof that the platform will promptly start UIDT jobs from a genuine user gesture. Keep RMD-1802/1803/1805 unchecked until full exact-head Android qualification and the independent review complete.

**RMD-1802 Android UIDT parcel-equivalent stop callback fix (2026-10-12; exact implementation head qualified, final review remains open):** API-35 FGS/UIDT instrumentation on previous `master` `3b1cbd73854a77356885a3543cf325ac471f1f94`, run `38285005426` attempts 1 and 2, repeatedly left the durable queue `DOWNLOADING` after JobScheduler timeout instead of transitioning to `RETRY_WAIT`. Production `DownloadUserInitiatedJobService.onStopJob` was locating worker ownership only by Java `JobParameters` reference identity; callbacks can carry separately unmarshaled equivalent parameters, leaving the worker unstopped. Implementation `5d72860b70301e3bd756be42e752d9a00a9411ec` in `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadExecutionScheduler.kt` validates both the framework job ID and durable queue identity for a fallback and retains attempts in predecessor order. `DownloadExecutionSchedulerPolicyTest` adds parcel-equivalent stop, wrong-job rejection, native-stop signaling and replacement-isolation regressions. **All eight exact-head workflows passed on the implementation SHA:** CI `38291558898`, Android smoke `38291558809`, Android FGS timeout (including API-35 stop/retry) `38291559053`, Supply chain `38291558804`, CI evidence `38291558830`, deterministic fixture E2E `38291558902`, Android cold start `38291558832`, Android real-network E2E `38291558885`. This qualifies this specific finding, **not** the entire independent production audit, freeze/closeout or human external service/legal approval. Keep RMD-1802/1803/1805 and global checkboxes open pending remaining audit and final exact-head reconciliation.

**RMD-1802 independent resume-sidecar resource-bound finding (2026-10-12; exact-head qualification pending):** `core/src/resume.rs::load_resume_representation` previously used an unbounded `fs::read` on a persisted `.resume.json` sidecar. A corrupt/imported oversized sidecar could allocate arbitrarily much memory before JSON parsing. The reader now streams at most 64 KiB + 1 byte, rejects oversize as nonretryable `IntegrityFailure`, and the writer enforces the same bound. Rust regressions `oversized_sidecar_fails_closed_without_consuming_partial` and `oversized_resume_representation_is_not_written` verify fail-closed behavior and preservation of partial bytes. See `docs/RMD_1802_RESUME_SIDECAR_SIZE_BOUND_REVIEW_2026-10-12.md`. Keep RMD-1802/1803/1805 unchecked until the exact implementation head passes all required CI and the independent audit is complete.

**RMD-1802 UI qualification evidence audit (2026-10-12; corrective deletion `bcd3f2be4fc8abd8f93ef7a11c5b1ce740c64d40`, exact-head CI pending):** `NoHiddenControlsPolicy` and `AccessibilityQualificationPolicy` stored hand-authored true/false capabilities rather than measuring Compose UI behavior; their host-side tests asserted only those declarations. They could not qualify no-hidden-controls, accessibility, focus, or large-text requirements. The four declarative-only production/test files have been removed. Actual rendered behavior is exercised separately by `ProductionComposeLayoutAccessibilityTest` (control bounds, semantics, touch targets, long-list scrolling, large text), `ProductionComposeBehaviorTest`, and `ProductionComposeGoldenTest`. See `docs/RMD_1802_UI_QUALIFICATION_EVIDENCE_REVIEW_2026-10-12.md` and the corrected `docs/NO_HIDDEN_CONTROLS_AUDIT.md`. The `GoldenCoveragePolicy` 22-scenario target inventory must not be conflated with executable screenshot coverage: independently enumerate pinned bitmap tests and profile combinations before final closeout. Do not close RMD-1802, RMD-1803, RMD-1805 or global proof rules until remaining full-spectrum review and current-master CI qualify.

**RMD-1802 independent playback and source-choice review (2026-10-12; current exact-head qualification pending):** The earlier fixed 30-second completion window in `LocalPlaybackPolicy.completedByPosition` incorrectly treated the start of any video shorter than 30 seconds as completed; `fa8d1823e6da6394a3fc55cb5b8c055a0d427cb3` caps the near-end window at one-quarter of shorter recordings while retaining the 30-second rule on longer videos, with JVM short/tiny/boundary regressions. Android `GeneratedUniffiSourceAnalysisGateway` previously applied `distinctBy { it.label }` to format choices and silently hid different provider formats with identical display labels; `f815fff73b18a8a4258990454a76ee77ac5841e9` preserves choice IDs, assigns non-colliding display suffixes, and adds executable mapping tests. `OfflineYTPlayerApp` previously made Download Setup actionable with zero usable format IDs; `09ad21f7d26d8348504938c134faffc1e2296f2f` disables Download without a selected executable quality ID and adds a packaged Compose regression. Detailed review: `docs/RMD_1802_PLAYBACK_AND_QUALITY_CHOICE_REVIEW_2026-10-12.md`. These source/test changes **do not** complete RMD-1802, RMD-1803 or engineering closeout until the final exact-head CI matrix passes and independent review is finished. No human YouTube/service/legal approval is inferred.

**RMD-1802 provider diagnostic disclosure finding (2026-10-12; exact-head qualification pending):** `SourceAnalysisUseCase.classify` passed raw provider `CoreGatewayError.message` into Add/Analyze UI for unsupported, network, parser/source-changed and general errors, potentially exposing signed URLs, Authorization headers or cookies supplied by an upstream transport diagnostic. Commit `00e57e954fc983514c154ef1cea397020fd40476` replaces display text with static, category-specific diagnostics while retaining machine-readable error kinds and retryability; `3b917e81472a19fc0edb943d91e7193ac9ecce6d` retains the existing safe `SOURCE_ANALYSIS_UNAVAILABLE` wording required by the existing gateway-exception test. JVM regression `SourceAnalysisUseCaseTest.providerErrorsNeverExposeSecretsInPresentationState` injects explicit private credentials/URL values across all categories. This is **implementation evidence, not closeout**; see `docs/RMD_1802_SOURCE_DIAGNOSTIC_DISCLOSURE_REVIEW_2026-10-12.md`. RMD-1802/1803/1805 and global proof requirements remain unchecked pending the complete exact-head matrix and unfinished independent review.

**RMD-1802 dead Add-screen policy/evidence correction (2026-10-12; exact-head qualification pending):** The unused `app/src/main/java/com/ekkus/offlineytplayer/ui/AddScreenPolicy.kt` declared five hand-authored visibility/scroll booleans and `primaryControlsFit()`, unrelated to measured Compose geometry. Its `app/src/test/java/com/ekkus/offlineytplayer/ui/AddScreenPolicyTest.kt` inspected source strings rather than rendering or invoking behavior. Commit `5f154004c82f8a8a464d38a3f0a75a12b6d16c78` deletes both policy-only artifacts. The production Add/Analyze/Setup code remains in `ui/AppShell.kt` and is qualified through `ProductionComposeBehaviorTest`, `ProductionComposeLayoutAccessibilityTest`, `AddWorkflowPolicyTest`, `SourceAnalysisUseCaseTest` and staged Android smoke lanes rather than checkbox-by-assertion. See `docs/RMD_1802_ADD_POLICY_EVIDENCE_REVIEW_2026-10-12.md`. This evidence records a focused finding; it does **not** establish a completed repo-wide no-op/dead-policy search or RMD-1802/1803/1805 closeout.

**RMD-1802 library error HTTP-header leakage (2026-10-12; qualification pending):** `SourceMetadataPolicy.diagnostic`, used in real library rename/remove/details error paths, redacted signed URLs but still displayed raw Authorization/Proxy-Authorization/Cookie/Set-Cookie/X-Api-Key header values. The formatter now redacts these headers and all trailing content after sanitizing any signed URL prefix, including timestamp-prefixed diagnostics, with two new host-JVM regression tests. See `docs/RMD_1802_LIBRARY_ERROR_HEADER_REDACTION_REVIEW_2026-10-12.md`. This fix does not close RMD-1802/1803/1805; require all eight workflows on the new exact `master` and completion of independent audit before checking closeout boxes.

**RMD-1802 Add/Download Setup error-presentation finding (2026-10-12; implementation and packaged Compose regressions added; exact-head qualification pending):** `OfflineYTPlayerApp` publishes a raw `AppDownloadControlGateway.enqueue` error message directly into rendered Compose status instead of passing it through `SourceMetadataPolicy.diagnostic`. Production now sanitizes enqueue failure diagnostics via `SourceMetadataPolicy.diagnostic`; packaged Compose regressions exercise the actual Add/Analyze/Download enqueue failure and bound an untrusted Library Details quality label. See `docs/RMD_1802_DOWNLOAD_SETUP_ERROR_DISCLOSURE_REVIEW_2026-10-12.md`. Implementation is not qualification: keep RMD-1802/1803/1805 and global closeout checkboxes open until the exact-head CI matrix and independent review are complete.

**RMD-1802 Library Details persisted metadata presentation follow-up (2026-10-13; exact-head qualification pending):** Production `LibraryDetailsPanel` rendered canonical source URLs and asset paths directly from persisted gateway records, bypassing the already-required URL/query/header diagnostic sanitizer. This could expose signed query tokens and allow unbounded strings in the Details UI, despite the previously bounded quality label. `ui/LibraryDownloads.kt` now applies `SourceMetadataPolicy.diagnostic` to displayed source identity, canonical URL, and asset summary text. A packaged `ProductionComposeBehaviorTest.libraryDetailsPanelRedactsPersistedUrlsAndAssetPaths` invokes the real Details action with an injected gateway record containing synthetic secret-bearing query parameters and checks that the token is absent and redacted values are rendered. This is implementation/test evidence only; keep RMD-1802, RMD-1803, RMD-1805 and global closeout unchecked until exact-head required CI and independent review complete.

**RMD-1802 recovery/export and Downloads error-presentation follow-up (2026-10-13; exact-head CI pending):** `ui/LibraryDownloads.kt` previously exported raw Library recovery diagnostic text with `Intent.EXTRA_TEXT`, and failed/unavailable Library or Downloads repository messages and per-download failure text could render raw gateway strings. The production paths now use the shared `SourceMetadataPolicy.diagnostic` redaction/bound before display or export. Packaged `ProductionComposeBehaviorTest` regressions `recoveryExportUsesRedactedDiagnosticInsteadOfRawGatewayText` (intercepts the actual share chooser and verifies its ACTION_SEND payload) and `downloadsAndLibraryUnavailableDiagnosticsAreRedactedInRenderedUi` exercise the real user-facing callbacks/state rather than asserting a policy constant. See `docs/RMD_1802_RECOVERY_EXPORT_AND_DOWNLOAD_ERROR_PRESENTATION_2026-10-13.md`. This does not close RMD-1802, RMD-1803, RMD-1805 or global requirements without full current-head CI and remaining independent production review.

**RMD-1802 Library Details large-asset layout follow-up (2026-10-13; CI pending):** the production Details panel was a non-scrollable, non-weighted Column, allowing a sufficiently large persisted asset list to push the Close details control beyond the reachable viewport and consume the Library collection region. `ui/LibraryDownloads.kt` now caps the panel at 320dp with vertical scrolling and bounds the rendered asset list to the first 64, displaying the full recorded count when truncated. `ProductionComposeBehaviorTest.libraryDetailsWithManyAssetsKeepsCloseActionScrollableAndReachable` opens real Details with 80 gateway assets, scrolls to Close, activates it, and verifies dismissal. This newly discovered RMD-1404 layout edge case is implementation evidence only until Android smoke and complete exact-head CI pass; RMD-1802/1803/1805 and global closeout remain unchecked. See `docs/RMD_1802_LIBRARY_DETAILS_LARGE_ASSET_LAYOUT_2026-10-13.md`.

**RMD-1802 Android playback-library root symlink follow-up (2026-10-13; exact-head qualification pending):** `MainActivity.safeLocalPlaybackPath` rejected symlinked media components but first canonicalized the caller-supplied Library root, so a root that was itself a symlink could resolve playable paths in external storage. The helper now rejects a directly symlinked or non-directory root before canonical containment checks. Executable JVM regressions `LibraryIdentityMappingTest.symlinkedLibraryRootCannotResolveExternalPlaybackAssets` and `regularFileCannotServeAsPlaybackLibraryRoot` verify failed Library mapping and preserved external bytes, while existing normal and linked-child cases remain. See `docs/RMD_1802_ANDROID_PLAYBACK_LIBRARY_ROOT_SYMLINK_REVIEW_2026-10-13.md`. As with the Rust download/deletion root protections, this correction is not RMD-1802/1803/1805/global closeout without new exact-head CI and remaining audit.

**RMD-1802 Download Setup resolved-source disclosure review (2026-10-13; exact-head qualification pending):** Successful Analyze/Setup previously rendered provider-resolved canonical URLs and media identity without redaction, potentially exposing signed query parameters. The production preview now redacts/bounds displayed identity and title while retaining the original URL for enqueue. Packaged Compose regression `ProductionComposeBehaviorTest.downloadSetupDoesNotRenderSignedResolvedSourceUrlsOrIdentityTokens` drives Add → Analyze → Setup with synthetic private URL values and verifies redacted display. See `docs/RMD_1802_DOWNLOAD_SETUP_RESOLVED_IDENTITY_DISCLOSURE_REVIEW_2026-10-13.md`. This focused finding does not close RMD-1802/1803/1805 or global proof rules without exact-head qualification and remaining independent review.

**RMD-1802 provider-resolved format-label presentation finding (2026-10-13; qualification pending):** the production Add/Analyze → Download Setup → Advanced Options/Download path carried provider-controlled quality, audio, subtitle, and container labels straight to Compose and scheduling status. Some provider labels may contain signed URLs, HTTP headers, control characters or excessive length. `AddWorkflowPolicy.displayLabel/displaySummary` now applies the shared header/URL diagnostic sanitizer and bounded quality-label formatting *only when rendering*, preserving original quality/audio/subtitle selection-map keys and provider IDs for enqueue. `ProductionComposeBehaviorTest.downloadSetupAndAdvancedOptionsRedactProviderLabelsWithoutChangingSelectionIds` exercises real Compose Analyze → Options → Download with injected secret-bearing choice labels and verifies sanitized render plus unchanged selection ID; `AddWorkflowPolicyTest.displayFormattingRedactsSignedUrlsAndBoundsUntrustedLabelsWithoutChangingMapping` provides fast JVM proof. See `docs/RMD_1802_PROVIDER_FORMAT_LABEL_PRESENTATION_REVIEW_2026-10-13.md`. This is not qualification until exact-head CI including packaged Compose passes, and RMD-1802/1803/1805 and global closeout remain open.

**RMD-1802 playback metadata and track-label presentation follow-up (2026-10-13; exact-head qualification pending):** `LocalPlaybackPolicy` passed provider/persisted media titles into Media3 `MediaItem.mediaMetadata.title`, subtitle labels into `SubtitleConfiguration`, and audio/subtitle track labels into player UI lists without signed URL/header redaction. It now applies shared bounded diagnostic presentation to the MediaSession title and track labels, without changing local asset URIs, track identities, selection indices, durable metadata, or media source construction. Host-JVM `LocalPlaybackPolicyTest.mediaSessionAndTrackPresentationRedactProviderControlledLabels` checks the actual Media3 `MediaItem` and local track-label projection against synthetic credential-bearing provider metadata. See `docs/RMD_1802_PLAYBACK_METADATA_LABEL_REDACTION_2026-10-13.md`. The preceding Download Setup label implementation head `d3fa3ba45d1824315df7877e577402315b4d2da1` has its own exact-head CI matrix, which cannot qualify this new playback change; RMD-1802/1803/1805 and global proof requirements remain unchecked.

**RMD-1802 playback-label tiered qualification correction (2026-10-13):** first exact-head main CI `38373149050` failed because the new host-JVM regression incorrectly constructed Android `MediaItem`/`Uri`, which requires runtime Android APIs. The host test now exercises the actual pure string-projection methods while `PlaybackSessionBehaviorInstrumentedTest.media3ItemSanitizesProviderTitleAndSubtitleLabelsBeforeSessionExposure` validates Media3 `MediaItem`/subtitle configuration metadata on Android. See the qualification-failure addendum in `docs/RMD_1802_PLAYBACK_METADATA_LABEL_REDACTION_2026-10-13.md`. Require a fresh successful exact-head matrix before accepting this fix; no RMD-1802/1803/1805/global closeout is claimed.

**RMD-1802 Library Details no-gateway fabricated-detail correction (2026-10-13; exact-head qualification pending):** independent Android review found that `LibraryScreen` treated the absence of `AppLibraryDetailsGateway` as a successful Details action and displayed cached `LibraryRowModel` title/detail as though the repository had returned authoritative metadata. This masks a production gateway failure and violates RMD-G06's no-fabricated-state intent. `ui/LibraryDownloads.kt` now explicitly reports Library details unavailable and does not open a details panel without a gateway. `ProductionComposeBehaviorTest.library_empty_and_populated_states_are_behavioral` uses the real rendered Details button with a missing gateway and asserts an unavailable message, no manufactured row-derived detail summary, and no details panel. The existing `library_details_action_reads_repository_detail_gateway_off_main_thread` remains the real-gateway success-path qualification. See `docs/RMD_1802_LIBRARY_DETAILS_NO_GATEWAY_FALLBACK_REVIEW_2026-10-13.md`. This is implementation/test evidence only, pending complete exact-head CI and final independent review; leave RMD-1802/1803/1805 and global closeout unchecked.

**RMD-1802 Library rename success-result diagnostic presentation (2026-10-13; qualification pending):** the production rename success path in `LibraryScreen` previously rendered `AppLibraryMutationGateway.renameDisplayTitle`'s returned title without a bound or credential redaction, even though its error path used `SourceMetadataPolicy.diagnostic`. A malformed/imported gateway result could expose signed URL/query credentials or an unbounded label in Compose. The success notice now projects the returned title through `SourceMetadataPolicy.title` before display, without changing the persisted mutation request/response data. Packaged `ProductionComposeBehaviorTest.libraryRenameSuccessDoesNotExposeUntrustedReturnedTitle` clicks the actual Rename/Save controls, injects a returned signed URL, and asserts a redacted success message with no marker. See `docs/RMD_1802_LIBRARY_RENAME_SUCCESS_PRESENTATION_REVIEW_2026-10-13.md`. Do not mark RMD-1802, RMD-1803, RMD-1805 or RMD-G06/G07 complete until the new exact head qualifies and the remaining independent review and closeout requirements are met.

**RMD-1802 Library Details mismatched identity rejection (2026-10-13; qualification pending):** independent Android gateway integrity review found `LibraryScreen` displayed any successful `getDetails(row.id)` result without confirming that `CoreLibraryDetails.itemId` matched the selected row. An inconsistent/malformed gateway reply could expose details from another Library item and falsely represent its identity. The production callback now clears previously expanded details and fails closed with the existing unavailable message when the returned ID differs (including blank IDs); the normal success and error branches remain unchanged. Packaged `ProductionComposeBehaviorTest.libraryDetailsRejectsGatewayRecordForDifferentItem` opens Details for one rendered row, injects a record for another ID through the actual gateway path, and asserts that no foreign title or panel is rendered while the requested ID remains correct. See `docs/RMD_1802_LIBRARY_DETAILS_IDENTITY_VALIDATION_2026-10-13.md`. Source/test changes alone are not RMD-1802/1803/1805 or global closeout; full exact-head qualification and independent review remain mandatory.

**RMD-1802 Library/Downloads row presentation review (2026-10-13; exact-head qualification pending):** `ui/LibraryDownloads.kt` previously rendered persisted Library row titles/details and Downloads row titles/status/size/speed/ETA labels without applying the shared signed-URL/header redaction and length bounds, despite sanitizing the expanded Details panel and download error text. This could disclose provider-controlled credentials in the default collection screens and action/status messages. The production rendering now sanitizes those fields at the presentation boundary, retaining original durable IDs and queue-control arguments. `ProductionComposeBehaviorTest.libraryAndDownloadRowsRedactPersistedProviderMetadataInRealCompose` checks actual Library/Downloads Compose rows, Remove confirmation and Pause action using a synthetic signed URL. See `docs/RMD_1802_LIBRARY_AND_DOWNLOAD_ROW_PRESENTATION_REVIEW_2026-10-13.md`. Leave RMD-1802/1803/1805 and global closeout unchecked until this exact head passes all eight workflows and the remaining independent review is complete.

**RMD-1802 superseded Library Details request review (2026-10-13; exact-head qualification pending):** `LibraryScreen` previously accepted late `AppLibraryDetailsGateway.getDetails` completions without checking whether the user had selected a different Library row. A slow earlier request could overwrite newer details even when its returned ID matched its own originally requested row. The production callback now ignores stale success and error results using a selection generation. Packaged `ProductionComposeBehaviorTest.libraryDetailsDoesNotPublishSupersededGatewayResponse` holds the first real gateway call until a second selection is displayed, releases the first response, and asserts the newer detail remains authoritative. See `docs/RMD_1802_LIBRARY_DETAILS_SUPERSEDED_REQUEST_REVIEW_2026-10-13.md`. This is implementation/test evidence only, not RMD-1802/1803/1805 closeout until the new exact head passes all eight workflows and the independent review is complete.

- [ ] Perform a new independent code review of Rust and Android production paths.
- [ ] Search for remaining production no-op callbacks.
- [ ] Search for hard-coded empty/fabricated production data.
- [ ] Search for policy-only tests being cited as behavioral proof.
- [ ] Search for dead/declarative capability flags that disagree with runtime behavior.
- [ ] Resolve newly found release-blocking issues.

**RMD-1802 additional Android safety findings (2026-10-13; new exact-head CI required):** `SourceMetadataPolicy` previously preserved credentials in nested HTTP(S) URLs embedded in a parsed URL path; it now redacts such URLs entirely (including encoded credential-bearing paths), with host-JVM metadata/diagnostic regressions. `DownloadExecutionSchedulerPolicy` now normalizes negative UIDT estimated-download-byte inputs to `JobInfo.NETWORK_BYTES_UNKNOWN` before building a platform job, with host-JVM boundary regressions. See `docs/RMD_1802_NESTED_URL_AND_UIDT_ESTIMATE_REVIEW_2026-10-13.md`. This is implementation evidence only; leave RMD-1802/1803/1805 and global closeout open until all required exact-head CI and independent review complete.

**RMD-1802 host Wi-Fi metered E2E failure diagnostics (2026-10-13; exact-head qualification pending):** Android real-network E2E run `38387651853` passed its first genuine OS network-loss/resume instrumentation, but the subsequent Wi-Fi metered/unmetered phase ended before the `wifi-ready` marker. The host harness printed the *first* instrumentation log instead of the failing second log, concealing the test's actual assertion/failure. `scripts/run_android_os_network_e2e.sh` now accepts a per-phase failure log in `wait_signal` and prints the Wi-Fi metered instrumentation log on missing markers/timeouts. See `docs/RMD_1802_WIFI_METERED_HOST_FAILURE_DIAGNOSTICS_2026-10-13.md`. This is diagnostic hardening, not evidence of successful metered-network acceptance; keep final exact-head qualification and RMD-1802/1803/1805 open.

**RMD-1802 UIDT uncaught worker exception review (2026-10-13; qualification pending):** `DownloadUserInitiatedJobService` used a plain Java/Kotlin `Thread` around `runAndSettleDownloadJob`. Although `finally` called `jobFinished`, an unexpected worker exception still escaped the thread and could terminate the Android process via its uncaught-exception handler. The UIDT thread now uses `runAndSettleDownloadJobSafely`, which retains exactly-once settlement and emits only a bounded, non-sensitive failure notice rather than rethrowing; `DownloadExecutionSchedulerPolicyTest.unexpectedUidtWorkerExceptionDoesNotEscapeAfterDurableJobSettlement` exercises failure and success paths. See `docs/RMD_1802_UIDT_UNCAUGHT_WORKER_FAILURE_2026-10-13.md`. Do not close RMD-1802/1803/1805 until exact-head CI and independent review are complete.

**RMD-1802 foreground-service callback thread crash review (2026-10-13; qualification pending):** `DownloadForegroundService` launched plain threads for Android connectivity events, observer reuse, and worker settlement. Those entry points could throw from the generated UniFFI/SQLite gateway (notably during app-private database reset), and an uncaught background-thread exception would terminate the Android process. The service now runs these callbacks through `runForegroundServiceTaskSafely` with fixed non-sensitive warnings; the worker still releases ownership and clears its running flag in `finally`. A host-JVM regression covers both failure containment and the successful path. See `docs/RMD_1802_FOREGROUND_CALLBACK_THREAD_FAILURE_2026-10-13.md`. This does not close RMD-1802/1803/1805 without exact-head CI and the remaining independent review.

### RMD-1803 — Exact-head full qualification

**Interim exact-head evidence and golden-coverage audit (2026-10-12; NOT final candidate freeze):** `docs/RMD_1803_EXACT_HEAD_AND_GOLDEN_COVERAGE_AUDIT_2026-10-12.md` records all eight successful standard workflow runs on exact `master` implementation `03ae38b441cbf14108473efec85edb09b3decdd6`: CI `38316280118`, Android smoke `38316280138`, Android FGS timeout `38316280232`, Supply chain `38316280206`, CI evidence `38316280207`, Deterministic E2E fixture `38316280103`, Android cold start `38316280182`, and Android real-network E2E `38316280067`. The independently enumerated `ProductionComposeGoldenTest` has 12 executable, SHA-256-pinned raster methods; `GoldenCoveragePolicy.RequiredScenarios` is a **22-item target inventory**, not equivalent screenshot proof. Missing explicit per-surface/profile raster combinations and the unproven compact-device viewport of the named compact fixture are documented without inflating qualification. These passing runs qualify that historical exact SHA, not later documentation commits or unreviewed changes; RMD-1802 independent review, RMD-1803 final freeze, RMD-1804/1805, and external service/legal approval remain OPEN. No completion checkbox is changed by this evidence entry.


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


### RMD-1805 — Engineering definition of done

All items below must be true before engineering closeout:

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

---


## Suggested execution order

The dependency-aware implementation order is:


1. **RMD-000** truthful baseline/tracking.
2. **RMD-100 + RMD-200** platform and actual Rust/Android integration.
3. **RMD-400** core correctness fixes while integration foundations settle.
4. **RMD-300** production YouTube source.
5. **RMD-600 + RMD-1100** repositories, ViewModels, durable settings.

6. **RMD-500** real download scheduling/orchestration and controls.
7. **RMD-700 + RMD-800** Add/Share and asset pipelines.
8. **RMD-900** unified playback.
9. **RMD-1000 + RMD-1200 + RMD-1300** operational UX, recovery, security.
10. **RMD-1400 + RMD-1500** real Android qualification and E2E.

11. **RMD-1600 + RMD-1700** CI/supply chain/docs reconciliation.
12. **RMD-1800** independent re-review, exact-head qualification, merge, and final verification.


Parallel work is allowed when dependencies are respected, but no downstream acceptance checkbox may be checked using a fake gateway/policy object in place of the production path it claims to qualify.
