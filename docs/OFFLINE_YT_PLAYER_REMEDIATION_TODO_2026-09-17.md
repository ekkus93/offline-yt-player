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

- [ ] Add the remediation spec and this TODO to `docs/`.
- [ ] Record baseline SHA `b8aebfd0467de67a2cc0b0a583d91f9a1783da7c`.
- [ ] Add a short `docs/REMEDIATION_BASELINE_AUDIT.md` mapping each code-review finding to a remediation task ID.
- [ ] Link the prior detailed TODO from git history for historical acceptance criteria.
- [ ] Explicitly document that the previous final reconciliation changed checklist state without corresponding implementation for all claimed items.

**Acceptance:** every review finding is mapped to at least one unchecked task in this file.

### RMD-002 — Add anti-false-closeout reconciliation rules

- [ ] Document evidence required before checking a task: implementation path, behavioral test path, exact SHA/run.
- [ ] Require task reconciliation to reference evidence rather than narrative assertions.
- [ ] Add a final script/test that fails if the detailed remediation TODO contains unchecked items during a release-closeout workflow.
- [ ] Ensure that script does not mutate or auto-check the TODO.

**Acceptance:** a documentation-only summary cannot make unresolved detailed tasks appear complete.

---

## RMD-100 — Android manifest and platform compliance

### RMD-101 — Restore network capability

- [ ] Add `android.permission.INTERNET` to `app/src/main/AndroidManifest.xml`.
- [ ] Retain only permissions actually needed by implemented runtime paths.
- [ ] Add a manifest-level test/assertion for required network permission.
- [ ] Add an Android runtime fixture test that performs a local deterministic HTTP request through the packaged app/core path.

**Acceptance:** the Android app can open the deterministic fixture network endpoint on an emulator/device.

### RMD-102 — Replace illegal/fragile boot-start behavior

- [ ] Stop launching a `dataSync` foreground service directly from `BOOT_COMPLETED` on target SDK 35+.
- [ ] Define boot recovery as durable-state reconciliation plus legal future scheduling.
- [ ] Decide whether `LOCKED_BOOT_COMPLETED` is actually required.
- [ ] If not required, remove it.
- [ ] If required, mark the receiver Direct-Boot-aware and move only necessary pre-unlock state to device-protected storage.
- [ ] Ensure credential-encrypted DB/media are not opened before unlock.
- [ ] Add API-appropriate boot/restart tests.

**Acceptance:** reboot recovery never throws `ForegroundServiceStartNotAllowedException` and never accesses unavailable credential-protected state.

### RMD-103 — Adopt a compliant download runtime by API level

- [ ] Introduce an Android `DownloadExecutionScheduler` abstraction.
- [ ] On API 34+, implement User-Initiated Data Transfer jobs for user-requested downloads.
- [ ] Add `android.permission.RUN_USER_INITIATED_JOBS` for the API 34+ path.
- [ ] Supply required network constraints and estimated bytes when known.
- [ ] Attach/update the required UIDT notification.
- [ ] On API 26-33, implement and document a compatible foreground/background transfer fallback.
- [ ] Keep both paths on the same durable queue/control model.
- [ ] Add tests for scheduler selection by SDK.

**Acceptance:** a user-triggered download is scheduled legally on API 26-33 and API 34+ without duplicating domain state.

### RMD-104 — Handle foreground-service timeout paths if retained

- [ ] Inventory every remaining `dataSync`/`mediaProcessing` foreground service.
- [ ] Implement `Service.onTimeout(...)` for any path subject to Android 15+ time limits.
- [ ] Persist resumable state before stopping.
- [ ] Add ADB/emulator qualification using shortened foreground-service timeout where applicable.

**Acceptance:** forced timeout ends cleanly without a fatal `RemoteServiceException` and without corrupting download state.

### RMD-105 — Notification permission behavior

- [ ] Add Android 13+ notification-permission UX where required.
- [ ] Define behavior when permission is denied.
- [ ] Ensure denial cannot corrupt or silently misreport queue state.
- [ ] Add instrumentation coverage for granted/denied state where feasible.

---

## RMD-200 — Real Rust/Android UniFFI integration

### RMD-201 — Package Rust native libraries into the APK

- [ ] Define supported Android ABIs for v1.
- [ ] Build Rust `cdylib` for each supported ABI.
- [ ] Copy/package `.so` files through Gradle/JNI libs or an equivalent deterministic mechanism.
- [ ] Verify packaged APK contains each required native library.
- [ ] Fail CI when an expected ABI library is absent.

### RMD-202 — Compile generated UniFFI Kotlin bindings into the app

- [ ] Make binding generation reproducible from the Rust interface.
- [ ] Add generated sources to the Android compile source set or consume them from a generated module/artifact.
- [ ] Prevent stale checked/generated bindings from silently diverging.
- [ ] Add CI diff/consistency verification.

### RMD-203 — Create an app-owned core gateway

