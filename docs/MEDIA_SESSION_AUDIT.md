# MediaSession Audit

## Scope

OYP-904 qualifies the Android Media3 session boundary used for platform playback controls.

## Implementation

- `PlaybackSessionService` owns a Media3 `MediaSession` backed by `ExoPlayer`.
- The manifest exposes the service through the `androidx.media3.session.MediaSessionService` action and declares the media-playback foreground-service type.
- MediaSession provides the standard platform command surface consumed by lock-screen, notification, Bluetooth, and headset controllers.
- `ExoPlayer.setAudioAttributes(..., true)` delegates audio-focus acquisition/release behavior to Media3.
- `setHandleAudioBecomingNoisy(true)` pauses appropriately when an audio route such as wired headphones is disconnected.
- Session/player resources are explicitly released when the service is destroyed.

## Qualification

`PlaybackSessionPolicyTest` locks the required policy flags, manifest service contract, MediaSession construction, audio-focus delegation, and becoming-noisy handling. Full device playback behavior remains covered by OYP-905 and end-to-end qualification.
