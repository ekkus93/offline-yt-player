# Offline YT Player — Implementation TODO

This TODO implements `docs/OFFLINE_YT_PLAYER_SPEC.md`.

Task IDs are stable. A task is complete only when implementation, automated qualification, documentation, and TODO reconciliation are all complete.

## Global rules

- Android is **portrait only**. Do not create or maintain landscape layouts.
- Primary controls may not require scrolling to discover/reach them.
- Only naturally unbounded content regions may scroll.
- When controls do not fit, split the screen into subpages/tabs instead of hiding them below the fold.
- No important action may be gesture-only.
- Rust owns portable domain/download/source logic; Android owns presentation, lifecycle, Media3, and platform integration.
- YouTube logic must stay behind a replaceable source adapter.
- Exact-head CI evidence is required before closeout of a milestone.

---

## OYP-000 — Repository and project bootstrap

### OYP-001 — Establish repository structure
- [x] Create Rust workspace.
- [x] Create Android Gradle project.
- [x] Create `core/`, Android app, test, and documentation boundaries.
- [x] Add `.editorconfig`, `.gitignore`, license decision placeholder, and contribution/build notes.

Acceptance:
- [x] Clean checkout has documented bootstrap commands.
- [x] Rust and Android skeletons build in CI.

Evidence: `docs/BOOTSTRAP_CI_AUDIT.md`; exact-head master CI `35033596290` passed at `23fc6c047931ebbc82d71be681296a1e07f493e7`.

### OYP-002 — Pin toolchains
- [x] Pin Rust toolchain.
- [x] Pin Java/JDK version.
- [x] Pin Android Gradle Plugin, Kotlin, Compose BOM, and Gradle wrapper.
- [x] Document supported Android SDK/minSdk/targetSdk values.

Acceptance:
- [x] CI uses the same declared toolchain versions as local documentation.

Evidence: `docs/BOOTSTRAP_CI_AUDIT.md`; exact-head master CI `35033596290` passed at `23fc6c047931ebbc82d71be681296a1e07f493e7`.

### OYP-003 — Baseline CI
- [x] Add Rust fmt/clippy/test jobs.
- [x] Add Android lint/unit/build jobs.
- [x] Add exact commit SHA reporting.
- [x] Add dependency cache without making correctness cache-dependent.

Evidence: `docs/BOOTSTRAP_CI_AUDIT.md`; exact-head master CI `35033596290` passed at `23fc6c047931ebbc82d71be681296a1e07f493e7`.

---

## OYP-100 — Rust core foundation

### OYP-101 — Domain models
- [x] Define source identity.
- [x] Define `MediaInfo`.
- [x] Define media/audio/video/subtitle format models.
- [x] Define curated quality-choice model distinct from raw streams.
- [x] Define local library item model.
- [x] Define typed error taxonomy.

Evidence: `docs/CORE_FOUNDATION_AUDIT.md`; exact-head master CI `35033596290` passed at `23fc6c047931ebbc82d71be681296a1e07f493e7`.

### OYP-102 — Download state machine
- [x] Define queued/resolving/downloading/paused/retry-wait/failed/verifying/completed/canceled states.
- [x] Define legal transitions.
- [x] Reject impossible transitions.
- [x] Add exhaustive state-transition tests.

Evidence: `docs/CORE_FOUNDATION_AUDIT.md`; exact-head master CI `35033596290` passed at `23fc6c047931ebbc82d71be681296a1e07f493e7`.

### OYP-103 — Core event model
- [x] Define coarse progress/update events for UI consumption.
- [x] Prevent high-frequency FFI chatter.
- [x] Define durable vs ephemeral state.

Evidence: `docs/CORE_FOUNDATION_AUDIT.md`; exact-head master CI `35033596290` passed at `23fc6c047931ebbc82d71be681296a1e07f493e7`.

---

## OYP-200 — Android portrait-only application shell

### OYP-201 — Compose application
- [x] Create Kotlin application module.
- [x] Add Material 3 Compose setup.
- [x] Apply Midnight Transit semantic color tokens.
- [x] Implement typography/spacing/radius tokens.

### OYP-202 — Portrait-only enforcement
- [x] Lock app activity to portrait orientation.
- [x] Remove landscape resource assumptions.
- [x] Add automated manifest/configuration test where practical.
- [x] Document portrait-only support policy.

Acceptance:
- [x] Rotating a test device does not switch the application into a landscape layout.

