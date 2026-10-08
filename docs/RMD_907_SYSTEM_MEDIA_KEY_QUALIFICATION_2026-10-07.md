# RMD-907 Android system media-key qualification

Exact master commit: `7475bc07afed86ee1e4d7846ca0bfd5401a3e303`.

The device-side `PlaybackSessionBehaviorInstrumentedTest.systemMediaButtonsPauseResumeAndSeekTheCanonicalSession` exercises Android `AudioManager.dispatchMediaKeyEvent` play, pause, fast-forward, and rewind events against `PlaybackSessionService` using a generated local WAV fixture. It checks resulting playback state and position through a `MediaController` attached to the canonical session.

All six workflows passed for this exact commit: CI 37707460711, Android smoke 37707460692, Android FGS timeout 37707460738, Supply chain 37707460662, CI evidence 37707460660, and Deterministic E2E fixture 37707460706.

This qualifies the RMD-907 headset/system play-pause/seek subtask. It does not yet qualify the separate current-item/position agreement between displayed Compose UI state and the session; leave that subtask unchecked until explicit UI-state evidence exists.
