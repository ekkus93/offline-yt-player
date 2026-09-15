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
- [ ] Create Rust workspace.
- [ ] Create Android Gradle project.
- [ ] Create `core/`, Android app, test, and documentation boundaries.
- [ ] Add `.editorconfig`, `.gitignore`, license decision placeholder, and contribution/build notes.

Acceptance:
- [ ] Clean checkout has documented bootstrap commands.
- [ ] Rust and Android skeletons build in CI.

### OYP-002 — Pin toolchains
- [ ] Pin Rust toolchain.
- [ ] Pin Java/JDK version.
- [ ] Pin Android Gradle Plugin, Kotlin, Compose BOM, and Gradle wrapper.
- [ ] Document supported Android SDK/minSdk/targetSdk values.

Acceptance:
- [ ] CI uses the same declared toolchain versions as local documentation.

### OYP-003 — Baseline CI
- [ ] Add Rust fmt/clippy/test jobs.
- [ ] Add Android lint/unit/build jobs.
- [ ] Add exact commit SHA reporting.
- [ ] Add dependency cache without making correctness cache-dependent.

---

## OYP-100 — Rust core foundation

### OYP-101 — Domain models
- [ ] Define source identity.
- [ ] Define `MediaInfo`.
- [ ] Define media/audio/video/subtitle format models.
- [ ] Define curated quality-choice model distinct from raw streams.
- [ ] Define local library item model.
- [ ] Define typed error taxonomy.

### OYP-102 — Download state machine
- [ ] Define queued/resolving/downloading/paused/retry-wait/failed/verifying/completed/canceled states.
- [ ] Define legal transitions.
- [ ] Reject impossible transitions.
- [ ] Add exhaustive state-transition tests.

### OYP-103 — Core event model
- [ ] Define coarse progress/update events for UI consumption.
- [ ] Prevent high-frequency FFI chatter.
- [ ] Define durable vs ephemeral state.

---

## OYP-200 — Android portrait-only application shell

### OYP-201 — Compose application
- [ ] Create Kotlin application module.
- [ ] Add Material 3 Compose setup.
- [ ] Apply Midnight Transit semantic color tokens.
- [ ] Implement typography/spacing/radius tokens.

### OYP-202 — Portrait-only enforcement
- [ ] Lock app activity to portrait orientation.
- [ ] Remove landscape resource assumptions.
- [ ] Add automated manifest/configuration test where practical.
- [ ] Document portrait-only support policy.

Acceptance:
- [ ] Rotating a test device does not switch the application into a landscape layout.

### OYP-203 — Navigation shell
- [ ] Implement fixed bottom navigation: Library, Downloads, Add, Settings.
- [ ] Library is launch destination.
- [ ] Preserve destination state appropriately.
- [ ] No drawer or horizontal nav carousel.

### OYP-204 — Fixed-region layout primitives
- [ ] Create reusable scaffold for fixed top bar, bounded content region, fixed actions, and bottom nav.
- [ ] Support system insets.
- [ ] Add tests that primary actions remain visible on compact portrait profile.
- [ ] Add large-font validation.

---

## OYP-300 — Rust/Kotlin FFI

### OYP-301 — Select and configure FFI
- [ ] Prototype UniFFI.
- [ ] Confirm Android ABI/build integration.
- [ ] Document fallback criteria for manual JNI.

### OYP-302 — Stable coarse-grained API
- [ ] Expose resolve/list choices/enqueue/pause/resume/cancel/library/get/delete operations.
- [ ] Define async/cancellation semantics.
- [ ] Map Rust errors into Kotlin-safe typed errors.

### OYP-303 — FFI qualification
- [ ] Round-trip representative domain types.
- [ ] Test errors and cancellation.
- [ ] Verify no FFI calls occur on Android main thread when blocking.

---

## OYP-400 — Persistence and offline library

### OYP-401 — SQLite schema
- [ ] Add migration framework.
- [ ] Add library item table.
- [ ] Add source identity table/fields.
- [ ] Add media/subtitle/thumbnail asset records.
- [ ] Add download job persistence.
- [ ] Add playback position persistence.

### OYP-402 — Atomic library completion
- [ ] Keep incomplete assets separate from completed items.
- [ ] Promote verified downloads atomically.
- [ ] Recover correctly after process interruption during promotion.

### OYP-403 — Library repository API
- [ ] List/filter/search items.
- [ ] Retrieve details.
- [ ] Rename display title if enabled.
- [ ] Delete item and associated assets safely.
- [ ] Detect missing/corrupt files.

### OYP-404 — Migration tests
- [ ] Test fresh database.
- [ ] Test upgrade from every released schema version once versions exist.
- [ ] Test rollback/failure behavior where applicable.

---

## OYP-500 — Generic download engine

### OYP-501 — HTTP transfer foundation
- [ ] Bounded timeouts.
- [ ] Redirect handling.
- [ ] Content length/range handling.
- [ ] Safe temporary paths.
- [ ] Filename/path sanitization.

