# Offline YT Player — Remediation Specification

**Document:** `docs/OFFLINE_YT_PLAYER_REMEDIATION_SPEC_2026-09-17.md`  
**Status:** Proposed remediation baseline  
**Repository:** `ekkus93/offline-yt-player`  
**Reviewed baseline:** `b8aebfd0467de67a2cc0b0a583d91f9a1783da7c`  
**Companion execution checklist:** `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`

## 1. Purpose

This specification defines the engineering remediation required to turn the current Offline YT Player repository from a strong Rust-core prototype plus Android UI/policy scaffolding into an operational, testable Android v1.

It is intentionally corrective. It does not replace the product intent in `docs/OFFLINE_YT_PLAYER_SPEC.md`; it tightens implementation boundaries and acceptance criteria where the previous closeout overstated completion.

The remediation must preserve working core behavior unless a task explicitly changes it. The goal is to connect the existing core to a real Android product, repair the concrete correctness defects identified in review, and replace policy-only qualification with executable behavioral qualification.

## 2. Baseline assessment

The reviewed baseline has two very different maturity levels:

1. **Rust core:** substantial real implementation exists for domain modeling, persistence, transfer, resumability, source abstraction, integrity checks, security helpers, and deterministic fixture testing.
2. **Android application:** many screens, policy objects, and service shells exist, but production wiring is incomplete. Important actions are no-ops, several screens use empty or fabricated data, the APK is not actually integrated with the UniFFI-generated Rust interface, and most claimed UI/E2E qualification is declarative rather than behavioral.

The remediation shall therefore retain and harden the Rust core while replacing Android scaffolding with real application orchestration.

## 3. Governing engineering rules

### 3.1 Completion means runtime behavior

A feature is complete only when the production path performs the requested behavior and an automated test or explicitly documented device qualification proves it.

The following do **not** by themselves prove completion:

- a constant saying a capability is supported;
- an enum containing the expected states;
- a policy object containing expected booleans;
- a unit test that asserts those constants or booleans;
- a source-text test that merely checks that a symbol or string is present;
- a screen with a visible button whose callback is empty;
- a mock/fabricated record that is never replaced by the production provider;
- a checked TODO box without implementation evidence.

Policy tests may remain as specification guards, but behavioral acceptance criteria require behavioral tests.

### 3.2 Exact-head evidence

Any milestone claimed complete must identify the exact commit SHA it qualified. Final closeout requires all required CI jobs to pass on the exact candidate SHA, followed by verification that the merged `master` head contains the same implementation and passes the required post-merge matrix.

### 3.3 Detailed acceptance criteria are permanent records

The remediation TODO must never be compressed in a way that destroys task/subtask state. A later summary file may be generated, but the detailed checklist remains authoritative and must stay in git history and on the branch.

A documentation-only reconciliation commit may mark an item complete only if the referenced implementation and evidence already existed and the reconciliation explicitly cites that evidence. It may not convert unresolved acceptance criteria to complete merely by rewriting the checklist.

### 3.4 External policy/legal approval remains external

Engineering completion does not authorize public distribution, app-store publication, or use that violates a provider's terms or applicable law. Existing YouTube policy/legal release gates remain separate from engineering completion.

## 4. Known defects that this remediation must resolve

The minimum defect inventory is:

