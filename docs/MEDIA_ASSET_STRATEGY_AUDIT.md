# Media asset strategy audit

This audit qualifies OYP-801 and OYP-803 against the current v1 architecture and portable domain model. OYP-802 remains separate because coordinated Media3 playback of persisted separate local audio/video assets requires Android-side playback qualification rather than a documentation-only conclusion.

## OYP-801 — Compatibility policy

The v1 compatibility policy is explicit and provider-neutral.

`MediaFormat` records container, MIME type, stream role, bitrate, video codec/resolution, audio codec/bitrate/channels/language, and whether the stream is directly playable. `QualityChoice` collapses provider formats into a user-facing choice with a `Compatibility` classification. The classification distinguishes `Preferred`, `Compatible`, `RequiresSeparateAssets`, `RequiresMuxing`, and `Unsupported` instead of pretending every remotely available stream is suitable for Android playback.

ADR-004 defines the selection policy: prefer directly playable Android Media3-compatible assets when the quality tradeoff is acceptable. Separate adaptive audio/video assets are allowed when Media3 can coordinate them locally. This keeps compatibility policy independent of YouTube-specific format identifiers and makes the direct-play preference visible in the normalized domain contract.

OYP-801 is therefore qualified by the combination of the accepted ADR and the typed compatibility model already exercised by the Rust test suite and CI.

## OYP-803 — Muxing decision gate

The muxing gate is also explicit and fail-closed.

The domain model can classify a curated choice as `RequiresMuxing`, so a mux requirement can be represented without silently adding a native muxer. ADR-004 states that FFmpeg or another muxer is not a baseline dependency. A muxer may be added only after deterministic compatibility tests identify a supported v1 case that cannot be played correctly from the persisted local asset plan.

Before such a dependency is admitted, ADR-004 requires an explicit review of binary size, Android ABI impact, licensing, maintenance burden, and supply-chain impact, followed by an architecture update. This is the intended OYP-803 decision gate: identify mux-required cases in the compatibility model, prefer direct/separate playable assets, and defer the dependency until tested playback evidence demonstrates necessity.

No current evidence establishes that a muxer is required for the supported v1 compatibility set, so adding one now would violate the gate rather than complete it.

## Qualification boundary

This audit does **not** close OYP-802. The portable model already represents multiple `LocalAsset` records per `LibraryItem`, and `DownloadPlan` can carry multiple typed assets, but OYP-802 additionally requires proof that Android Media3 can coordinate the selected persisted local audio/video assets. That proof belongs with Android playback implementation/qualification.

Qualification evidence for this audit is the exact-head CI run attached to the commit/PR that introduces this document. The existing CI executes the Rust domain tests and Android build/test qualification, ensuring the documented types and accepted architecture remain buildable at the audited head.
