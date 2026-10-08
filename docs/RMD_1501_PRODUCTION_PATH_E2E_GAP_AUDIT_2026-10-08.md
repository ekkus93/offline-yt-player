# RMD-1501 production-path E2E gap audit — 2026-10-08

The sole canonical checklist remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; the Android acceleration plan remains mandatory execution guidance.

Current Android smoke executes `Rmd1500AppPipelineFixtureTest`, which verifies Add/Share input, deterministic analysis metadata, selected setup options, saved-state restoration, and enqueue options. Its `RecordingSourceAnalysisGateway` and `FakeDownloadControlGateway` do not execute the production provider, durable queue, Android scheduler, core worker, asset persistence, or MediaSession playback.

The deterministic Rust fixture lane independently verifies real asset download, durable Library persistence, cold-start reopen, and offline playback-asset export. It does not prove the Android UI-to-worker-to-MediaSession chain. Neither test alone qualifies RMD-1501.

Next qualification must use one isolated deterministic local fixture with a production-equivalent source adapter, durable UniFFI gateway, Android runtime scheduler and worker, persisted assets, offline cold start, canonical MediaSession playback, and an observable no-network assertion. Do not enable live-provider calls in normal CI or mark any RMD-1501 checkbox complete until that integrated evidence exists and passes exact-head CI.