### OYP-203 — Navigation shell
- [x] Implement fixed bottom navigation: Library, Downloads, Add, Settings.
- [x] Library is launch destination.
- [x] Preserve destination state appropriately.
- [x] No drawer or horizontal nav carousel.

### OYP-204 — Fixed-region layout primitives
- [x] Create reusable scaffold for fixed top bar, bounded content region, fixed actions, and bottom nav.
- [x] Support system insets.
- [x] Add tests that primary actions remain visible on compact portrait profile.
- [x] Add large-font validation.

Evidence: `docs/OYP_200_802_TODO_RECONCILIATION.md`; exact-head master CI evidence recorded there. Remaining broader screen-specific UI work is tracked under OYP-1300 through OYP-1700.

---

## OYP-300 — Rust/Kotlin FFI

### OYP-301 — Select and configure FFI
- [x] Prototype UniFFI.
- [x] Confirm Android ABI/build integration.
- [x] Document fallback criteria for manual JNI.

Evidence: `docs/FFI_QUALIFICATION_AUDIT.md`; exact-head master CI `35035111202` passed at `4014d03abc6c2d4f789c567939dbf8d827cf7601`.

### OYP-302 — Stable coarse-grained API
- [x] Expose resolve/list choices/enqueue/pause/resume/cancel/library/get/delete operations.
- [x] Define async/cancellation semantics.
- [x] Map Rust errors into Kotlin-safe typed errors.

Evidence: `docs/OYP_200_802_TODO_RECONCILIATION.md`; exact-head master CI evidence recorded there.

### OYP-303 — FFI qualification
- [x] Round-trip representative domain types.
- [x] Test errors and cancellation.
- [x] Verify no FFI calls occur on Android main thread when blocking.

Evidence: `docs/OYP_200_802_TODO_RECONCILIATION.md`; exact-head master CI evidence recorded there.

---

## OYP-400 — Persistence and offline library

### OYP-401 — SQLite schema
- [x] Add migration framework.
- [x] Add library item table.
- [x] Add source identity table/fields.
- [x] Add media/subtitle/thumbnail asset records.
- [x] Add download job persistence.
- [x] Add playback position persistence.

Evidence: `docs/PERSISTENCE_LIBRARY_AUDIT.md`; exact-head master CI `35038771334` passed at `a8b593d2387626b225eddd70fede0798a211304c`.

### OYP-402 — Atomic library completion
- [x] Keep incomplete assets separate from completed items.
- [x] Promote verified downloads atomically.
- [x] Recover correctly after process interruption during promotion.

Evidence: `docs/PERSISTENCE_LIBRARY_AUDIT.md`; exact-head master CI `35038771334` passed at `a8b593d2387626b225eddd70fede0798a211304c`.

### OYP-403 — Library repository API
- [x] List/filter/search items.
- [x] Retrieve details.
- [x] Rename display title if enabled.
- [x] Delete item and associated assets safely.
- [x] Detect missing/corrupt files.

Evidence: `docs/PERSISTENCE_LIBRARY_AUDIT.md`; exact-head master CI `35038771334` passed at `a8b593d2387626b225eddd70fede0798a211304c`.

### OYP-404 — Migration tests
- [x] Test fresh database.
- [x] Test upgrade from every released schema version once versions exist.
- [x] Test rollback/failure behavior where applicable.

Evidence: `docs/PERSISTENCE_LIBRARY_AUDIT.md`; v1 is the only released schema, so the released-version upgrade matrix is vacuously complete until schema v2 exists. Newer-schema rejection and typed persistence failures cover the applicable v1 failure boundary. Exact-head master CI `35038771334` passed at `a8b593d2387626b225eddd70fede0798a211304c`.

---

## OYP-500 — Generic download engine

### OYP-501 — HTTP transfer foundation
- [x] Bounded timeouts.
- [x] Redirect handling.
- [x] Content length/range handling.
- [x] Safe temporary paths.
- [x] Filename/path sanitization.

Evidence: `docs/DOWNLOAD_ENGINE_AUDIT.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; exact-head CI evidence recorded there.

### OYP-502 — Pause/resume
- [x] Persist continuation data.
- [x] Resume ranged downloads when supported.
- [x] Fall back safely when resume is not supported.
- [x] Verify partial data before append/reuse.

Evidence: `docs/DOWNLOAD_PAUSE_RESUME_AUDIT.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; exact-head CI evidence recorded there.

