# RMD-501 Durable Queue State Evidence — 2026-09-26

## Scope

This note records focused evidence for RMD-501 without changing the canonical checklist state by itself. The canonical checklist remains the source of truth and should only be reconciled after this evidence is merged to `master` and exact-head CI has passed.

## Production paths

- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt` defines `CoreDownloadState` for queued, resolving, downloading, paused, retry-wait, failed, verifying, completed, and canceled durable queue states.
- `GeneratedUniffiCoreGateway.listDownloadQueue()` calls the generated Rust/UniFFI `downloadQueue` method off the Android main thread and maps durable FFI snapshots into app-owned `CoreDownloadSnapshot` records.
- `MainActivity.bootstrapProductionUi()` opens `GeneratedUniffiCoreGateway` against the app-private database, renders initial Downloads state from `listDownloadQueue()`, and starts `AppStateRefresher` so the UI continues to observe durable queue state instead of service-local booleans.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundControlDispatcher.kt` routes notification pause/resume/cancel actions through the same `AppDownloadControlGateway` path used by UI controls.

## Tests

- `app/src/androidTest/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGatewaySmokeTest.kt` loads the packaged Rust library, opens a temporary app-private database, enqueues through the generated download-control gateway, and verifies durable queued/paused state through the generated core gateway.
- `app/src/test/java/com/ekkus/offlineytplayer/AppStateRefresherTest.kt` verifies the lifecycle-controlled state bridge publishes repository-backed Library and Downloads state.
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundControlDispatcherTest.kt` verifies notification pause/resume/cancel dispatch through the shared gateway path.
- `app/src/test/java/com/ekkus/offlineytplayer/coregateway/Rmd501DurableQueueGatewayTest.kt` adds explicit regression coverage that all generated durable queue states map to app-owned states and that the core gateway surface exposes durable snapshots rather than a service-local state model.

## Current limitation

This evidence supports RMD-501. It does not claim RMD-502 worker-loop completion, RMD-508 connectivity behavior, RMD-1500 deterministic E2E completion, or final closeout.
