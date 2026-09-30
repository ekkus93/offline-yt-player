# RMD-705 Share workflow reconciliation — 2026-09-30

## Result

The RMD-705 production Share workflow is implemented on current `master`; this note records the production wiring and existing behavioral evidence without claiming the broader RMD-1504 end-to-end download requirement.

## Production wiring

`MainActivity.onCreate()` parses Android share input through `ShareInput.parse(intent.action, intent.type, Intent.EXTRA_TEXT)` and passes the accepted canonical URL into `OfflineYTPlayerApp(initialSharedUrl = ...)`. `ShareInput` accepts only `ACTION_SEND` + `text/plain`, trims and bounds untrusted share text to 8192 characters, and delegates URL extraction/validation to the same hardened `SupportedUrlPolicy.singleSupportedUrlFromText` policy used by Add/source analysis. Unsupported schemes, unsupported hosts, malformed input, oversized input, and ambiguous/multiple supported URLs fail closed.

`OfflineYTPlayerApp` opens the Add route for accepted shared input and seeds the same URL state consumed by the normal Analyze action. Analyze then uses the production `GeneratedUniffiSourceAnalysisGateway` supplied by `MainActivity`, so Share does not have a parallel preview/provider path.

## Behavioral qualification

`ShareInputTest` covers action/MIME gating, bounded input, malformed/unsupported input, and supported URL extraction. `ProductionComposeBehaviorTest.shared_input_opens_add_flow_and_back_stack_remains_navigable` proves accepted shared input opens the Add flow and that navigation remains usable after Share entry. `ProductionComposeBehaviorTest.add_analyze_setup_and_download_use_gateway_boundaries` proves the seeded Add state proceeds through the app-owned analysis and download-control boundaries. RMD-1402 already records this Share navigation/back-stack behavior as qualified production Compose coverage.

The same implementation is present on exact master `682e7dda55987046ce3bb1694a377d98b0c9dd3b`, which passed all six configured workflows: CI `36676333860`, Android smoke `36676333845`, Android FGS timeout/API-35 UIDT `36676333767`, Supply chain `36676333779`, CI evidence `36676333747`, and Deterministic E2E fixture `36676333873`.

## Scope boundary

This evidence is sufficient for RMD-705's bounded Share input, shared validation, real Analyze/Setup routing, back-stack behavior, and safe unsupported/multiple/no-URL handling. It does not close RMD-1504, which additionally requires a full Share → schedule/download → durable Library end-to-end proof through the Android runtime.