### OYP-503 — Retry policy
- [x] Classify retryable/non-retryable failures.
- [x] Add bounded exponential backoff/jitter.
- [x] Make retry state visible to UI.

Evidence: `docs/DOWNLOAD_RETRY_POLICY_AUDIT.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; exact-head CI evidence recorded there.

### OYP-504 — Integrity and completion
- [x] Validate expected size when known.
- [x] Add optional checksum/integrity hooks.
- [x] Never mark incomplete content as completed.

Evidence: `docs/DOWNLOAD_INTEGRITY_COMPLETION_AUDIT.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; exact-head CI evidence recorded there.

### OYP-505 — Cleanup
- [x] Cancel behavior policy.
- [x] Orphan partial-file cleanup.
- [x] Startup reconciliation.
- [x] Storage-pressure failure handling.

Evidence: `docs/DOWNLOAD_CLEANUP_AUDIT.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; exact-head CI evidence recorded there.

### OYP-506 — Download test server
- [x] Add deterministic local HTTP fixture server.
- [x] Test interruption, range resume, timeout, disconnect, incorrect content length, and retry.

Evidence: `docs/DOWNLOAD_ENGINE_AUDIT.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; exact-head CI evidence recorded there.

---

## OYP-600 — Source abstraction

### OYP-601 — `MediaSource` contract
- [x] `can_handle`.
- [x] resolve metadata.
- [x] enumerate normalized formats.
- [x] create download plan.
- [x] define optional subtitle/thumbnail hooks.

### OYP-602 — Source registry
- [x] Select adapter based on URL.
- [x] Return unsupported-source error cleanly.
- [x] Prevent provider-specific types from leaking into generic UI/domain APIs.

### OYP-603 — Direct/local fixture adapter
- [x] Implement non-YouTube adapter usable in CI.
- [x] Use it for end-to-end tests without dependence on live external services.

Evidence: `docs/SOURCE_ABSTRACTION_AUDIT.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; PR #108 exact-head CI evidence recorded there.

---

## OYP-700 — YouTube source adapter

### OYP-701 — Extraction strategy spike
- [x] Evaluate pure-Rust extraction viability.
- [x] Evaluate wrapping/embedding an external extractor where licensing/platform constraints permit.
- [x] Document Android packaging, updateability, reliability, and legal/policy implications.
- [x] Choose initial implementation behind `MediaSource`.

### OYP-702 — URL recognition
- [x] Handle canonical watch URLs.
- [x] Handle share/short URLs.
- [x] Reject unsupported/non-video forms explicitly.

Evidence: `docs/YOUTUBE_EXTRACTION_AUDIT.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; PR #109 exact-head CI evidence recorded there.

### OYP-703 — Metadata resolution
- [x] Resolve title, duration, thumbnail, source ID, and available streams.
- [x] Sanitize all remote metadata.
- [x] Add fixtures/regression tests.

### OYP-704 — Format normalization
- [x] Normalize video/audio-only/adaptive streams.
- [x] Record codecs, container, bitrate, resolution, and compatibility.
- [x] Collapse raw formats into curated quality choices.

### OYP-705 — Adapter diagnostics
- [x] Distinguish network failure from extractor incompatibility/source change.
- [x] Do not log cookies/tokens/signed URLs.
- [x] Provide actionable user-facing failure category.

Evidence: `docs/YOUTUBE_ADAPTER_AUDIT.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; PR #110 exact-head CI evidence recorded there.

### OYP-706 — Policy/legal release gate
- [x] Document service terms/app-store considerations.
- [x] Define supported-use statement before public release.
- [x] Block app-store/public-release milestone until reviewed.

Evidence: `docs/YOUTUBE_POLICY_RELEASE_GATE.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; PR #111 exact-head CI evidence recorded there. This records the engineering release gate only; it does not assert that a future human policy/legal review has already approved public distribution.

---

## OYP-800 — Media format selection and local asset strategy

### OYP-801 — Compatibility policy
- [x] Define preferred codecs/containers for Android Media3/device compatibility.
- [x] Prefer directly playable formats where quality tradeoff is acceptable.

Evidence: `docs/MEDIA_ASSET_STRATEGY_AUDIT.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; PR #112 exact-head CI evidence recorded there.

### OYP-802 — Adaptive audio/video assets
- [x] Represent separate local audio/video streams.
- [x] Prove Media3 playback of coordinated local assets where supported.
- [x] Persist asset relationship in library database.

Evidence: `docs/ADAPTIVE_LOCAL_PLAYBACK_QUALIFICATION.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; PR #113 and PR #114 exact-head CI evidence recorded there. Full offline player behavior remains tracked under OYP-900 and OYP-1900.

