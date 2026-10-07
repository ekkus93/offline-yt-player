# RMD-1500 incremental Android pipeline evidence — 2026-10-01

This document records incremental qualification evidence for the canonical remediation TODO at `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`. It does **not** close RMD-1501 through RMD-1507; those remain governed solely by the canonical checklist and require their complete production-path acceptance evidence.

## Latest exact qualified head

Exact `master` SHA: `2d93d5117b33f82469301422a7e4818cfb22a7f9`.

The exact head passed the complete currently configured push matrix:

- CI run `37662009254` — success.
- Android smoke run `37662009335` — success.
- Android FGS timeout / API-35 runtime run `37662008998` — success.
- Supply chain run `37662009155` — success.
- CI evidence run `37662009073` — success.
- Deterministic E2E fixture run `37662009047` — success.

Evidence artifacts observed on that exact SHA include:

- `android-smoke-evidence-37662009335`.
- `android-fgs-timeout-evidence-37662008998`.
- `supply-chain-evidence-37662009155`.
- `ci-evidence-manifest-37662009073`.
- `deterministic-e2e-fixture-evidence-37662009047`.

## Android app-pipeline proof in the smoke lane

`app/src/androidTest/java/com/ekkus/offlineytplayer/ui/Rmd1500AppPipelineFixtureTest.kt` drives the production Compose application surface from a shared supported URL through:

1. Share/Add entry into `OfflineYTPlayerApp`.
2. Source analysis through the app-owned source-analysis gateway boundary.
3. Resolved metadata and curated quality-option presentation.
4. Download Setup presentation.
5. Advanced Options selection for source-derived subtitle and audio choices.
6. Applying the edited options back into the Download Setup state.
7. The production UI's Download action crossing the download-control gateway boundary with `DownloadSelectionOptions` carrying the selected quality, subtitle, and audio IDs.

`.github/workflows/android-smoke.yml` includes `Rmd1500AppPipelineFixtureTest` in the regular API-29 instrumentation smoke command. Exact-head Android smoke run `37659134871` passed with the expanded Advanced Options test in the executed class list. This proves the deterministic Add/Share -> Analyze -> Download Setup -> Advanced Options -> scheduling-boundary slice is continuously covered by the Android smoke lane.

## Real scheduler/core-worker -> Library -> MediaSession join proof

Exact `master` SHA `2d93d5117b33f82469301422a7e4818cfb22a7f9` extends `app/src/androidTest/java/com/ekkus/offlineytplayer/coregateway/GeneratedUniffiCoreGatewaySmokeTest.kt` so the packaged-core Android fixture now joins several previously separate production-path stages:

1. Seed deterministic durable fixture work in the app-private `offline-yt-player.sqlite3` database.
2. Schedule the work through `AndroidDownloadExecutionScheduler`.
3. Let the real Android scheduler/runtime execute the core worker and complete the fixture transfer.
4. Verify the durable queue reaches `COMPLETED`.
5. Verify the completed Library item exists and is marked completed.
6. Read the completed playback asset through `GeneratedUniffiLibraryPlaybackGateway`.
7. Verify the playback asset is playable, resolves to the app-private local fixture file, and does not require a remote playback URI.
8. Route the completed local fixture into the canonical `PlaybackSessionService` by connecting a Media3 `MediaController`, setting the local `MediaItem`, preparing it, and verifying the controller/session current item matches the completed fixture.

This is still an incremental slice, not full RMD-1501 closure. It proves the real Android scheduler/core-worker completion can be joined to durable Library playback-asset discovery and the canonical MediaSession boundary. It does not yet perform process kill/cold start, explicit network disablement, or a full user-facing Add/Share -> real scheduler -> worker -> Library -> offline playback path in one continuous Android E2E.

## Earlier incremental evidence

Exact `master` SHA `a47f0cb636e602ea6ad93669a1708a208215d9ca` previously qualified the initial RMD-1500 app-pipeline fixture in the Android smoke lane. That version proved source analysis, resolved metadata/quality presentation, Download Setup selection, and the selected quality-choice ID crossing the UI-to-download-control boundary.

Exact `master` SHA `2ffbf1d640702de3609f8ee88a96ae2888232945` extended that same smoke-lane proof to source-derived subtitle and audio option IDs and passed the complete six-workflow matrix.

## What this evidence does not prove

The Compose app-pipeline test deliberately uses deterministic app-owned test gateways. The packaged-core scheduler/Library/MediaSession slice deliberately seeds durable fixture work rather than entering through the production Add/Share source-analysis UI. Together they narrow the RMD-1500 gap substantially, but they do not by themselves satisfy the canonical RMD-1501 requirement to prove the full production path with network disablement, kill/cold-start recovery, and MediaSession-owned local playback in one end-to-end Android flow.

Likewise, this evidence does not close:

- RMD-1502 split A/V cold-start offline playback;
- RMD-1503 subtitle persistence/selection/display after offline cold start;
- RMD-1504 complete ACTION_SEND -> runtime download -> Library/back-stack flow;
- RMD-1505 Android storage-failure/ENOSPC flow;
- RMD-1506 Android connectivity-loss/resume flow;
- RMD-1507 notification pause/resume/cancel durable-state flow.

The deterministic core fixture lane, Android smoke lane, and API-35 runtime lane provide strong adjacent proof, but the canonical TODO explicitly forbids treating policy or disconnected stage tests as substitutes for the required RMD-1500 production-path E2E evidence.

## Next qualification target

The shortest safe next slice is to turn the joined Android fixture into a cold-start/offline playback E2E by re-opening app state after completion, disabling or proving absence of network use for completed playback, and verifying the MediaSession-owned local item remains playable from durable Library state after restart. Reuse the existing scheduler/runtime, core fixture, Library, and playback infrastructure rather than adding parallel test-only implementations.
