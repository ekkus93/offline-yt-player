# RMD-203 app-owned core gateway reconciliation — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, RMD-203.

This note audits RMD-203 against current `master` at `8b34e44a2645d161a629e1bf972dc7554324883b`. It records production-path evidence without changing canonical checkbox state before this evidence itself is qualified and merged.

## Stable app-owned interface

`app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt` defines `AppCoreGateway : Closeable` as the app-owned boundary for startup reconciliation, library list/get/delete, and durable download-queue reads. `GeneratedUniffiCoreGateway` implements that interface while keeping generated binding types behind the adapter. `AppDownloadControlGateway.kt` similarly defines the stable app-owned control boundary for enqueue/pause/resume/cancel/retry.

## Centralized model/error conversion

`AppCoreGateway.kt` owns app models (`CoreSourceIdentity`, `CoreLibraryItem`, `CoreDownloadSnapshot`, `CoreGatewayError`, `CoreGatewayResult`) and centralizes generated-record conversion in `mapLibraryItem`, `mapDownloadSnapshot`, and `readError`. Presentation code therefore consumes app-owned models rather than provider/generated UniFFI records. `AppDownloadControlGateway.kt` uses the same app-owned result/error model for control operations.

## Off-main-thread execution

`CoreCallDispatcher` owns a dedicated single-thread executor for blocking Rust/UniFFI work. `GeneratedUniffiCoreGateway` exposes dispatcher-backed async methods and every synchronous FFI operation calls `checkNotMainThread()`, which rejects Android-main-thread invocation. `GeneratedUniffiDownloadControlGateway` applies the same rule. Production `MainActivity.bootstrapProductionUi()` performs gateway opening, startup reconciliation, initial repository reads, and state refresh work on its dedicated `bootstrapExecutor`, publishing only mapped state back through `runOnUiThread`.

## Cancellation and lifecycle semantics

The gateway boundary is `Closeable`; `CoreCallDispatcher.close()` calls `shutdownNow()`. `MainActivity.onDestroy()` closes the state refresher, core gateway, download-control gateway, playback gateway, source-analysis gateway, settings subscription/store, and its bootstrap executor. This provides an explicit lifecycle/cancellation boundary for in-flight app-owned gateway work rather than relying on generated foreign-future cancellation semantics.

## Repository/state APIs suitable for presentation state holders

`AppCoreGateway` exposes operation-sized durable state APIs rather than database handles: startup reconciliation, library list/get/delete, and durable download-queue reads. `MainActivity` maps these results into `LibraryScreenState` and `DownloadsScreenState`, and `AppStateRefresher` refreshes those production states while the Activity is started. The composition root injects production download/source gateways and mapped repository state into `OfflineYTPlayerApp`.

## Deterministic fake

`FakeCoreGateway` implements `AppCoreGateway` with deterministic in-memory library/download state, query filtering, item lookup/deletion, queue listing, and configurable startup-reconciliation results. `FakeDownloadControlGateway` implements the control interface and records enqueue/pause/resume/cancel/retry calls for deterministic Android/UI tests.

## Runtime and exact-head qualification

The packaged-app `GeneratedUniffiCoreGatewaySmokeTest` loads `offline_yt_core`, opens an app-private temporary SQLite database, exercises real generated gateway library and durable-queue calls, verifies structured error conversion, performs enqueue/pause/list state round-trips, and cleans up the app-private root. `.github/workflows/android-smoke.yml` runs that test in the bounded API-29 runtime smoke tier.

Current master `8b34e44a2645d161a629e1bf972dc7554324883b` passed exact-head CI `36002691070`, Android smoke `36002691159`, and API-35 FGS timeout qualification `36002691152`. Those runs exercise the unchanged gateway, generated-binding, JVM/Android build, and packaged runtime paths audited above.

## Reconciliation result

All six RMD-203 subtasks have current-master production implementation and qualification evidence. After this evidence note itself passes exact-head CI, merges, and post-merge master qualification succeeds, RMD-203 is ready for canonical TODO checkbox reconciliation together with the already-audited RMD-201, RMD-202, and RMD-204 items. The canonical TODO remains authoritative and is intentionally unchanged by this evidence-only commit.