### OYP-803 — Muxing decision gate
- [x] Identify cases that require muxing.
- [x] Evaluate FFmpeg/native alternative size and licensing impact.
- [x] Do not add muxer dependency unless required by tested compatibility needs.

Evidence: `docs/MEDIA_ASSET_STRATEGY_AUDIT.md`, `docs/OYP_200_802_TODO_RECONCILIATION.md`; PR #112 exact-head CI evidence recorded there.

---

## OYP-900 — Media3 offline playback

### OYP-901 — Local player integration
- [ ] Build Media3 player around local URIs/assets.
- [ ] No network dependency for completed playback.
- [ ] Handle single-file and separate audio/video asset plans.

### OYP-902 — Portrait player screen
- [ ] Fixed full-width 16:9 video surface.
- [ ] Fixed title/timeline/transport/secondary control regions.
- [ ] Skip back/play-pause/skip forward.
- [ ] Speed/subtitles/audio controls.
- [ ] No rotate/fullscreen-landscape action.
- [ ] No scrolling required for playback controls.

### OYP-903 — Playback position
- [ ] Persist periodically and on lifecycle transitions.
- [ ] Resume intelligently.
- [ ] Mark near-end items completed according to defined threshold.

### OYP-904 — MediaSession
- [ ] Lock-screen controls.
- [ ] Bluetooth/headset controls.
- [ ] Audio focus and noisy-intent handling.

### OYP-905 — Offline playback qualification
- [ ] Download fixture.
- [ ] Disable networking.
- [ ] Cold-start app.
- [ ] Play/seek/pause/resume successfully.

---

## OYP-1000 — Android download service and notifications

### OYP-1001 — Foreground download service
- [ ] User-visible active-download service.
- [ ] Correct lifecycle/start/stop behavior.
- [ ] Multiple download coordination.

### OYP-1002 — Notifications
- [ ] Progress notification.
- [ ] Pause/resume/cancel actions where suitable.
- [ ] Failure/completion notification behavior.

### OYP-1003 — Network policy
- [ ] Wi-Fi-only mode.
- [ ] Connectivity-loss pause/retry behavior.
- [ ] Do not silently violate user network preference.

### OYP-1004 — Process death/reboot recovery
- [ ] Reconstruct durable queue.
- [ ] Reconcile in-progress temp files.
- [ ] Resume or fail explicitly.

---

## OYP-1100 — Android Share flow

### OYP-1101 — Share intent
- [ ] Register appropriate intent filter.
- [ ] Parse shared text/URL safely.
- [ ] Reject malformed/untrusted input.

### OYP-1102 — Share-to-download UX
- [ ] Resolve shared URL.
- [ ] Open Download Setup directly when possible.
- [ ] Preserve fixed control layout.
- [ ] Clear back-stack behavior.

---

## OYP-1200 — Thumbnails, subtitles, and metadata

### OYP-1201 — Thumbnails
- [ ] Download/store local thumbnail.
- [ ] Offline display.
- [ ] Cache/cleanup rules.

### OYP-1202 — Subtitles
- [ ] Enumerate available subtitle tracks.
- [ ] Download selected/default track.
- [ ] Persist language/format metadata.
- [ ] Play locally in Media3.

### OYP-1203 — Metadata presentation
- [ ] Compact metadata surfaces.
- [ ] No unbounded metadata wall on primary screens.
- [ ] Long details move to dedicated detail page/list region.

---

## OYP-1300 — Library UX

### OYP-1301 — Library screen
- [ ] Fixed top controls and bottom nav.
- [ ] Bounded scrolling item region.
- [ ] List/grid mode.
- [ ] Search/filter.
- [ ] Empty state with Add action.

### OYP-1302 — Library item actions
- [ ] Play.
- [ ] Details.
- [ ] Rename if enabled.
- [ ] Remove from device with confirmation.
- [ ] No swipe-only destructive action.

### OYP-1303 — Compact-screen validation
- [ ] Verify primary controls remain on-screen on compact portrait device.
- [ ] Verify no horizontal scrolling.
- [ ] Verify large-font behavior.

---

## OYP-1400 — Downloads UX

### OYP-1401 — Downloads screen
- [ ] Fixed app bar/filter/bottom nav.
- [ ] Scrollable transfer list only.
- [ ] Active/paused/failed/completed states.

