# RMD-702 Analyze Pipeline Reconciliation — 2026-09-27

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.
Required execution guidance: `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`.

This note records current-master evidence for RMD-702. It does not replace the canonical TODO; the canonical checklist must still be reconciled when a safe line-level or generated whole-file edit path is available.

## Scope

RMD-702 requires the Add screen Analyze path to:

- remove fabricated preview metadata from the production path;
- validate through the shared hardened policy;
- call the production source-analysis gateway; and
- show real metadata, quality, and error state.

## Production-path evidence

`MainActivity.bootstrapProductionUi()` opens `GeneratedUniffiSourceAnalysisGateway.open()` on the production bootstrap executor and passes that gateway into `OfflineYTPlayerApp`. Production UI therefore receives the generated UniFFI-backed source gateway rather than a preview fixture.

`GeneratedUniffiSourceAnalysisGateway.analyze(...)` enforces the shared URL contract by calling `SupportedUrlPolicy.normalizeSupportedUrl(...)` before invoking generated source services. Unsupported input returns a structured `UNSUPPORTED_SOURCE` `CoreGatewayError` with retryability disabled. The gateway also rejects main-thread use with a `Looper` check so source resolution remains off the Android UI thread.

For supported input, `GeneratedUniffiSourceAnalysisGateway` calls the generated `FfiYouTubeSourceService.resolve(...)` and `listChoices(...)` methods with a generated cancellation token, converts generated errors into `CoreGatewayError`, and maps generated media and choice records into provider-neutral `CoreSourceAnalysis` / `CoreSourceQualityChoice` data. Title and diagnostic strings are passed through `SourceMetadataPolicy` bounds/sanitization.

`AppShell.kt` uses the gateway from the Add screen Analyze button. It executes `gateway.analyze(requestUrl)` on `Dispatchers.IO`, cancels superseded analysis jobs, ignores stale results by matching the active URL, and maps successful analysis into `DownloadSetupState` using the returned source URL, title, duration, thumbnail URL, quality label, estimated size, and available quality labels. Errors are surfaced through the Add screen status text instead of fabricated preview metadata.

## Behavioral coverage

`ProductionComposeBehaviorTest.add_analyze_setup_and_download_use_gateway_boundaries` exercises the production Compose Add flow through the app-owned source-analysis boundary and verifies Analyze reaches the provided gateway, shows Download Setup, displays resolved title/duration/quality/size state, exposes quality choices, and schedules Download through the app-owned download-control boundary.

## Exact-head qualification

The RMD-702 production/evidence paths are present on current master. The latest exact evidence heads are:

- `1e9cf7fed8a211c0bf897da7ef6497a86b2c4b21`, which added RMD-701/RMD-702-adjacent evidence and passed CI `36326218336`, Android smoke `36326218328`, Android FGS timeout `36326218338`, and Supply chain `36326218331`.
- `2a691287b9c1d400a2e2ac1ebac62a0a7dc5d3cd`, which added the missing clipboard Compose coverage and passed CI `36325053089`, Android smoke `36325053119`, Android FGS timeout `36325053093`, and Supply chain `36325053088`.

## Canonical TODO reconciliation intent

The next safe canonical TODO edit should mark only RMD-702 complete and cite this document plus the exact head and run IDs above.

RMD-703 should remain unchecked because selected options are not yet durably persisted from the setup UI. RMD-704 should remain unchecked because subtitle/audio/container choices are still not populated from actual available tracks. RMD-705 should remain unchecked until the share pipeline has its own complete unsupported/multiple/no-URL evidence and setup/back-stack reconciliation.