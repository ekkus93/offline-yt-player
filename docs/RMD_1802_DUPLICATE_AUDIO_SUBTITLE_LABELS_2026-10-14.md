# RMD-1802 — Duplicate audio/subtitle provider labels (2026-10-14)

## Independent review and corrected scope

`AddScreen` uses provider-controlled labels as map keys for quality, audio and subtitle executable IDs. However, the production `GeneratedUniffiSourceAnalysisGateway` **already** calls `disambiguateSourceQualityLabels`, with JVM regressions for identical and suffix-colliding quality labels. Therefore the earlier TODO note claiming that all production quality choices silently overwrite one another was too broad.

The actual uncovered production defect was in **audio and subtitle** choices: these were mapped directly from the generated FFI values without label disambiguation, so `associate { label to id }` in the production Compose Add path silently overwrote an earlier format/track with the same presentation key. Auto-generated subtitles additionally append `(auto)` in `sourceSubtitleLabel`, so the disambiguation must operate on the effective rendered key, not just the provider's raw label.

## Correction

`GeneratedUniffiSourceAnalysisGateway.analyze` now disambiguates audio and subtitle labels at the production boundary before passing `CoreSourceAnalysis` to Add/Download Setup. Both functions reserve original labels/keys so suffixes cannot steal a genuine later provider label, and preserve original executable format/track IDs and order. Subtitle disambiguation uses the exact effective `(auto)` presentation rule. Host-JVM `AppSourceAnalysisGatewayMappingTest` covers duplicate audio formats, pre-existing suffix collisions, duplicate manual and generated subtitle tracks, and preserved IDs. Existing production quality disambiguation remains unchanged.

This is implementation and JVM mapping evidence, not final packaged Compose proof. Exact-head CI and the full required Android matrix must pass before the finding can qualify. RMD-1802/1803/1805 and external policy/legal approval remain open.

## Packaged Compose production-path qualification

`DuplicateProviderOptionsInstrumentedTest.duplicateAudioAndSubtitleLabelsEnqueueSelectedOriginalIds` feeds duplicate audio/subtitle labels through the production boundary disambiguators into the actual `OfflineYTPlayerApp` Add/Analyze/Options/Download Compose flow. It selects the second provider audio format and subtitle track after a reserved-suffix collision and asserts the enqueue gateway receives the original `video-720`, `sub-b`, and `audio-b` identities, rather than silently overwritten choices. The required API-29 smoke script invokes this exact test in its own instrumentation process and verifies the XML method evidence. This is an executable regression, but final qualification still requires a passing exact-head eight-workflow matrix; no engineering closeout checkbox is changed.
