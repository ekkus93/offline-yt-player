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

- [ ] Expose durable queued/active/paused/waiting/retrying/failed/cancelled/completed states through the core gateway.
- [ ] Ensure state survives process death.
- [ ] Eliminate service-local booleans/constants as authoritative queue state.

### RMD-502 — Implement worker execution loop

- [ ] Claim eligible durable work safely.
- [ ] Enforce configured concurrency.
- [ ] Execute the real core download plan.
- [ ] Emit/persist progress at bounded cadence.
- [ ] Commit completion only after integrity and asset promotion succeed.
- [ ] Release/repair claimed work after cancellation/process death.

### RMD-503 — Implement Pause

- [ ] UI action calls real control gateway.
- [ ] Notification action calls same control path.
- [ ] Worker reaches a bounded cancellation point.
- [ ] Durable resumable state is persisted.
- [ ] Partial asset is retained only according to resume policy.
- [ ] Add behavioral tests.

### RMD-504 — Implement Resume

- [ ] Resume transitions a paused item to eligible work.
- [ ] Revalidate continuation metadata before range append.
- [ ] Honor current network/settings policy.
- [ ] Add process-death + resume regression test.

### RMD-505 — Implement Cancel

- [ ] Cancel stops active work.
- [ ] Remove/quarantine partial assets according to policy.
- [ ] Persist terminal cancelled state.
- [ ] Cancel from notification and UI uses same code path.

### RMD-506 — Implement Retry

- [ ] Retry is available only for eligible failed states.
- [ ] Do not create duplicate library/source identities.
- [ ] Reset only appropriate attempt/error fields.
- [ ] Honor maximum-attempt/user-action semantics.

### RMD-507 — Implement real progress/speed/ETA

- [x] Propagate transferred/total bytes.
- [x] Calculate speed from bounded recent samples.
- [x] Show ETA only when meaningful.
- [x] Never fabricate numeric progress for unknown-length responses.

**Evidence (RMD-507):** `core/src/events.rs::TransferMetricEstimator` computes speed from a bounded recent sample window and emits ETA only when a meaningful total is known. `core/src/worker.rs::DownloadWorker` seeds the estimator from durable transferred/total byte state, updates it from real transfer results, emits bounded-cadence `CoreEvent::DownloadProgress` events, and preserves unknown-length transfers as unknown-total/unknown-ETA rather than fabricating percentage or ETA. Android presentation mapping preserves the invariant through `DownloadProgressPresentationMapper` and `DownloadRowPresentation`. Behavioral coverage includes `worker_emits_real_progress_bytes_speed_and_eta_for_known_length_transfer`, `worker_does_not_fabricate_eta_for_unknown_length_transfer`, `DownloadRowPolicyTest.unknownLengthProgressDoesNotFabricatePercentageOrEta`, and `DownloadRowPolicyTest.progressMapperKeepsOnlyRealPositiveMetrics`. Supporting implementation/evidence docs are `docs/RMD_507_PROGRESS_METRICS_IMPLEMENTATION_2026-09-24.md` and `docs/RMD_507_PROGRESS_METRICS_RECONCILIATION_2026-09-24.md`. Qualified/merged evidence: PR #354 exact implementation head `4b881b06d60540b2a8c7e15e3dfdb1cb63b54f63` passed push CI `35983598837`, push Android smoke `35983599037`, push Android FGS timeout `35983599025`, PR CI `35984323724`, PR Android smoke `35984323707`, and PR Android FGS timeout `35984323690`; PR #354 merged as `72b2af7a85576250655eb10fdd08de48923fec46`; post-merge master CI `35986175688`, Android smoke `35986175695`, and Android FGS timeout `35986175679` passed on that exact merge SHA. Evidence PR #355 exact head `58bd8c1fed59d7f6a6a0672b0e8055a6164691b0` passed PR CI `35986430735`, PR Android smoke `35986430765`, PR Android FGS timeout `35986430829`, push CI `35986393668`, push Android smoke `35986393598`, and push Android FGS timeout `35986393706`; PR #355 merged as `ebb66af299320c6d03d1de44a5efb5ba1352627e`, with post-merge master CI `35988142447`, Android smoke `35988142417`, and Android FGS timeout `35988142407` passing on that exact merge SHA.

