# RMD-600 production repositories/app-state reconciliation — 2026-09-20

This note audits RMD-600 against current production wiring without replacing or auto-checking the detailed remediation TODO. It records implementation evidence after PR #249 (`692665aeb5c5b77da20fcf45434424c95db2edc6`) and PR #250 (`841e410cf7c8eeca5e5a93396660bbb4fe50786d`).

## RMD-601 — Library repository wiring

Production `MainActivity.bootstrapProductionUi` opens `GeneratedUniffiCoreGateway` against the app-private SQLite database off the main thread and maps `listLibrary()` results into `LibraryScreenState`. `AppCoreGateway` also exposes `listLibrary(query)`, `getLibraryItem`, and `deleteLibraryItem`, with generated-model conversion centralized in the gateway. The UI has explicit loading/failure/ready states rather than a fabricated production empty list.

PR #249 added `AppStateRefresher`, a lifecycle-controlled observable-state bridge that periodically refreshes durable library state from the production core gateway while the activity is started, stops refreshing on `onStop`, and resumes on restart. The app now publishes repository-backed library updates into Compose state rather than relying solely on the initial bootstrap snapshot.

Remaining fail-closed gaps: production Library search/detail presentation is not yet surfaced through a dedicated lifecycle-aware screen state holder, and process-restorable detail/search state still needs stronger qualification.

## RMD-602 — Download repository wiring

Production bootstrap reads `listDownloadQueue()` from the same durable core database and maps real durable snapshots into `DownloadsScreenState`; the Downloads screen is not driven by a hard-coded empty list. Actual byte counts, total bytes, durable state, and last error are mapped into rows.

PR #249 extends that from a bootstrap snapshot to lifecycle-controlled refresh: `AppStateRefresher` calls `listDownloadQueue()` off the main thread on a bounded cadence and publishes durable queue progress/state changes back to Compose while the activity is started. Unit coverage verifies repository-state publication, duplicate-start prevention, and idempotent stop behavior.

Remaining fail-closed gaps: user-facing filters against actual states are not yet implemented in the production Downloads UI, and the current refresh bridge is polling-based rather than a core-emitted event stream.

## RMD-603 — Source-analysis repository/use case

`GeneratedUniffiSourceAnalysisGateway` centralizes the production UniFFI YouTube source service boundary, maps structured source errors, and executes only off the Android main thread. The Add/Share production path uses this gateway instead of fabricated preview metadata.

PR #250 added explicit superseded-request handling in `AddScreen`: in-flight analysis is cancelled when URL text changes, when clipboard paste replaces the URL, when a newer Analyze request starts, and when the composable leaves composition. Completed results are ignored unless their request URL still matches the active analysis identity. JVM policy coverage asserts the cancellation and active-request guards.

Remaining fail-closed gaps: Add/Share still represent analysis state locally in the composable rather than in a reusable source-analysis state holder, and the UI state taxonomy should be tightened to distinguish unsupported URL, network failure, source-changed/provider failure, and unavailable media consistently across Add and Share.

## RMD-604 — lifecycle-aware state architecture

Blocking core/source work is kept off the main thread. `MainActivity` now owns a lifecycle-controlled `AppStateRefresher` that starts in `onStart`, stops in `onStop`, closes in `onDestroy`, and publishes production repository state into Compose. `rememberSaveable` preserves some UI state and source-analysis jobs are cancelled on composable disposal.

This is an equivalent lifecycle-aware state holder for the current single-activity implementation, but final RMD-604 closure remains fail-closed: feature ViewModels or a more explicit state-holder layer are still needed for robust process recreation, detail/search restoration, and source-analysis state reuse beyond the current Add screen.

## Disposition

RMD-601 through RMD-603 now have real production gateway/data foundations plus lifecycle refresh/cancellation behavior. RMD-600 as a whole is **not complete**: repository-backed filters/detail/search presentation, reusable source-analysis state, and robust process-restoration semantics remain implementation work. These gaps must stay fail-closed in the detailed TODO until implemented and behaviorally qualified.
