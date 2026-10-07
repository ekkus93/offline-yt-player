# RMD-1500 incremental Android pipeline evidence — 2026-10-01

This document records incremental qualification evidence for the canonical remediation TODO at `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`. It does **not** close RMD-1501 through RMD-1507; those remain governed solely by the canonical checklist and require their complete production-path acceptance evidence.

## Latest exact qualified head

Exact `master` SHA: `2ffbf1d640702de3609f8ee88a96ae2888232945`.

The exact head passed the complete currently configured push matrix:

- CI run `37659134787` — success.
- Android smoke run `37659134871` — success.
- Android FGS timeout / API-35 runtime run `37659134986` — success.
- Supply chain run `37659134962` — success.
- CI evidence run `37659134991` — success.
- Deterministic E2E fixture run `37659134868` — success.

Evidence artifacts observed on that exact SHA include:

- `android-smoke-evidence-37659134871`.
- `android-fgs-timeout-evidence-37659134986`.
- `supply-chain-evidence-37659134962`.
- `ci-evidence-manifest-37659134991`.
- `deterministic-e2e-fixture-evidence-37659134868`.

## Android app-pipeline proof now in the smoke lane

`app/src/androidTest/java/com/ekkus/offlineytplayer/ui/Rmd1500AppPipelineFixtureTest.kt` drives the production Compose application surface from a shared supported URL through:

1. Share/Add entry into `OfflineYTPlayerApp`.
2. Source analysis through the app-owned source-analysis gateway boundary.
3. Resolved metadata and curated quality-option presentation.
4. Download Setup presentation.
5. Advanced Options selection for source-derived subtitle and audio choices.
6. Applying the edited options back into the Download Setup state.
7. The production UI's Download action crossing the download-control gateway boundary with `DownloadSelectionOptions` carrying the selected quality, subtitle, and audio IDs.

`.github/workflows/android-smoke.yml` includes `Rmd1500AppPipelineFixtureTest` in the regular API-29 instrumentation smoke command. Exact-head Android smoke run `37659134871` passed with the expanded test in the executed class list. This proves the deterministic Add/Share -> Analyze -> Download Setup -> Advanced Options -> scheduling-boundary slice is now continuously covered by the Android smoke lane.

## Earlier incremental evidence

Exact `master` SHA `a47f0cb636e602ea6ad93669a1708a208215d9ca` previously qualified the initial RMD-1500 app-pipeline fixture in the Android smoke lane. That version proved source analysis, resolved metadata/quality presentation, Download Setup selection, and the selected quality-choice ID crossing the UI-to-download-control boundary. The latest exact head above extends that same smoke-lane proof to source-derived subtitle and audio option IDs.

## What this evidence does not prove

The test deliberately uses deterministic app-owned test gateways. Therefore it does not by itself satisfy the canonical RMD-1501 requirement to continue through the real Android scheduler/runtime, core worker, durable Library promotion, network disablement, process kill/cold start, and MediaSession-owned local playback in one end-to-end Android flow.

Likewise, it does not close:

- RMD-1502 split A/V cold-start offline playback;
- RMD-1503 subtitle persistence/selection/display after offline cold start;
- RMD-1504 complete ACTION_SEND -> runtime download -> Library/back-stack flow;
- RMD-1505 Android storage-failure/ENOSPC flow;
- RMD-1506 Android connectivity-loss/resume flow;
- RMD-1507 notification pause/resume/cancel durable-state flow.

The deterministic core fixture lane and API-35 runtime lane provide strong adjacent proof, but the canonical TODO explicitly forbids treating policy or disconnected stage tests as substitutes for the required RMD-1500 production-path E2E evidence.

## Next qualification target

The shortest safe next slice is to join the already-proven Android app pipeline to the real `AndroidDownloadExecutionScheduler`/runtime and durable Library state in deterministic instrumentation, then extend that fixture through cold-start MediaSession playback. Reuse the existing scheduler/runtime, core fixture, Library, and playback infrastructure rather than adding parallel test-only implementations.