### RMD-508 — Connectivity integration

- [ ] Observe Android network capability changes.
- [ ] Map to core/app connectivity state.
- [ ] Pause/wait when no usable network exists.
- [ ] Enforce Wi-Fi/unmetered preference.
- [ ] Automatically make waiting work eligible when constraints return.
- [ ] Add instrumentation tests for transitions.

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

- [x] Bound provider response size.
- [x] Bound URL/metadata lengths.
- [x] Bound redirects/timeouts/asset sizes.
- [x] Bound concurrent downloads.
- [x] Bound retry attempts.
- [x] Add tests for each enforced limit.

**Evidence (RMD-1304):** resource bounds are centralized in `core/src/resource_bounds.rs` for provider response size, provider/media/caption/thumbnail URL lengths, metadata length, redirect count, and provider HTTP timeout policy. `core/src/youtube_source.rs` applies those bounds to production YouTube parsing/fetch behavior; `core/src/download.rs` enforces declared/observed transfer asset-size limits; `core/src/concurrency.rs` enforces `MAX_CONCURRENT_DOWNLOADS` and preference clamping; and `core/src/worker.rs` consumes `DownloadPolicy.max_attempts` as the bounded retry policy. Deterministic Rust tests cover provider response-size rejection, provider URL bounds, title truncation, redirect/timeout policy, stream/subtitle/thumbnail/metadata parsing limits, asset-size enforcement, concurrency ceilings, and retry-attempt limits. Qualified/merged evidence: PR #316 merged as `da8ea3050809a975cada87f8ad785e236783c94d` from exact implementation head `a21040f9745e3cc943178f7723b7d193e1f04ae7`, with push CI `35800979863`, PR CI `35801483348`, and post-merge master CI `35801968234` passing; supporting reconciliation is recorded in `docs/RMD_1304_RESOURCE_BOUNDS_RECONCILIATION_2026-09-22.md`.

---

## RMD-1400 — Real Android UI qualification

### RMD-1401 — Establish `androidTest` infrastructure

- [x] Add required AndroidX test/Compose test dependencies.
- [x] Create emulator-compatible instrumentation setup.
- [x] Ensure CI executes `connected...AndroidTest` or managed-device equivalent.
- [x] Upload useful failure artifacts/screenshots.

**Evidence (RMD-1401):** Android instrumentation infrastructure is established by `app/src/androidTest/java/com/ekkus/offlineytplayer/AndroidRuntimeSmokeTest.kt`, Gradle packaging/build support for emulator-compatible `x86_64` native Rust libraries alongside `arm64-v8a`, fast-gate APK/native-library verification, and `.github/workflows/android-smoke.yml`. The smoke workflow runs `connectedDebugAndroidTest` on an API-29 AOSP x86_64 emulator scoped to `com.ekkus.offlineytplayer.AndroidRuntimeSmokeTest`, preserving bounded Gradle reports and logcat/artifact evidence on failure. This is intentionally the smallest runtime smoke lane required by the acceleration plan; behavioral Compose, golden, accessibility/layout, and deterministic E2E qualification remain open under RMD-1402 through RMD-1507 and final RMD-1803 closeout. Qualified/merged evidence: PR #318 merged as `751b1762861f795aed1245c1fe48b9e521019856` from exact implementation head `a0f3fd7970ce7dbe3cb697ac082f902ba4c2c5e9`, with PR CI `35809009062` and Android-smoke run `35809009026` passing on exact head, then post-merge master CI `35812405751` and post-merge Android-smoke run `35812405807` passing on merge commit `751b1762861f795aed1245c1fe48b9e521019856`.

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

### RMD-1504 — Share E2E

- [ ] Send `ACTION_SEND text/plain` fixture/supported input.
- [ ] Enter real analysis/setup pipeline.
- [ ] Schedule/download.
- [ ] Verify Library state.
- [ ] Verify back-stack behavior.

### RMD-1505 — Storage failure E2E

- [ ] Exercise insufficient-space preflight.
- [ ] Exercise write failure/ENOSPC path where infrastructure permits.
- [ ] Verify partial cleanup/recoverability.
- [ ] Verify user-visible actionable failure.
- [ ] Verify no false completed library record.