- Android manifest lacks `android.permission.INTERNET`.
- The Android APK does not package/use generated UniFFI Kotlin bindings and Rust JNI libraries as a production dependency.
- No production live YouTube `MediaSource` implementation is registered; the fixture source is the only operational source implementation.
- Add-screen Paste/Analyze/Download behavior is incomplete or fabricated.
- Library and Downloads screens are backed by hard-coded empty lists rather than repositories/state flows.
- Foreground-service pause/resume/cancel/connectivity/reboot actions are no-ops.
- Android 15+ `dataSync` foreground-service timeout and `BOOT_COMPLETED` restrictions are not respected.
- `LOCKED_BOOT_COMPLETED` is declared without a correct Direct Boot design.
- Split audio/video playback is modeled but the production player screen ignores the audio asset.
- The visible playback UI and `MediaSessionService` own different ExoPlayer instances.
- Playback position persistence is not wired to the real player lifecycle.
- Thumbnail and subtitle pipelines are mostly helpers/policies rather than complete download/persist/display flows.
- Settings screens mostly display policy values without durable settings or runtime application.
- Several Android E2E, golden, accessibility, and layout tests assert declarations rather than actual rendered/runtime behavior.
- Retry classification can retry errors whose `CoreError.retryable` value is false.
- `DownloadPolicy.max_attempts` is not authoritative in the production transfer path.
- response-body network failures can be misclassified as local storage/internal failures.
- deleting a library item removes metadata but can leave media files behind.
- stored SHA-256 values are not used during later asset-corruption validation.
- raw HTTP client error text can expose sensitive/signed URLs.
- quality curation can retain an inferior stream when multiple formats share the same height.
- the final previous TODO reconciliation marked unresolved items complete without corresponding implementation changes.

This list is a floor, not a ceiling. New defects discovered during remediation must be added to the TODO and fixed before closeout if they affect v1 correctness, safety, or acceptance criteria.

## 5. Target architecture

The repaired v1 shall use the following responsibility split:

```text
Android UI (Compose)
        |
        v
ViewModels / application use-cases
        |
        +---------------------> Android Settings/DataStore
        |
        v
Kotlin Core Gateway (generated UniFFI bindings behind an app-owned interface)
        |
        v
Rust Core
  - source registry / YouTube source
  - normalized media model
  - persistence
  - transfer planning and transfer engine
  - retry/resume/integrity
  - security / validation
        |
        +---- SQLite + app-private media files

Background download runtime
  API 34+: user-initiated data transfer JobScheduler job
  API 26-33: compatible foreground/background transfer fallback
        |
        v
Same Kotlin Core Gateway / same durable Rust queue state

Playback
  Android Media3 MediaSessionService owns the canonical ExoPlayer
        ^
        |
Compose player UI connects through MediaController
```

The central invariant is that there is **one authoritative durable model** for library/download state and **one authoritative player session** for playback. UI policy objects must not become parallel sources of truth.

## 6. Android manifest and platform compliance

### 6.1 Network permission

The manifest shall declare `android.permission.INTERNET`. `ACCESS_NETWORK_STATE` may remain for connectivity policy.

### 6.2 Download background-execution strategy

For user-requested downloads:

- **API 34 and later:** use Android User-Initiated Data Transfer (UIDT) jobs via `JobScheduler` as the preferred execution primitive. Declare `android.permission.RUN_USER_INITIATED_JOBS`, schedule only from allowed user-visible conditions, specify a required network, and attach/update the required notification.
- **API 26-33:** provide a compatible fallback suitable for long network transfers. A foreground worker/service is acceptable when started legally from user interaction and when its lifecycle is tied to the same durable queue model.
- Do not launch a `dataSync` foreground service from `BOOT_COMPLETED` on target SDK 35+.
- If any `dataSync` foreground service remains for compatibility, implement platform timeout handling and stop promptly when the system invokes the timeout callback.

The implementation must be selected through a small Android runtime abstraction so download-domain logic does not branch throughout the UI.

### 6.3 Boot recovery

Boot recovery must mean **reconstructing durable state and scheduling permitted future work**, not blindly starting a forbidden foreground service.

At boot:

1. inspect durable queue state;
2. move interrupted in-flight work into an explicit resumable/waiting state where required;
3. schedule work using an API legal for the device/API level and current app state;
4. never claim the download completed unless integrity and metadata promotion succeeded.

If `LOCKED_BOOT_COMPLETED` is retained, the receiver and every dependency used before first unlock must be Direct-Boot-aware and use device-protected storage. Otherwise remove `LOCKED_BOOT_COMPLETED` and reconcile after ordinary `BOOT_COMPLETED`/next app start instead. Credential-encrypted database/media state must not be accessed before it is available.

### 6.4 Notification permission and degradation

Android 13+ notification permission behavior must be handled explicitly. Denial must not corrupt queue state. The UI must explain any reduced background visibility/behavior required by platform rules.

## 7. Rust/Kotlin FFI integration

