# RMD-902 MediaController playback reconciliation — 2026-09-29

RMD-902 requires production Compose playback to connect through the canonical Media3 session rather than constructing an independent player, render session/controller state, and route user transport actions through the controller lifecycle-safely.

## Current production path

`app/src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt` satisfies the RMD-902 production wiring requirements:

- `PortraitPlayerScreen` builds a `SessionToken` for `PlaybackSessionService` and obtains a `MediaController` with `MediaController.Builder(context, token).buildAsync()`.
- The production playback screen does not import or construct `ExoPlayer`; canonical player ownership remains inside `PlaybackSessionService` under RMD-901.
- The `DisposableEffect` lifecycle scopes the asynchronous controller connection, cancels the pending controller future on disposal, and releases the acquired controller.
- `PlayerView` is bound to the session controller rather than to a locally owned player.
- Play/pause, seek back, seek forward, and speed changes are sent through the connected `MediaController`.
- The UI exposes an explicit connecting state before the controller is available and the offline playback state after controller connection.

## Deterministic qualification

`app/src/test/java/com/ekkus/offlineytplayer/playback/PlaybackControllerIntegrationTest.kt` guards the production source shape so future changes cannot silently reintroduce independent ExoPlayer construction, detach `PlayerView` from the session controller, drop lifecycle release, or bypass the MediaController for primary transport commands.

## Boundary

This closes the controller integration proof for RMD-902 only. RMD-903 through RMD-907 remain separate obligations: split A/V playback, subtitle/audio-track controls, persisted playback position, and behavioral MediaSession command qualification still require their own production-path evidence before unified playback closeout.
