# RMD-1006 Downloads Screen Reconciliation — 2026-10-01

## Scope

This note reconciles the current-master implementation and qualification evidence for canonical remediation item RMD-1006 (Operational Downloads screen) in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

It does not claim completion of the broader RMD-1500 end-to-end flows, notification-control E2E, final RMD-1800 closeout, or any external YouTube/service-policy/legal gate.

## Current-master implementation evidence

Exact implementation head audited: `f0817f12e0c852ea5c96174f6a8a182f02a03dc4`.

The production Downloads screen is repository-backed rather than hard-coded:

- `MainActivity.bootstrapProductionUi()` opens `GeneratedUniffiCoreGateway` against the app-private `offline-yt-player.sqlite3` database, calls `listDownloadQueue()`, and maps the result into `DownloadsScreenState`.
- `AppStateRefresher` periodically refreshes `gateway.listDownloadQueue()` off the UI thread and republishes the result to production Compose state while the activity is started.
- `MainActivity.toDownloadsScreenState()` maps `CoreDownloadSnapshot` records into `DownloadRowModel` rows with durable job id, durable state, downloaded/total bytes, user-visible size/progress text, sanitized error state, and title metadata from `DownloadPresentationGateway`.

The screen applies real filters and renders actual queue rows:

- `DownloadsScreen` derives visible rows from `DownloadScreenPolicy.matchesFilter`, so All/Active/Paused/Failed/Completed operate on actual mapped queue states.
- Empty/loading/error states are explicit: `DownloadsScreenState.Loading`, `Ready`, `Unavailable`, and `Failed` render distinct production states instead of silently fabricating an empty queue.
- `DownloadScreenPolicy.detail()` renders state label, bounded percent, size, speed label, and ETA label from row state.

Pause/resume/cancel/retry are wired to the real control boundary:

- `DownloadsScreen` passes row actions through `DownloadRowControlBinding.invoke(action, row.id, gateway)`.
- The production gateway is `SchedulingDownloadControlGateway`, wrapping `GeneratedUniffiDownloadControlGateway` and `AndroidDownloadExecutionScheduler`, so UI controls use the same durable control path as the runtime scheduler.
- RMD-500 evidence covers the underlying durable queue semantics for pause, resume, cancel, and retry; RMD-508 covers connectivity-driven pause/resume policy against the same durable queue.

Illegal actions are disabled by omission from the rendered row:

- `DownloadScreenPolicy.legalActions()` renders only Pause/Cancel for Active rows, Resume/Cancel for Paused rows, Retry/Cancel for Failed rows, and no mutable actions for Completed rows.
- `DownloadRow()` renders only that legal-action list plus Details, so unsupported state/action combinations are not exposed as active production buttons.

## Qualification evidence

Current exact head `f0817f12e0c852ea5c96174f6a8a182f02a03dc4` passed the complete discovered push matrix:

- CI: `36828269272`
- Android smoke: `36828269326`
- Android FGS timeout / API-35 UIDT: `36828269149`
- Supply chain: `36828269478`
- CI evidence: `36828269193`
- Deterministic E2E fixture: `36828269308`

Prior implementation evidence remains relevant for the same production paths:

- `docs/RMD_602_DOWNLOAD_REPOSITORY_RECONCILIATION_2026-09-27.md` records repository-backed Downloads state, filters, row presentation, and progress/error mapping.
- `docs/RMD_500_DURABLE_DOWNLOAD_RECONCILIATION_2026-09-26.md` records durable pause/resume/cancel/retry semantics and the shared control path.
- `docs/RMD_508`-related evidence in the canonical TODO records production connectivity pause/resume integration against the same durable queue.

## Canonical TODO impact

RMD-1006 is now eligible for checklist reconciliation once this exact-head evidence is carried into `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`:

- Render durable queue — satisfied by `MainActivity` + `AppStateRefresher` + `GeneratedUniffiCoreGateway.listDownloadQueue()` mapping.
- Apply real filters — satisfied by `DownloadScreenPolicy.matchesFilter` over mapped durable queue rows.
- Show actual progress/state/error/speed/ETA — satisfied by durable snapshot mapping and row detail presentation, with progress derived only from real transferred/total bytes.
- Wire pause/resume/cancel/retry — satisfied by `DownloadRowControlBinding` through `SchedulingDownloadControlGateway` and the generated durable control gateway.
- Disable actions illegal for current state — satisfied by `DownloadScreenPolicy.legalActions()` and row rendering that omits illegal controls.

Do not use this note to close RMD-1507 notification-control E2E, RMD-1501 full offline fixture E2E, or final RMD-1800 closeout; those remain separate checklist items.