### 7.1 Build integration

The Android build must consume the generated UniFFI Kotlin bindings and native Rust library for every supported ABI included in the APK.

The Gradle build shall:

- generate or consume deterministic UniFFI Kotlin sources;
- build/copy the Rust `.so` libraries into Android JNI packaging locations;
- expose generated code to the app compile classpath;
- fail CI if generated bindings do not match the Rust interface;
- verify the APK/AAB actually contains the required native libraries.

### 7.2 App-owned gateway

Generated UniFFI types shall not leak indiscriminately through Compose screens. Introduce an app-owned gateway/repository layer that:

- converts generated records/errors into stable Kotlin application models;
- invokes blocking core calls off the main thread;
- centralizes cancellation and lifecycle behavior;
- exposes observable state suitable for ViewModels/Compose;
- provides a test seam for Android UI tests.

### 7.3 Runtime smoke qualification

At least one Android instrumentation test must load the packaged native library and execute representative real FFI calls against a temporary app-private data directory. Merely generating bindings in CI is insufficient.

## 8. Production source implementation

### 8.1 YouTube source adapter

Implement a production `MediaSource` for the supported YouTube URL shapes chosen in the original extraction-strategy decision.

It must perform, at minimum:

- URL recognition and canonical source identity extraction;
- metadata resolution for title, duration, thumbnail, and source identity;
- format discovery;
- normalization into provider-neutral `MediaInfo`/format models;
- construction of one or more executable download plans;
- source-change diagnostics distinct from generic network failures;
- subtitle/thumbnail discovery where supported;
- bounded parsing and response sizes.

`DirectFixtureSource` remains for deterministic tests but must not masquerade as the production YouTube adapter.

### 8.2 Extraction boundary

Provider-specific parsing/network structures stay inside the adapter. Android code must not parse YouTube provider responses.

### 8.3 Format curation

When multiple streams share a height, selection must use deterministic compatibility ranking, not provider iteration order. Unless the user explicitly asks otherwise, rank formats roughly as:

1. direct-play combined A/V supported by Media3;
2. compatible separate A/V pair that Media3 can merge locally;
3. format requiring local mux/transcode, only when that capability is deliberately enabled;
4. unsupported formats excluded with a diagnostic reason.

Within a compatibility class, use explicit deterministic tie-breakers such as codec support, bitrate, frame rate, and stable format ID.

## 9. Download engine correctness

### 9.1 Retry authority

`CoreError.retryable` (or an equivalently explicit typed retry decision) must be authoritative. A nonretryable HTTP/source/integrity error must never be made retryable merely because of its broad error kind.

The production scheduler must consume `DownloadPolicy.max_attempts` or remove that field in favor of a single authoritative retry configuration. There must not be two divergent retry systems.

### 9.2 Network body failures

Separate local filesystem I/O from remote response-body I/O. A socket reset/timeout/truncated network body must be classified as a network/transfer/integrity event according to retry policy, not as a local storage failure.

### 9.3 Durable retries

Retry attempt count, next eligible retry time, and terminal/nonterminal failure state must survive process death. Retry backoff must remain bounded and testable with an injectable clock/randomness source where necessary.

### 9.4 Resume correctness

Retain the existing fail-closed validator checks. Never append to a partial asset when the remote representation cannot be proven compatible with the persisted continuation metadata.

### 9.5 Resource bounds

The existing maximum-size, timeout, redirect, filename/path, and concurrency bounds remain mandatory. Android settings may lower supported concurrency but must not silently exceed core bounds.

## 10. Persistence, deletion, and corruption recovery

### 10.1 Deletion is an asset lifecycle operation

Deleting a library item must delete or deliberately quarantine all owned media/thumbnail/subtitle/partial files before or as part of final metadata removal.

Required properties:

- no path may escape the app-managed media root;
- shared assets, if ever introduced, require reference-aware deletion;
- failed file deletion is surfaced and leaves a recoverable state rather than silently orphaning data;
- orphan reconciliation can repair interrupted deletions.

### 10.2 Integrity validation

