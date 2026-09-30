# RMD-1102 Runtime Concurrency Implementation — 2026-09-30

RMD-1102 requires persisted download settings to affect production runtime behavior rather than remaining decorative UI state.

This change connects the persisted `maxConcurrentDownloads` setting to the real Android worker launch path. `DownloadWorkerExecutor` opens the production `SharedPreferencesAppSettingsStore`, reads the typed/clamped concurrency preference, and passes it across the generated UniFFI `FfiDownloadWorkerService.executeJob` boundary. The Rust service converts that value to the `DownloadWorker` concurrency input, where `bounded_download_concurrency` applies the authoritative core ceiling.

The existing RMD-408 core mapping remains authoritative for the hard maximum; Android settings remain a user preference bounded by that core policy. Wi-Fi-only runtime consumption remains handled by `DownloadConnectivityCoordinator`/`SchedulingDownloadControlGateway`. Default quality and subtitle defaults are consumed by the Add/Download Setup presentation/selection path and require final RMD-1102 reconciliation together with this runtime concurrency evidence.

This document records implementation only. RMD-1102 must not be checked until the exact implementation head passes the required CI/runtime matrix and the canonical remediation TODO is reconciled with that evidence.
