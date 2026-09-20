# RMD-600 production repositories/app-state reconciliation — 2026-09-20

This note audits RMD-600 against current production wiring without replacing or auto-checking the detailed remediation TODO.

## RMD-601 — Library repository wiring

Production `MainActivity.bootstrapProductionUi` opens `GeneratedUniffiCoreGateway` against the app-private SQLite database off the main thread and maps `listLibrary()` results into `LibraryScreenState`. `AppCoreGateway` also exposes `listLibrary(query)`, `getLibraryItem`, and `deleteLibraryItem`, with generated-model conversion centralized in the gateway. The UI has explicit loading/failure/ready states rather than a fabricated production empty list.

The remaining gap is live observation: the current activity loads a snapshot during bootstrap rather than subscribing to a repository flow. Search/detail APIs exist at the gateway boundary, but the production presentation layer is not yet a lifecycle-aware observable repository.

## RMD-602 — Download repository wiring

Production bootstrap reads `listDownloadQueue()` from the same durable core database and maps real durable snapshots into `DownloadsScreenState`; the Downloads screen is not driven by a hard-coded empty list. Actual byte counts, total bytes, durable state, and last error are mapped into rows.

The remaining gap is live observation/filtering: production currently renders a bootstrap snapshot rather than a lifecycle-aware stream of queue/progress changes, and the presentation layer does not yet implement repository-backed state filters.

## RMD-603 — Source-analysis repository/use case

`GeneratedUniffiSourceAnalysisGateway` centralizes the production UniFFI YouTube source service boundary, maps structured source errors, and executes only off the Android main thread. The Add/Share production path uses this gateway instead of fabricated preview metadata.

The remaining gap is lifecycle/cancellation semantics in presentation state: `AddScreen` launches analysis from a composable coroutine scope and represents resolving/success/error text locally, but the gateway creates a fresh cancellation token per request without exposing cancellation of a superseded request. A dedicated state holder should map unsupported/network/source-changed states and cancel superseded analysis explicitly.

## RMD-604 — lifecycle-aware state architecture

Blocking core/source work is kept off the main thread, and `rememberSaveable` preserves some UI state. However, `MainActivity` currently owns bootstrap executor/gateways and passes snapshot state into Compose; feature ViewModels (or an equivalent lifecycle-aware observable state holder) are not yet present. Consequently repository-flow collection, continuous queue/library updates, and robust restoration across process recreation remain open.

## Disposition

RMD-601 through RMD-603 have real production gateway/data foundations and no longer depend on fabricated empty production data. RMD-600 as a whole is **not complete**: observable repositories/state holders, live filtering/progress updates, explicit superseded-analysis cancellation, and lifecycle/process-restoration semantics remain implementation work. These gaps must stay fail-closed in the detailed TODO until implemented and behaviorally qualified.
