# OYP-1603 Playback Settings Audit

The playback settings policy exposes every required preference: remember-position behavior, default playback speed, skip interval, subtitle default, and audio default.

Playback speed is bounded to 0.5x-2.0x. Skip intervals are restricted to the compact set 5, 10, 15, and 30 seconds so UI controls cannot introduce arbitrary or unbounded values. Defaults are deterministic: remember position enabled, 1.0x speed, 10-second skip, subtitles off, and original audio.

`PlaybackSettingsPolicyTest` qualifies the complete preference surface and both numeric bounds.