- [ ] Add a stable Kotlin interface wrapping generated UniFFI services.
- [ ] Centralize FFI model/error conversion.
- [ ] Ensure blocking calls execute off the Android main thread.
- [ ] Define cancellation/lifecycle semantics.
- [ ] Expose repository/state APIs suitable for ViewModels.
- [ ] Provide a fake implementation for deterministic Android UI tests.

### RMD-204 — Android runtime FFI smoke test

- [ ] Add at least one `androidTest` that loads the packaged native library.
- [ ] Execute a real representative FFI call.
- [ ] Round-trip representative records/errors.
- [ ] Exercise a temporary app-private DB/media root.

**Acceptance for RMD-200:** production Kotlin imports/calls the packaged generated core interface; FFI is no longer a separate CI-only artifact.

---

## RMD-300 — Production YouTube source adapter

### RMD-301 — Implement the live production `MediaSource`

- [ ] Add a concrete YouTube `MediaSource` distinct from `DirectFixtureSource`.
- [ ] Register it in the production `SourceRegistry`.
- [ ] Support the URL forms documented by the original strategy decision.
- [ ] Resolve canonical source identity.
- [ ] Resolve real title/duration/thumbnail metadata.
- [ ] Discover available media formats.
- [ ] Produce executable provider-neutral download plans.
- [ ] Discover subtitles where supported.
- [ ] Apply strict response/metadata bounds.

### RMD-302 — Keep provider logic isolated

- [ ] Keep provider response types/parsers inside the YouTube adapter.
- [ ] Do not expose provider-specific payloads through Android UI models.
- [ ] Convert failures to structured source diagnostics.
- [ ] Distinguish unsupported URL, source changed/parser failure, network failure, and unavailable media.

### RMD-303 — Add deterministic production-adapter fixtures

- [ ] Store bounded sanitized fixtures for representative provider responses.
- [ ] Test metadata extraction.
- [ ] Test combined A/V formats.
- [ ] Test separate A/V formats.
- [ ] Test unavailable/private/changed-source responses.
- [ ] Test malformed/oversized responses.

### RMD-304 — Controlled live-source qualification

- [ ] Add an opt-in/manual or appropriately isolated live-source smoke path that is not required to leak secrets into CI.
- [ ] Document its policy/legal prerequisites.
- [ ] Record expected failure behavior when provider structure changes.

**Acceptance:** OYP-703 can only be considered repaired when the production registry can resolve a supported real URL; fixture-only resolution is insufficient.

---

## RMD-400 — Rust core correctness fixes

### RMD-401 — Make retryability authoritative

- [ ] Refactor retry classification so `CoreError.retryable == false` cannot become retryable due only to `ErrorKind`.
- [ ] Add regression test: HTTP 404/nonretryable status does not retry.
- [ ] Add regression test: source-change/nonretryable provider failure does not enter generic retry loop.
- [ ] Add regression tests for retryable transient statuses/network errors.

### RMD-402 — Unify retry policy with production scheduler

- [ ] Make `DownloadPolicy.max_attempts` authoritative in production or remove it in favor of one authoritative retry policy.
- [ ] Persist attempt count/next eligible retry time.
- [ ] Ensure process death preserves retry semantics.
- [ ] Bound exponential backoff and jitter.
- [ ] Inject/abstract clock/randomness where needed for deterministic tests.

### RMD-403 — Separate network-read I/O from filesystem I/O

- [ ] Do not send remote response-body read errors through the generic local `io_error` storage mapper.
- [ ] Classify socket reset/timeout/truncation correctly.
- [ ] Preserve retryability where appropriate.
- [ ] Add fixture tests for mid-body disconnect and timeout.
- [ ] Verify local ENOSPC/write failures still map to storage errors.

### RMD-404 — Make deletion remove owned assets

- [ ] Define deletion transaction/state machine for metadata plus files.
- [ ] Delete video/audio/thumbnail/subtitle/partial assets owned by the item.
- [ ] Prevent traversal/out-of-root deletion.
- [ ] Surface file-delete failures explicitly.
- [ ] Add interrupted-deletion reconciliation.
- [ ] Add tests proving files are gone after successful delete.

### RMD-405 — Use stored hashes for corruption detection

- [ ] Preserve cheap existence/size checks where appropriate.
- [ ] Add SHA-256 verification when a stored hash exists during explicit/deep validation or suspected corruption.
- [ ] Add same-length corruption regression test.
- [ ] Map corruption to a repairable/user-visible state.

### RMD-406 — Redact network diagnostics

- [ ] Replace raw `reqwest::Error` user-facing text with structured safe diagnostics.
- [ ] Strip/redact URLs, query strings, signed parameters, tokens, and sensitive filesystem details.
- [ ] Add tests with synthetic signed URLs/secrets.
- [ ] Verify logs and FFI error messages contain no injected secret markers.

### RMD-407 — Deterministic quality ranking

- [ ] Rank formats by compatibility before deduplicating equal heights.
- [ ] Prefer direct-play combined streams where product policy says so.
- [ ] Prefer compatible split A/V over mux-required variants where appropriate.
- [ ] Add deterministic tie-breakers for codec/bitrate/fps/format ID.
- [ ] Add tests where provider order is intentionally adversarial.

