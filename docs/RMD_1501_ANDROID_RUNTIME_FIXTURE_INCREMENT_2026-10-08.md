# RMD-1501 Android runtime fixture increment — 2026-10-08

The sole canonical checklist remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; this note is supporting evidence only.

## What this increment proves

`Rmd1501AndroidRuntimeFixtureInstrumentedTest` runs inside the packaged Android app process and uses the production database name, generated UniFFI download-control/core gateways, `DownloadWorkerExecutor`, the generated `FfiDownloadWorkerService`, and the real core worker loop.

The test starts from clean app-private state, seeds one deterministic durable executable work item, serves the media asset from a loopback HTTP fixture, executes the job through the Android runtime worker bridge, and verifies via `GeneratedUniffiCoreGateway` that the durable queue reaches `COMPLETED`, the Library contains a completed item, and the asset exists under the production library root with the expected byte length and exact fixture bytes. The generated library-playback gateway also exports the completed item as a playable local asset with the expected relative path.

## What this does not yet close

This does not complete RMD-1501. It intentionally does not mark any canonical checkbox complete because source analysis, option selection, scheduler dispatch, offline cold start, canonical MediaSession playback, and no-network playback assertion still require one integrated Android E2E that begins at the app UI/share pipeline and ends at local playback.

## Why it was added

The previous `Rmd1500AppPipelineFixtureTest` proved the Add/Share → Analyze → Download Setup UI and scheduling boundary with recording/fake gateways. The Rust deterministic fixture lane proved core download and playback-asset export outside Android. This increment closes part of the Android gap between those lanes by proving the packaged Android worker bridge can execute deterministic durable work into completed Library state without live-provider access.

## Qualification note

The prior exact-head Android smoke run `37738839262` passed on `c4976e5001d92c5d4f1bc9f9ef6e678707d05770`. Its API 35 FGS counterpart `37738839264` failed when the software emulator stopped responding to ADB and Gradle saw `Unknown API Level`; this is not accepted as passing qualification. The FGS workflow now explicitly waits for a readable API-35 ADB property before Gradle device discovery. All new checks remain pending exact-head CI, and the canonical RMD-1501 checkboxes stay unchecked.