When an asset has a persisted SHA-256, subsequent validation shall verify it when performing an explicit integrity scan or when corruption is suspected. Fast-path startup validation may use existence/size to avoid hashing all media every launch, but the product must have a real path to detect same-length corruption.

### 10.3 Database recovery

Corrupt/unsupported database states must map to explicit user-visible recovery options and diagnostics. No production recovery screen may be represented solely by a policy enum.

## 11. Application state and repositories

Introduce production repositories/use-cases for:

- source analysis;
- library list/search/detail/rename/delete;
- download queue and per-download control;
- persisted settings;
- playback resume position;
- storage usage and cleanup.

Compose screens consume ViewModel state derived from these repositories. Production screens must not create hard-coded `emptyList()` data sources or fabricated source metadata except inside previews/tests.

## 12. Add and Share workflows

### 12.1 Add by paste

The Add flow shall:

1. accept pasted/typed text;
2. normalize and validate it with the same hardened source URL policy used by the core;
3. resolve through the real source registry;
4. display resolved metadata and available download choices;
5. surface unsupported/source-changed/network errors distinctly;
6. schedule the selected download through the real runtime.

The Paste button must read clipboard content through Android's supported clipboard API and place it in the field; it must not auto-start a network request without user action unless product behavior explicitly documents that.

### 12.2 Android Share

`ACTION_SEND text/plain` handling shall extract exactly one supported URL from bounded input, apply the same validation policy, and enter the same analysis/setup pipeline as Add-by-paste. It shall not maintain a separate fake/share-only download path.

Back-stack behavior must be exercised by instrumentation testing.

## 13. Download orchestration and user controls

### 13.1 Durable queue

The durable Rust/Kotlin application model must be the source of truth for queued, active, paused, waiting-for-network, retrying, failed, cancelled, and completed items.

### 13.2 Pause/resume/cancel

UI and notification actions must invoke real queue/control methods and produce observable state transitions.

- Pause persists a resumable state and causes transfer work to stop at a bounded cancellation point.
- Resume schedules eligible work and honors network/settings policy.
- Cancel terminates work, cleans partial files according to policy, and reaches a terminal cancelled state.
- Retry clears/updates only the fields appropriate for a new attempt and does not fabricate a fresh library identity.

### 13.3 Progress

Notifications and Downloads UI must use actual bytes transferred/expected size when known. Speed and ETA may be smoothed, but must not be fabricated when insufficient data exists.

### 13.4 Connectivity

Connectivity observation must translate Android network state into the queue policy. Wi-Fi-only means an eligible unmetered network policy as defined/documented by the app, not a static UI label.

## 14. Thumbnail, subtitle, and metadata asset pipelines

### 14.1 Thumbnails

Thumbnail discovery, download, safe filename/path, persistence, cleanup, offline display, and corruption handling must be end-to-end operational.

### 14.2 Subtitles

Supported subtitle tracks must carry explicit language and format identity. Selected tracks must download into managed local assets and be attached to Media3 playback from local URIs. Unsupported subtitle formats must be rejected or transformed by an explicitly licensed/tested path.

### 14.3 Metadata

Resolved metadata shown in Download Setup and Library must come from persisted source/library records, not fabricated UI constants. Bound all displayed and persisted provider text.

## 15. Playback architecture

### 15.1 One canonical player

`PlaybackSessionService` shall own the canonical Media3 `ExoPlayer` and `MediaSession`. The Compose player screen shall connect via a `MediaController` rather than creating a second independent ExoPlayer.

This guarantees that:

- headset controls;
- lock-screen/system controls;
- audio focus;
- noisy-audio handling;
- UI controls;
- playback position;
- current media identity

all refer to the same session.

### 15.2 Local-only media

Playback shall only use local app-managed asset URIs for completed offline items. A completed library item must remain playable with network connectivity disabled.

### 15.3 Split A/V

For separate video/audio assets, build a Media3 source that merges the local tracks. The production player must use this path, not the current video-only shortcut.

### 15.4 Subtitles

Selected local subtitle tracks must be included in the MediaItem/MediaSource configuration and the subtitle control must operate on the real player track selection.

### 15.5 Position persistence

