# RMD-900 Media3 Playback Reconciliation — 2026-10-01

## Scope

This note reconciles current-master implementation and qualification evidence for the RMD-900 unified Media3 playback section:

- RMD-901 — `PlaybackSessionService` owns the canonical player.
- RMD-902 — Compose connects through `MediaController`.
- RMD-903 — local split A/V playback.
- RMD-904 — subtitle playback controls.
- RMD-905 — audio-track controls where applicable.
- RMD-906 — playback position persistence.
- RMD-907 — MediaSession behavioral qualification.

It does not claim full RMD-1500 end-to-end offline playback closeout. The E2E items still require cold-start/offline fixture playback, split A/V E2E, subtitle E2E, Share E2E, connectivity/failure E2E, and notification-control E2E where listed by the canonical TODO.

## Current-master implementation evidence

Exact implementation head audited: `0e0b291849d091d3286d7cf76038a23d60e164ae`.

### RMD-901 — Service-owned canonical player/session

`app/src/main/java/com/ekkus/offlineytplayer/playback/PlaybackSessionService.kt` now subclasses `MediaSessionService` and owns a single service-scoped `MediaSession` over an `ExoPlayer` created in `onCreate()`.

The canonical player is configured with:

- `SplitAudioMediaSourceFactory(DefaultMediaSourceFactory(this))` so the same player can handle combined and split local assets.
- `setAudioAttributes(AudioAttributes.DEFAULT, true)` for audio focus.
- `setHandleAudioBecomingNoisy(true)` for noisy-route handling.
- `onGetSession(...)` returning the canonical session.
- `onDestroy()` releasing `session.player` before releasing the session and clearing the service field.

`PlaybackSessionOwnershipTest` asserts service ownership, single session construction, audio focus/noisy handling, and lifecycle release order.

### RMD-902 — Compose connects through MediaController

`app/src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt` now uses a `SessionToken` targeting `PlaybackSessionService` and connects with `MediaController.Builder(context, token).buildAsync()` inside a `DisposableEffect`.

The production Compose playback surface:

- no longer constructs an independent `ExoPlayer`;
- sets the local `MediaItem` on the controller;
- prepares through the controller;
- binds `PlayerView.player` to the controller;
- releases the acquired controller and cancels the pending future on disposal;
- sends play/pause/seek/speed controls through the session controller.

`PlaybackControllerIntegrationTest` asserts this integration boundary and guards against reintroducing independent production `ExoPlayer.Builder` use in the Compose screen.

### RMD-903 — Local split A/V playback

`LocalPlaybackPolicy.mediaItemFor(asset)` carries a split audio path in the local MediaItem tag after validating that both video and audio paths are local and distinct. `PlaybackSessionService.SplitAudioMediaSourceFactory` reads that path through `LocalPlaybackPolicy.splitAudioPathFrom(mediaItem)`, creates a local audio `MediaItem`, and returns `MergingMediaSource(videoSource, audioSource)`.

`SplitAudioPlaybackSessionIntegrationTest` verifies the playback-session source factory and the local split-audio tag handoff. `LocalPlaybackPolicyTest` and related playback tests continue to reject remote playback URIs before player construction.

### RMD-904 — Subtitle playback controls

`LocalPlaybackPolicy.mediaItemFor(asset)` attaches persisted local subtitle tracks as Media3 subtitle configurations using local file URIs. `PortraitPlayerScreen` exposes actual subtitle labels from `LocalPlaybackPolicy.availableSubtitleLabels(validated)` and mutates Media3 text-track policy through the controller:

- selecting a persisted subtitle sets preferred text language and enables text;
- cycling past the final track disables text through `setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)`.

`SubtitlePlaybackControlIntegrationTest` verifies subtitle attachment and controller selection policy.

### RMD-905 — Audio-track controls

`LocalPlaybackPolicy.availableAudioLabels(...)` exposes labels only from persisted local audio-track metadata. `LocalPlaybackPolicy.shouldEnableAudioSelection(...)` enables the UI only when more than one audio track exists. `PortraitPlayerScreen` cycles available labels and mutates Media3 preferred audio language through the session controller.

`AudioPlaybackControlIntegrationTest` verifies the real-track gating and controller mutation path.

### RMD-906 — Playback position persistence

`PortraitPlayerScreen` loads `asset.startPositionMs` when `settings.rememberPlaybackPosition` is enabled and supplies the configured start position to `connected.setMediaItem(..., configuredStartPositionMs)` before `prepare()`.

During playback, position persistence is bounded by `LocalPlaybackPolicy.PositionPersistCadenceMs`; final disposal persists a final transition; and near-completion behavior uses `LocalPlaybackPolicy.persistedPositionForStop(...)`, which resets completed playback according to the documented threshold.

Persistence uses `GeneratedUniffiPlaybackPositionGateway.open(databasePath).use { gateway -> gateway.savePlaybackPosition(...) }` off the UI thread. `PlaybackPositionPersistenceIntegrationTest` and `LocalPlaybackPolicyTest` cover start-position restoration, periodic/final save wiring, bounded cadence, completion reset, and clamping semantics.

### RMD-907 — MediaSession behavior

`app/src/androidTest/java/com/ekkus/offlineytplayer/playback/PlaybackSessionBehaviorInstrumentedTest.kt` creates two independent `MediaController` instances against the same `PlaybackSessionService`, mutates media item, play state, and speed from one controller, and observes matching state through the other controller. It then mutates pause/speed from the second controller and observes the canonical state through the first.

This proves UI/controller commands manipulate the same service-owned session player and that current item/playback state agree across independent MediaSession controllers. Headset/system-media button automation remains bounded by emulator API support, but the service advertises headset and lock-screen support through `PlaybackSessionPolicy` and uses the Media3 session/controller path required for those system controls.

## Qualification evidence

Current exact head before this note, `0e0b291849d091d3286d7cf76038a23d60e164ae`, had all six push workflows running after the RMD-703/RMD-704 evidence commit; CI evidence had already completed successfully as run `36831430408` at the time this note was written.

The preceding implementation head `f0817f12e0c852ea5c96174f6a8a182f02a03dc4` passed the complete discovered push matrix:

- CI: `36828269272`
- Android smoke: `36828269326`
- Android FGS timeout / API-35 UIDT: `36828269149`
- Supply chain: `36828269478`
- CI evidence: `36828269193`
- Deterministic E2E fixture: `36828269308`

Direct playback-specific tests on current master include:

- `PlaybackSessionOwnershipTest`
- `PlaybackControllerIntegrationTest`
- `SplitAudioPlaybackSessionIntegrationTest`
- `SubtitlePlaybackControlIntegrationTest`
- `AudioPlaybackControlIntegrationTest`
- `PlaybackPositionPersistenceIntegrationTest`
- `PlaybackSessionBehaviorInstrumentedTest`
- `LocalPlaybackPolicyTest`

## Canonical TODO impact

RMD-901 through RMD-907 are eligible for checklist reconciliation once this exact-head evidence is copied into `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

Do not use this note to close RMD-1501, RMD-1502, or RMD-1503 by itself. Those items require complete deterministic fixture download/cold-start/offline playback proof across the app pipeline and remain separate end-to-end qualification tasks.