### OYP-502 — Pause/resume
- [ ] Persist continuation data.
- [ ] Resume ranged downloads when supported.
- [ ] Fall back safely when resume is not supported.
- [ ] Verify partial data before append/reuse.

### OYP-503 — Retry policy
- [ ] Classify retryable/non-retryable failures.
- [ ] Add bounded exponential backoff/jitter.
- [ ] Make retry state visible to UI.

### OYP-504 — Integrity and completion
- [ ] Validate expected size when known.
- [ ] Add optional checksum/integrity hooks.
- [ ] Never mark incomplete content as completed.

### OYP-505 — Cleanup
- [ ] Cancel behavior policy.
- [ ] Orphan partial-file cleanup.
- [ ] Startup reconciliation.
- [ ] Storage-pressure failure handling.

### OYP-506 — Download test server
- [ ] Add deterministic local HTTP fixture server.
- [ ] Test interruption, range resume, timeout, disconnect, incorrect content length, and retry.

---

## OYP-600 — Source abstraction

### OYP-601 — `MediaSource` contract
- [ ] `can_handle`.
- [ ] resolve metadata.
- [ ] enumerate normalized formats.
- [ ] create download plan.
- [ ] define optional subtitle/thumbnail hooks.

### OYP-602 — Source registry
- [ ] Select adapter based on URL.
- [ ] Return unsupported-source error cleanly.
- [ ] Prevent provider-specific types from leaking into generic UI/domain APIs.

### OYP-603 — Direct/local fixture adapter
- [ ] Implement non-YouTube adapter usable in CI.
- [ ] Use it for end-to-end tests without dependence on live external services.

---

## OYP-700 — YouTube source adapter

### OYP-701 — Extraction strategy spike
- [ ] Evaluate pure-Rust extraction viability.
- [ ] Evaluate wrapping/embedding an external extractor where licensing/platform constraints permit.
- [ ] Document Android packaging, updateability, reliability, and legal/policy implications.
- [ ] Choose initial implementation behind `MediaSource`.

### OYP-702 — URL recognition
- [ ] Handle canonical watch URLs.
- [ ] Handle share/short URLs.
- [ ] Reject unsupported/non-video forms explicitly.

### OYP-703 — Metadata resolution
- [ ] Resolve title, duration, thumbnail, source ID, and available streams.
- [ ] Sanitize all remote metadata.
- [ ] Add fixtures/regression tests.

### OYP-704 — Format normalization
- [ ] Normalize video/audio-only/adaptive streams.
- [ ] Record codecs, container, bitrate, resolution, and compatibility.
- [ ] Collapse raw formats into curated quality choices.

### OYP-705 — Adapter diagnostics
- [ ] Distinguish network failure from extractor incompatibility/source change.
- [ ] Do not log cookies/tokens/signed URLs.
- [ ] Provide actionable user-facing failure category.

### OYP-706 — Policy/legal release gate
- [ ] Document service terms/app-store considerations.
- [ ] Define supported-use statement before public release.
- [ ] Block app-store/public-release milestone until reviewed.

---

## OYP-800 — Media format selection and local asset strategy

### OYP-801 — Compatibility policy
- [ ] Define preferred codecs/containers for Android Media3/device compatibility.
- [ ] Prefer directly playable formats where quality tradeoff is acceptable.

### OYP-802 — Adaptive audio/video assets
- [ ] Represent separate local audio/video streams.
- [ ] Prove Media3 playback of coordinated local assets where supported.
- [ ] Persist asset relationship in library database.

### OYP-803 — Muxing decision gate
- [ ] Identify cases that require muxing.
- [ ] Evaluate FFmpeg/native alternative size and licensing impact.
- [ ] Do not add muxer dependency unless required by tested compatibility needs.

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
- [ ] Rust dependency audit.
- [ ] Android dependency audit.
- [ ] OSS license inventory.
- [ ] Explicit review for extractor/muxer dependencies.

### OYP-2003 — Reproducible release metadata
- [ ] Versioning scheme.
- [ ] Commit SHA embedded/displayable.
- [ ] Release build instructions.
- [ ] Artifact naming.

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
- [ ] Rust/Kotlin boundary ADR.
- [ ] FFI ADR.
- [ ] Source-adapter ADR.
- [ ] Media/muxing ADR.
- [ ] Portrait-only UI ADR.

### OYP-2103 — User-facing documentation
- [ ] Offline workflow.
- [ ] Download/storage behavior.
- [ ] Privacy.
- [ ] Troubleshooting.

---

## OYP-2200 — Cross-platform readiness

### OYP-2201 — Core portability audit
- [ ] No Android-specific types in portable domain layer.
- [ ] Platform filesystem/network assumptions abstracted where required.
- [ ] FFI API suitable for future Swift/desktop bindings.

### OYP-2202 — Portable library-format contract
- [ ] Document database/media directory semantics.
- [ ] Document versioning/migration expectations.
- [ ] Avoid baking Android-only URIs into portable persisted records.

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
