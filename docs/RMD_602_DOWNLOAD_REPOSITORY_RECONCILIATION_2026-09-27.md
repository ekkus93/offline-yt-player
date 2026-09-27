# RMD-602 Download Repository Wiring Reconciliation — 2026-09-27

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.
Required execution guidance: `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`.

This note records current-master evidence for RMD-602 only. It is not a replacement TODO and does not mark the canonical checklist by itself.

## Scope

RMD-602 requires the production Downloads screen to stop using hard-coded empty data and instead render durable queue state, apply real filters, and update rows from real progress/events.

This note intentionally does not close RMD-601, RMD-603, or RMD-604. The existing RMD-600 audit still identifies separate library search/detail, reusable source-analysis state, and process-restoration/state-holder gaps that remain fail-closed in the canonical TODO.

## Evidence

### Durable queue replaces hard-coded Downloads data

`MainActivity.bootstrapProductionUi()` opens `GeneratedUniffiCoreGateway` against the app-private SQLite database and calls `listDownloadQueue()` for the initial production Downloads state. It maps the returned durable queue snapshots into `DownloadsScreenState` via `CoreGatewayResult<List<CoreDownloadSnapshot>>.toDownloadsScreenState()` rather than supplying a hard-coded empty list.

`AppStateRefresher` keeps the screen state live while the activity is started. It calls `gateway.listDownloadQueue()` off the UI thread on a bounded cadence and publishes the returned durable queue result through the `onDownloads` callback. `MainActivity` wires that callback back into Compose state through `downloadsState = result.toDownloadsScreenState()`.

Relevant files:

- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/AppStateRefresher.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGateway.kt`

### Real filters apply to mapped durable state

`DownloadsScreen` derives `visibleRows` by applying `DownloadScreenPolicy.matchesFilter(row, filter)` to the mapped `DownloadRowModel` list. `DownloadScreenPolicy.Filters` exposes All, Active, Paused, Failed, and Completed filters, and the filter predicate compares each row's actual `DownloadUiState` rather than static fixture state.

Coverage in `DownloadsOperationalScreenTest.downloadsScreenExposesEveryRuntimeFilter` and `DownloadsOperationalScreenTest.downloadsScreenFiltersAgainstCurrentDurableState` verifies the filter set and state-based behavior.

Relevant files:

- `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/ui/DownloadsOperationalScreenTest.kt`

### Rows update from actual progress/state/error fields

`toDownloadsScreenState()` maps each `CoreDownloadSnapshot` into `DownloadRowModel` with the durable job id, mapped state, transferred bytes, total bytes, computed percentage only when a positive total is known, and sanitized last-error diagnostics.

`DownloadScreenPolicy.detail(row)` renders state label, percent, size, speed label, and ETA label, while `DownloadRow` renders row-level errors when present. This preserves the RMD-507 invariant that unknown-length progress must not fabricate unsupported numeric ETA/progress claims.

Coverage in `DownloadsOperationalScreenTest.downloadRowsShowProgressStateErrorSpeedAndEtaFields` verifies that production row rendering includes state, progress detail, speed, ETA, and error surfaces. `DownloadRowPolicyTest` separately covers real-progress presentation invariants and unknown-length behavior under RMD-507.

Relevant files:

- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt`
- `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/ui/DownloadsOperationalScreenTest.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/ui/DownloadRowPolicyTest.kt`

## Canonical TODO reconciliation intent

After this evidence note is qualified and merged, the canonical TODO may mark only the RMD-602 subtasks complete:

- `Replace hard-coded empty Downloads data with durable queue state.`
- `Implement filters against actual states.`
- `Update rows from actual progress/events.`

The canonical TODO should keep RMD-601, RMD-603, and RMD-604 unchecked until their separate fail-closed gaps are implemented and qualified.

## Qualification

This evidence note was prepared from current master `762d30a5987aaa700e61e9a5284a1e299cfda167` after PR #390 reconciled RMD-500/RMD-508 in the canonical TODO. The reconciliation PR for this note must pass exact-head CI, Android smoke, Android FGS timeout, and Supply chain before it can be merged and cited by the canonical checklist.
