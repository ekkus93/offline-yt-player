# RMD-901 canonical playback-session reconciliation — 2026-09-29

RMD-901 requires `PlaybackSessionService` to own the canonical ExoPlayer, expose one MediaSession over that player, configure audio-focus/noisy handling, and release both player and session with service lifecycle.

## Current production path

`app/src/main/java/com/ekkus/offlineytplayer/playback/PlaybackSessionService.kt` satisfies the production ownership requirements directly:

- `PlaybackSessionService : MediaSessionService` creates the ExoPlayer in `onCreate()`.
- The service creates exactly one app-owned `MediaSession` over that player and returns it from `onGetSession`.
- `setAudioAttributes(AudioAttributes.DEFAULT, true)` delegates audio-focus handling to Media3 for the canonical player.
- `setHandleAudioBecomingNoisy(true)` enables canonical-player noisy-route handling.
- `onDestroy()` releases the session player, releases the MediaSession, clears the service reference, and then completes service teardown.

## Deterministic qualification

`app/src/test/java/com/ekkus/offlineytplayer/playback/PlaybackSessionOwnershipTest.kt` guards the production source shape so future changes cannot silently split canonical ownership, drop the MediaSession binding, remove audio-focus/noisy handling, or omit lifecycle release.

This evidence is intentionally scoped to RMD-901. RMD-902 through RMD-907 remain separate: controller-driven Compose playback, split A/V, subtitle/audio-track controls, position persistence, and behavioral MediaSession qualification must still be proven independently before playback closeout.