### RMD-1506 — Connectivity E2E

- [ ] Start transfer.
- [ ] Remove network.
- [ ] Verify waiting/pause state.
- [ ] Restore eligible network.
- [ ] Verify legal resume.
- [ ] Repeat with Wi-Fi-only/metered policy where emulator controls permit.

### RMD-1507 — Notification-control E2E

- [ ] Pause from notification.
- [ ] Resume from notification.
- [ ] Cancel from notification.
- [ ] Verify durable state/UI mirrors each action.

**Acceptance for RMD-1500:** policy enum sequence tests may remain, but they cannot be cited as the E2E evidence for these tasks.

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
- [ ] Deterministic E2E fixture lane.
- [x] Exact-head identity assertion.

**Evidence (RMD-1601 partial reconciliation):** The fast deterministic matrix is already implemented on current master. `.github/workflows/ci.yml` runs exact-commit identity checks, Rust fmt, clippy with `-D warnings`, workspace tests, Android lint/JVM tests/assemble, reproducible UniFFI generation, both supported Android Rust ABI builds, and APK native-library verification. `.github/workflows/android-smoke.yml` also asserts exact commit identity and runs the packaged instrumentation/Compose suite including `ProductionComposeBehaviorTest`. Exact master `dc18f27c01620cd7b3254b1c5bb8d8f9b15b08eb` passed CI run `36186074873` and Android smoke run `36186075209`. Screenshot/golden qualification is now implemented and qualified under RMD-1403 through RMD-1405 and `.github/workflows/android-smoke.yml`; deterministic fixture E2E remains deliberately unchecked until RMD-1500 is implemented and qualified.

### RMD-1602 — Dependency/advisory checks

- [ ] Add Rust vulnerability/advisory scanning with an explicitly reviewed exception mechanism.
- [ ] Add Android/Gradle dependency vulnerability/license review tooling where practical.
- [ ] Generate/reconcile OSS license notices for shipped dependencies.
- [ ] Fail release qualification on unresolved prohibited/license-incompatible dependencies.

### RMD-1603 — CI evidence quality

- [ ] Ensure failures preserve logs/test reports/screenshots.
- [ ] Ensure emulator/E2E artifacts are bounded and useful.
- [ ] Record exact candidate SHA in final qualification report.

---

## RMD-1700 — Documentation reconciliation

### RMD-1701 — README truthfulness

- [ ] Update project status to match actual implementation after remediation.
- [ ] Describe supported/unsupported workflows.
- [ ] Document build/run prerequisites.
- [ ] Keep external release gate explicit.

### RMD-1702 — Build and FFI docs

- [ ] Document Android ABI build flow.
- [ ] Document UniFFI generation and Gradle integration.
- [ ] Document emulator/device setup.
- [ ] Document common native-loading failures.

### RMD-1703 — Background execution docs

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

### RMD-1705 — Security/privacy/legal docs

- [ ] Update input/security model.
- [ ] Update diagnostic/redaction guarantees.
- [ ] Update storage/deletion behavior.
- [ ] Keep YouTube/service-policy/legal approval external and unresolved unless separately approved by a human authority.

### RMD-1706 — Supersede misleading prior audits

- [ ] Add a remediation reconciliation document explaining which prior audit claims were corrected.
- [ ] Do not delete historical audit docs; mark/supersede them clearly where their closeout claims are no longer authoritative.

---

## RMD-1800 — Final engineering closeout

### RMD-1801 — Detailed TODO reconciliation

- [ ] Review every RMD task and subtask against current `master` code.
- [ ] For each completed milestone, cite implementation paths and behavioral tests.
- [ ] Confirm zero unchecked engineering subtasks except explicitly external release approval, which must not be represented as engineering-complete approval.
- [ ] Do not collapse this TODO.

### RMD-1802 — Code review after remediation

- [ ] Perform a new independent code review of Rust and Android production paths.
- [ ] Search for remaining production no-op callbacks.
- [ ] Search for hard-coded empty/fabricated production data.
- [ ] Search for policy-only tests being cited as behavioral proof.
- [ ] Search for dead/declarative capability flags that disagree with runtime behavior.
- [ ] Resolve newly found release-blocking issues.

### RMD-1803 — Exact-head full qualification

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