### OYP-1402 — Download row actions
- [ ] Visible pause/resume.
- [ ] Cancel.
- [ ] Retry.
- [ ] Human-readable error reason.
- [ ] Progress, percentage, and size.
- [ ] Speed/ETA only when trustworthy.

---

## OYP-1500 — Add and Download Setup UX

### OYP-1501 — Add screen
- [ ] URL field.
- [ ] Paste.
- [ ] Analyze.
- [ ] Supported-source hint.
- [ ] Entire functional UI fits without scrolling.

### OYP-1502 — Download Setup
- [ ] Thumbnail/title/duration.
- [ ] Curated quality choices.
- [ ] Estimated size when available.
- [ ] Fixed Options and Download actions.
- [ ] Entire primary UI fits without scrolling.

### OYP-1503 — Advanced options subpage
- [ ] Move subtitle/audio/advanced choices off primary setup screen.
- [ ] Keep subpage focused and non-scroll-dependent for primary controls.

---

## OYP-1600 — Settings UX

### OYP-1601 — Settings hub
- [ ] Fixed cards/rows for Downloads, Playback, Storage, Appearance, About.
- [ ] Hub fits without scrolling on target compact profile.

### OYP-1602 — Download settings
- [ ] Default quality.
- [ ] Wi-Fi only.
- [ ] Concurrent download limit.
- [ ] Subtitle defaults.
- [ ] Retry preference.

### OYP-1603 — Playback settings
- [ ] Remember position.
- [ ] Default speed.
- [ ] Skip interval.
- [ ] Subtitle/audio defaults.

### OYP-1604 — Storage settings
- [ ] Storage location summary.
- [ ] Used/free space.
- [ ] Cache management.
- [ ] Orphan/incomplete cleanup.

### OYP-1605 — Appearance settings
- [ ] Dark/Light/System selector.
- [ ] Dark default.
- [ ] Library layout preference.

### OYP-1606 — About
- [ ] Version/build.
- [ ] Licenses.
- [ ] Privacy.
- [ ] Diagnostics/export if implemented.
- [ ] Legal/source-service notice.

---

## OYP-1700 — Design-system and UI qualification

### OYP-1701 — Midnight Transit theme
- [ ] Implement all semantic tokens from spec.
- [ ] Contrast validation.
- [ ] Component states: enabled/disabled/pressed/focused/error.

### OYP-1702 — Screenshot/golden tests
- [ ] Library.
- [ ] Add.
- [ ] Download Setup.
- [ ] Downloads.
- [ ] Player.
- [ ] Settings hub and each settings subpage.
- [ ] Compact portrait profile.
- [ ] Large portrait profile.

### OYP-1703 — No-hidden-controls gate
- [ ] Automated or deterministic UI test asserts primary actions are visible without scrolling on target profiles.
- [ ] No horizontal scrolling controls.
- [ ] No landscape-only affordances.

### OYP-1704 — Accessibility
- [ ] 48 dp touch targets.
- [ ] TalkBack labels.
- [ ] Logical focus order.
- [ ] Non-color status cues.
- [ ] Text scaling qualification.

---

## OYP-1800 — Security, privacy, and resilience

### OYP-1801 — Untrusted input hardening
- [ ] URL validation.
- [ ] Metadata sanitization.
- [ ] Path traversal tests.
- [ ] Filename sanitization tests.

### OYP-1802 — Secret/log hygiene
- [ ] Redact sensitive headers/tokens/cookies/signed URLs.
- [ ] Add tests around diagnostic output.

### OYP-1803 — Resource bounds
- [ ] Network timeout bounds.
- [ ] Response/body bounds where relevant.
- [ ] Concurrency bounds.
- [ ] Disk-space preflight.

### OYP-1804 — Corruption/recovery
- [ ] Missing asset handling.
- [ ] Corrupt database/media handling.
- [ ] Incomplete migration handling.
- [ ] Startup reconciliation.

---

## OYP-1900 — End-to-end qualification

### OYP-1901 — Deterministic E2E fixture flow
- [ ] Resolve test source.
- [ ] Select quality.
- [ ] Download.
- [ ] Kill/restart process during transfer.
- [ ] Resume.
- [ ] Complete and verify.
- [ ] Disable network.
- [ ] Play locally.

### OYP-1902 — Share E2E
- [ ] Receive URL through Android Share.
- [ ] Resolve.
- [ ] Download.
- [ ] Verify library item.