### RMD-408 — Reconcile concurrency/resource-policy duplication

- [ ] Identify conflicting core/Android concurrency constants.
- [ ] Establish one maximum enforced by core and one user preference bounded by it.
- [ ] Ensure Android cannot request a value above the core bound.
- [ ] Add mapping tests.

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

- [ ] Propagate transferred/total bytes.
- [ ] Calculate speed from bounded recent samples.
- [ ] Show ETA only when meaningful.
- [ ] Never fabricate numeric progress for unknown-length responses.

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

- [ ] Discover thumbnail URL through source adapter.
- [ ] Download through bounded safe transfer path.
- [ ] Store as managed local asset.
- [ ] Persist asset metadata.
- [ ] Render from local file in Library/Setup/Details while offline.
- [ ] Delete thumbnail with owning item.
- [ ] Detect/recover missing/corrupt thumbnail.

### RMD-802 — Subtitle model and lifecycle

- [ ] Persist language identity.
- [ ] Persist subtitle format/MIME identity.
- [ ] Download selected subtitle track as managed local asset.
- [ ] Validate supported format.
- [ ] Delete with owning item.
- [ ] Attach to local playback.

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

- [ ] Call core startup reconciliation from an appropriate production initialization path.
- [ ] Reconcile interrupted active transfers.
- [ ] Reconcile staged/partial/orphan files.
- [ ] Reconcile metadata/file mismatches.
- [ ] Surface repairable failures.

### RMD-1202 — Process-death test

- [ ] Start fixture download.
- [ ] Persist partial progress.
- [ ] Kill process abruptly.
- [ ] Relaunch.
- [ ] Reconstruct durable queue.
- [ ] Resume/restart according to validator policy.
- [ ] Complete with correct integrity and one library item.

### RMD-1203 — Boot-recovery test

- [ ] Prepare durable interrupted work.
- [ ] Simulate/reboot emulator where CI infrastructure supports it.
- [ ] Verify receiver/reconciliation path.
- [ ] Verify no forbidden `dataSync` FGS boot launch.
- [ ] Verify work remains recoverable and is scheduled only when legal.

### RMD-1204 — Corrupt-state recovery UI

- [ ] Convert corruption policy into real application state/actions.
- [ ] Handle missing media.
- [ ] Handle size/hash mismatch.
- [ ] Handle unsupported/newer DB schema.
- [ ] Handle damaged DB according to documented strategy.
- [ ] Provide safe diagnostic/export/reset choices as applicable.

---

## RMD-1300 — Security, privacy, and resource bounds

### RMD-1301 — Unify Android/core URL validation

- [ ] Define one supported URL contract.
- [ ] Align Share/Add validation with core source recognition.
- [ ] Reject unsupported schemes/hosts/oversized inputs consistently.
- [ ] Add adversarial tests.

### RMD-1302 — Secret/log hygiene end to end

- [ ] Audit Android logs.
- [ ] Audit Rust logs/errors.
- [ ] Audit notifications/user-visible diagnostics.
- [ ] Inject synthetic tokens/signed query parameters in tests.
- [ ] Assert they never appear in CI-visible outputs.

### RMD-1303 — File/path safety for all mutations

- [ ] Apply safe-root/path validation to download, delete, rename, thumbnail, subtitle, cleanup, and recovery operations.
- [ ] Add traversal/symlink/adversarial path tests appropriate to platform/filesystem semantics.

### RMD-1304 — Provider/resource bounds

- [ ] Bound provider response size.
- [ ] Bound URL/metadata lengths.
- [ ] Bound redirects/timeouts/asset sizes.
- [ ] Bound concurrent downloads.
- [ ] Bound retry attempts.
- [ ] Add tests for each enforced limit.

---

## RMD-1400 — Real Android UI qualification

### RMD-1401 — Establish `androidTest` infrastructure

- [ ] Add required AndroidX test/Compose test dependencies.
- [ ] Create emulator-compatible instrumentation setup.
- [ ] Ensure CI executes `connected...AndroidTest` or managed-device equivalent.
- [ ] Upload useful failure artifacts/screenshots.

### RMD-1402 — Replace policy-only screen qualification with behavioral Compose tests

- [ ] Library empty/populated behavior.
- [ ] Add input/paste/analyze behavior.
- [ ] Download Setup choices/actions.
- [ ] Downloads state/actions.
- [ ] Player controls.
- [ ] Settings persistence/interaction.
- [ ] Share navigation/back stack.

### RMD-1403 — Deterministic screenshot/golden tests

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

### RMD-1404 — No-hidden-controls behavioral gate

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

- [ ] Rust fmt.
- [ ] Rust clippy with warnings denied.
- [ ] Rust unit/integration tests.
- [ ] Android lint.
- [ ] Android JVM tests.
- [ ] Android assemble/package.
- [ ] UniFFI generation consistency.
- [ ] Android Rust ABI builds.
- [ ] APK native-library packaging verification.
- [ ] Android instrumentation/Compose tests.
- [ ] Screenshot/golden tests.
- [ ] Deterministic E2E fixture lane.
- [ ] Exact-head identity assertion.

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