Persist meaningful position changes periodically and on lifecycle/session transitions. Resume from the stored position unless the item is considered completed according to the documented completion threshold. Tests must use the real persistence API and player/controller boundary.

## 16. Settings

All settings visible in production must be durable and used by the runtime.

Minimum settings:

- default download quality;
- network preference/Wi-Fi-only behavior;
- allowed download concurrency within core limits;
- subtitle default behavior;
- retry policy choices that are genuinely user-configurable;
- playback defaults supported by the player;
- appearance mode (system/light/dark);
- default library layout where offered.

Use a durable Android settings store such as DataStore unless an existing suitable persistence layer is deliberately chosen. Settings must expose observable flows to ViewModels/runtime components.

Storage settings shall calculate real managed-storage usage and provide real cleanup actions with confirmation where destructive.

About shall use `BuildConfig`/build metadata rather than hard-coded version strings.

## 17. Security and privacy

### 17.1 One input-validation policy

Android intake and Rust source validation must agree on supported URL schemes/hosts/lengths. Do not maintain a permissive Android parser in front of a stricter Rust parser without clear user-visible handling.

### 17.2 Diagnostic redaction

Never surface raw client error strings that may contain signed URLs, tokens, query parameters, filesystem secrets, or provider-private payload fragments.

All network errors crossing the core/application boundary shall use structured safe diagnostics. Detailed raw errors may be retained only in an explicitly scrubbed developer-only path that is proven not to log secrets.

### 17.3 File/path safety

Continue using app-controlled roots and validated relative paths. Every delete/rename/import path must use the same traversal and filename protections as download creation.

### 17.4 Resource abuse

Bound provider response sizes, metadata lengths, URL lengths, redirects, transfer sizes, concurrent jobs, retry counts, database query result sizes where appropriate, and UI text rendering inputs.

## 18. Android UI and UX completion

The existing portrait-only Midnight Transit design intent remains.

Every visible primary action must either:

- perform its production action; or
- be disabled with an accessible explanation because the action is unavailable in the current state.

No production `onClick = {}` placeholders are permitted.

Required operational screens:

- Library: real items, search, list/grid if retained, play/details/rename/remove.
- Add: paste/input/analyze using production source.
- Download Setup: real metadata, quality options, estimated sizes where known, advanced choices, schedule download.
- Downloads: real queue, filters, progress, pause/resume/cancel/retry, failure messages.
- Player: real MediaController session, timeline, play/pause/seek/speed, subtitle/audio track selection when applicable.
- Settings: durable settings with runtime effect.
- About: actual version/source revision/legal/privacy/licenses/diagnostic information.

If a previously advertised control is deliberately removed from v1, update the product spec/docs instead of leaving a decorative control.

## 19. Qualification strategy

### 19.1 Rust tests

Retain existing Rust suites and add regression tests for every core defect fixed in this remediation, including:

- nonretryable HTTP status remains nonretryable;
- source-change failures do not enter generic retry loops;
- network body disconnect classification;
- `max_attempts` production behavior;
- deletion removes/quarantines owned assets;
- same-length SHA mismatch detection;
- HTTP diagnostics never expose query secrets;
- deterministic same-height quality ranking.

### 19.2 Android JVM tests

Use JVM tests for pure mapping, state-reducer, settings, and formatting logic. Policy declarations may be tested here but are not acceptance substitutes.

### 19.3 Android instrumentation/Compose tests

Add `app/src/androidTest` behavioral tests for:

- native Rust library load + representative UniFFI call;
- Add flow with deterministic fixture provider/gateway;
- Library rendering real repository state;
- Downloads actions invoking real control gateway;
- Share intent/back stack;
- playback controller connection and controls;
- settings persistence;
- accessibility semantics and focus order;
- compact portrait and large-font rendering.

### 19.4 Screenshot/golden qualification

Create deterministic screenshot/golden tests for representative screens and states. A list of intended screenshot names is not a golden test.

At minimum cover:

- Library empty + populated;
- Add invalid + resolved;
- Download Setup;
- Downloads active + failure;
- Player;
- Settings hub;
- large-font representative screens;
- smallest supported portrait width/height class used by the project.

### 19.5 End-to-end tests

