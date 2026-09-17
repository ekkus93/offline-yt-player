# Subtitle Asset Audit

OYP-1202 qualification establishes a provider-independent subtitle policy.

- `MediaInfo.subtitles` is the normalized enumeration surface exposed by source adapters.
- Explicit user track selection wins; otherwise a preferred-language human-authored track is selected before generated/fallback tracks.
- Selected tracks receive deterministic local paths under `items/<item>/subtitles/`.
- Language and source format remain explicit in `SubtitleAssetPlan`; supported local formats map to Media3-compatible MIME types (`text/vtt`, SubRip, TTML).
- Persisted `MediaKind::Subtitle` assets are independently discoverable for local playback and require no network lookup.
- Unsafe identifiers and unsupported local subtitle formats fail closed.

The Android Media3 integration consumes the local subtitle asset path and MIME metadata rather than provider URLs, preserving offline playback semantics.