### OYP-1903 — Storage failure E2E
- [ ] Insufficient-space path.
- [ ] Partial-file cleanup.
- [ ] Clear user-facing recovery action.

---

## OYP-2000 — CI, release engineering, and supply chain

### OYP-2001 — Full CI matrix
- [ ] Rust fmt/clippy/test.
- [ ] Kotlin/Android lint/test/build.
- [ ] FFI generation/build.
- [ ] UI/golden tests.
- [ ] E2E fixture tests where environment permits.

### OYP-2002 — Dependency/license checks
- [x] Rust dependency audit.
- [x] Android dependency audit.
- [x] OSS license inventory.
- [x] Explicit review for extractor/muxer dependencies.

Evidence: `docs/DEPENDENCY_LICENSE_AUDIT.md`; exact-head master CI `35033596290` passed at `23fc6c047931ebbc82d71be681296a1e07f493e7`. Final generated notices and the project's own license choice remain release-gate obligations, not unresolved OYP-2002 audit work.

### OYP-2003 — Reproducible release metadata
- [x] Versioning scheme.
- [x] Commit SHA embedded/displayable.
- [x] Release build instructions.
- [x] Artifact naming.

Evidence: `docs/RELEASE.md`, `BuildMetadata`, and `app/build.gradle.kts`; exact-head master CI `35033596290` passed at `23fc6c047931ebbc82d71be681296a1e07f493e7`.

---

## OYP-2100 — Documentation

### OYP-2101 — README
- [x] Product summary.
- [x] Architecture.
- [x] Build prerequisites.
- [x] Local development.
- [x] Test commands.
- [x] Current limitations.

### OYP-2102 — Architecture decisions
- [x] Rust/Kotlin boundary ADR.
- [x] FFI ADR.
- [x] Source-adapter ADR.
- [x] Media/muxing ADR.
- [x] Portrait-only UI ADR.

Evidence: `docs/ARCHITECTURE_DECISIONS.md` records accepted ADR-001 through ADR-005 for each required boundary/decision.

### OYP-2103 — User-facing documentation
- [x] Offline workflow.
- [x] Download/storage behavior.
- [x] Privacy.
- [x] Troubleshooting.

Evidence: `docs/USER_GUIDE.md` documents the v1 offline workflow, storage/download semantics, privacy/diagnostic behavior, troubleshooting, portrait-only behavior, and current release limitations.

---

## OYP-2200 — Cross-platform readiness

### OYP-2201 — Core portability audit
- [x] No Android-specific types in portable domain layer.
- [x] Platform filesystem/network assumptions abstracted where required.
- [x] FFI API suitable for future Swift/desktop bindings.

Evidence: `docs/CORE_PORTABILITY_AUDIT.md` records the v1 portability audit covering Android type isolation, platform-owned filesystem/network policy boundaries, and the coarse UniFFI contract's suitability for future Swift/desktop wrappers.

### OYP-2202 — Portable library-format contract
- [x] Document database/media directory semantics.
- [x] Document versioning/migration expectations.
- [x] Avoid baking Android-only URIs into portable persisted records.

Evidence: `docs/PORTABLE_LIBRARY_FORMAT.md` defines the SQLite/relative-asset contract, schema migration rules, platform-owned library roots, and the prohibition on persisting Android/platform URI identities in portable records.

No iOS/desktop UI is required for v1.

---

## OYP-2300 — v1 engineering closeout

### OYP-2301 — TODO reconciliation
- [ ] Every task/subtask marked complete, deferred with rationale, rejected, or superseded.
- [ ] No stale partial states.

### OYP-2302 — Exact-head qualification
- [ ] Full CI green on exact candidate commit.
- [ ] Record run URLs/IDs and commit SHA.
- [ ] Verify clean checkout build.

### OYP-2303 — Portrait UX audit
- [ ] No landscape resources/features required.
- [ ] No primary controls off-screen on supported portrait profiles.
- [ ] No horizontal-scroll control surfaces.
- [ ] Only bounded collection/content lists scroll.

### OYP-2304 — Offline acceptance
- [ ] Completed library reconstructs offline.
- [ ] Thumbnails/metadata/subtitles needed for playback available offline.
- [ ] Representative completed item plays with networking disabled.

### OYP-2305 — Release gate
- [ ] Known limitations documented.
- [ ] Legal/policy review state documented.
- [ ] Public distribution remains blocked if unresolved policy/legal requirements remain.