Provide automated emulator/device tests where feasible for:

1. fixture source -> analyze -> schedule -> download -> library -> disable network -> cold start -> play local media;
2. Android Share -> analyze -> download -> library;
3. process death during active transfer -> restart -> reconcile -> resume/restart safely;
4. connectivity loss and Wi-Fi-only transitions;
5. pause/resume/cancel from notification and UI;
6. storage exhaustion/failure cleanup;
7. split A/V offline playback;
8. subtitle offline playback where fixture coverage exists.

A test that only checks an enum sequence is not E2E qualification.

### 19.6 Platform-behavior tests

For API levels affected by modern background-work restrictions, add emulator/ADB-assisted qualification for:

- UIDT scheduling on API 34+;
- system stop/retry behavior;
- notification actions;
- boot reconciliation without illegal foreground-service launch;
- any retained foreground-service timeout path.

## 20. CI requirements

The final CI matrix shall include, at minimum:

- Rust format;
- Rust clippy with warnings denied;
- Rust unit/integration tests;
- Android lint;
- Android JVM unit tests;
- Android debug/release-appropriate build;
- UniFFI generation consistency;
- Rust Android ABI builds;
- APK native-library packaging verification;
- Android instrumentation/Compose suite on emulator;
- screenshot/golden verification;
- deterministic fixture E2E suite;
- dependency/license checks;
- vulnerability/advisory scan for Rust and Android dependencies where tooling permits;
- exact-head identity check.

Longer emulator/E2E lanes may be separated from fast presubmit jobs, but final closeout requires them to pass on the candidate SHA.

## 21. Documentation requirements

Update docs to describe actual behavior rather than intended behavior.

Required updates include:

- README status and supported workflows;
- build/FFI instructions;
- background-download architecture by API level;
- source/extraction architecture;
- user guide using operational UI;
- troubleshooting/error states;
- security/privacy model;
- dependency/license notice process;
- release process;
- known limitations;
- external YouTube/legal/policy gate.

Audit documents may describe historical decisions but must not be cited as proof of current runtime behavior unless the linked tests/code still prove it.

## 22. Remediation closeout contract

The remediation is complete only when all of the following are true:

1. Every checkbox in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md` is reconciled with implementation/test evidence.
2. The Android application packages and calls the Rust core at runtime.
3. At least one production-supported YouTube URL resolves through a real production source adapter, subject to the existing external release gate.
4. Add/Share -> Download Setup -> Download -> Library -> Offline Playback works end to end.
5. Pause/resume/cancel/retry and network policy work through real runtime state.
6. Split A/V playback uses a single MediaSession-owned player.
7. Settings shown to users are durable and operational.
8. Core retry, I/O classification, deletion, integrity, redaction, and format-ranking defects identified by review are fixed and regression-tested.
9. Android instrumentation, screenshot/accessibility, and deterministic E2E tests test behavior rather than declarations.
10. Background execution is compliant with the targeted Android versions; boot cannot trigger an illegal foreground-service start.
11. Required CI passes on an exact candidate SHA and post-merge `master` evidence is recorded.
12. The detailed remediation TODO is preserved; completion is not established by replacing unresolved subtasks with a checked summary.
13. Public/app-store release remains blocked unless the separately documented human policy/legal gate is approved.

## 23. Current Android platform references

The remediation design is based on the Android platform rules current at the time of this document:

- Android `INTERNET` permission is required to open network sockets: <https://developer.android.com/reference/android/Manifest.permission>
- User-Initiated Data Transfer jobs are the recommended long-running primitive for user-requested transfers on Android 14+: <https://developer.android.com/develop/background-work/background-tasks/uidt>
- Android 15+ limits `dataSync` foreground-service time and restricts starting `dataSync` foreground services from `BOOT_COMPLETED`: <https://developer.android.com/about/versions/15/behavior-changes-15>
- Direct Boot components receiving `LOCKED_BOOT_COMPLETED` must be Direct-Boot-aware and use appropriate storage: <https://developer.android.com/privacy-and-security/direct-boot>

These rules must be rechecked if `compileSdk`, `targetSdk`, or supported Android versions change during remediation.