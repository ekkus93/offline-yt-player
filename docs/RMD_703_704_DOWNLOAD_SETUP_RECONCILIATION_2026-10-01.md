# RMD-703/RMD-704 Download Setup Reconciliation — 2026-10-01

## Scope

This note reconciles current-master implementation and qualification evidence for:

- RMD-703 — Make Download Setup operational.
- RMD-704 — Make Advanced Options operational.

It is intentionally scoped to the Add/Analyze/Setup/Options/Schedule production UI path. It does not claim completion of full offline playback E2E, split A/V E2E, subtitle playback E2E, notification-control E2E, final RMD-1800 closeout, or any external YouTube/service-policy/legal release gate.

## Current-master implementation evidence

Exact implementation head audited: `0094c63a5929c489cc6b6adeaa5686a462a05f07`.

### RMD-703 — Download Setup

`AddScreen` in `app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt` now builds Download Setup state from the production source-analysis boundary rather than fabricated preview data:

- The Analyze action constructs a `SourceAnalysisUseCase` from the production `AppSourceAnalysisGateway` and executes `useCase.analyzeBlocking(started.ticket)` on `Dispatchers.IO`.
- Resolved `CoreSourceAnalysis` fields populate `DownloadSetupState`: source URL, bounded title, duration label, thumbnail URL, source provider, source media id, quality labels, quality choice IDs, estimated bytes, subtitle track IDs, audio format IDs, container options, and selected defaults.
- `DownloadSetupPreview` renders resolved title, provider/media source identity, canonical URL, bounded thumbnail preview, available quality options, duration, selected quality, and estimated size.
- Estimated size is shown only when `qualityEstimatedBytesByLabel` has a value; otherwise the UI renders `Size unavailable` rather than fabricating bytes.
- Selected setup state is preserved in Compose state and updated by Advanced Options through `onApply(edited)`.
- The Download button calls the production `AppDownloadControlGateway.enqueue(jobId, DownloadSelectionOptions(...))` boundary with selected quality, subtitle, and audio option IDs.

The current `Rmd1500AppPipelineFixtureTest` drives `OfflineYTPlayerApp(initialSharedUrl=...)` through the Add/Analyze/Download Setup/Download scheduling surface and asserts that the selected quality ID crosses the UI-to-download-control boundary.

### RMD-704 — Advanced Options

`AdvancedDownloadOptions` in `AppShell.kt` operates on source-derived `DownloadSetupState` rather than static option labels:

- Quality choices come from `edited.qualityOptions`; selecting one updates `qualityLabel`, selected quality-choice ID, estimated-size label, and split-audio requirements through `setupWithQuality(...)`.
- Subtitle choices come from resolved source subtitle options mapped by label to `selectedSubtitleTrackId`; users can also select no subtitles.
- Audio choices come from resolved source audio options mapped by label to `selectedAudioFormatId`; the UI exposes explicit audio selection only when the selected quality requires separate audio and multiple audio options exist.
- Container choices are displayed from source-derived `containerOptions`, while unsupported conversion/mux-only paths remain unavailable through the explicit `Container follows the selected source quality` policy text.
- Applying options writes the edited setup state back to the main Download Setup screen and keeps the selected option IDs available for scheduling.

## Qualification evidence

Current exact head before this note, `f0817f12e0c852ea5c96174f6a8a182f02a03dc4`, passed the full discovered push matrix:

- CI: `36828269272`
- Android smoke: `36828269326`
- Android FGS timeout / API-35 UIDT: `36828269149`
- Supply chain: `36828269478`
- CI evidence: `36828269193`
- Deterministic E2E fixture: `36828269308`

The documentation-only follow-up head `0094c63a5929c489cc6b6adeaa5686a462a05f07` is running the same matrix as the direct parent for this note.

Direct test evidence for the Download Setup/Advanced Options path includes:

- `app/src/test/java/com/ekkus/offlineytplayer/ui/AddScreenPolicyTest.kt`, which asserts the Add screen uses `SourceAnalysisUseCase`, renders source-derived provider/media/thumbnail/quality state, schedules with `DownloadSelectionOptions`, and exposes source-derived advanced quality/audio/subtitle/container controls.
- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/Rmd1500AppPipelineFixtureTest.kt`, which proves a deterministic shared URL enters the app pipeline, resolves through the app-owned source gateway, displays Download Setup metadata, presses Download, and observes the selected `qualityChoiceId` at the download-control boundary.
- The broader Android smoke lane includes production Compose behavior/golden/layout/accessibility coverage; those remain RMD-1400 evidence and do not by themselves close RMD-1500.

## Canonical TODO impact

RMD-703 is eligible for checklist reconciliation:

- Display real title/duration/thumbnail/source identity — satisfied through `CoreSourceAnalysis` to `DownloadSetupState` to `DownloadSetupPreview`.
- Display actual curated quality options — satisfied through `qualityOptions`, `qualityChoiceIdsByLabel`, and rendered `Quality options` summary.
- Display estimated size only when known/derivable — satisfied by per-quality `estimatedBytes` mapping and `Size unavailable` fallback.
- Persist selected options — satisfied by the edited `DownloadSetupState` retained by Compose and applied from Advanced Options.
- Download button schedules real durable work — satisfied by `AppDownloadControlGateway.enqueue` and `DownloadSelectionOptions`, with deterministic app-pipeline instrumentation proving selected quality identity crosses the scheduling boundary.

RMD-704 is eligible for checklist reconciliation:

- Populate actual subtitle languages/tracks — satisfied by resolved subtitle option labels and track IDs.
- Populate audio choices where multiple tracks are supported — satisfied by resolved audio option labels and format IDs, exposed when the selected quality requires separate audio.
- Limit container/format choices to real supported paths — satisfied by source-derived container options and by not offering unsupported conversion/mux-only choices.
- Apply changes back to Download Setup state — satisfied by `onApply(edited)` and the `Download options applied.` state transition.

Do not use this note to close full RMD-1501/RMD-1502/RMD-1503/RMD-1504 E2E tasks, because those still require completed runtime download, cold-start/offline playback, split A/V, subtitle, and share-path proof.
