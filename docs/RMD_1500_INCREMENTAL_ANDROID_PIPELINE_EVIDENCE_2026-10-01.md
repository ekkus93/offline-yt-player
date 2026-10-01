# RMD-1500 incremental Android pipeline evidence — 2026-10-01

This document records incremental qualification evidence for the canonical remediation TODO at `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`. It does **not** close RMD-1501 through RMD-1507; those remain governed solely by the canonical checklist and require their complete production-path acceptance evidence.

## Exact qualified head

Exact `master` SHA: `a47f0cb636e602ea6ad93669a1708a208215d9ca`.

The exact head passed the complete currently configured push matrix:

- CI run `36847286396` — success.
- Android smoke run `36847286368` — success.
- Android FGS timeout / API-35 runtime run `36847286377` — success.
- Supply chain run `36847286382` — success.
- CI evidence run `36847286343` — success.
- Deterministic E2E fixture run `36847286371` — success.

Android smoke uploaded bounded evidence artifact `android-smoke-evidence-36847286368` (artifact id `11154073312`).

## Android app-pipeline proof now in the smoke lane

`app/src/androidTest/java/com/ekkus/offlineytplayer/ui/Rmd1500AppPipelineFixtureTest.kt` drives the production Compose application surface from a shared supported URL through:

1. Share/Add entry into `OfflineYTPlayerApp`.
2. Source analysis through the app-owned source-analysis gateway boundary.
3. Resolved metadata and quality-option presentation.
4. Download Setup selection.
5. The production UI's download action crossing the download-control gateway boundary with the selected quality choice.

`.github/workflows/android-smoke.yml` now includes `Rmd1500AppPipelineFixtureTest` in the regular API-29 instrumentation smoke command. Exact-head Android smoke run `36847286368` passed with this test in the executed class list. This removes the previous qualification gap where the fixture test existed but was not part of the regular Android smoke lane.

## What this evidence does not prove

The test deliberately uses deterministic app-owned test gateways. Therefore it does not by itself satisfy the canonical RMD-1501 requirement to continue through the real Android scheduler/runtime, core worker, durable Library promotion, network disablement, process kill/cold start, and MediaSession-owned local playback in one end-to-end Android flow.

Likewise, it does not close:

- RMD-1502 split A/V cold-start offline playback;
- RMD-1503 subtitle persistence/selection/display after offline cold start;
- RMD-1504 complete ACTION_SEND → runtime download → Library/back-stack flow;
- RMD-1505 Android storage-failure/ENOSPC flow;
- RMD-1506 Android connectivity-loss/resume flow;
- RMD-1507 notification pause/resume/cancel durable-state flow.

The deterministic core fixture lane and API-35 runtime lane provide strong adjacent proof, but the canonical TODO explicitly forbids treating policy or disconnected stage tests as substitutes for the required RMD-1500 production-path E2E evidence.

## Next qualification target

The shortest safe next slice is to join the already-proven Android app pipeline to the real `AndroidDownloadExecutionScheduler`/runtime and durable Library state in deterministic instrumentation, then extend that fixture through cold-start MediaSession playback. Reuse the existing scheduler/runtime, core fixture, Library, and playback infrastructure rather than adding parallel test-only implementations.
