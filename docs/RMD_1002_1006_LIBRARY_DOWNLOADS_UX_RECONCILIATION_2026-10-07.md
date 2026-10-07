# RMD-1002 through RMD-1006 Library and Downloads UX Reconciliation — 2026-10-07

## Scope

This note gathers current-master evidence for the remaining RMD-1000 operational UX items after RMD-1001. It covers:

- RMD-1002 Library Play action.
- RMD-1003 Library Details action.
- RMD-1004 Library Rename action.
- RMD-1005 Library Remove action.
- RMD-1006 Operational Downloads screen.

It intentionally does not close RMD-907 MediaSession behavioral qualification, any RMD-1500 end-to-end flow, RMD-1800 final closeout, or the external YouTube/service-policy/legal release gate.

## RMD-1002 — Library Play action

The previously completed fail-closed path remains valid: `LibraryPlaybackRoute` rejects invalid, incomplete, corrupt, or remote-only rows and exposes a user-visible unavailable reason.

The unchecked canonical-playback requirement is now supported by the RMD-901/RMD-902 implementation and behavior evidence. The canonical playback stack is service-owned: `PlaybackSessionService` owns the production `ExoPlayer`, creates one `MediaSession`, handles audio focus/noisy behavior, and releases session/player lifecycle resources. Production `PortraitPlayerScreen` constructs no independent player; it connects through `SessionToken` and `MediaController.Builder`, binds the UI player view to that controller, and routes play/pause/seek/speed/track controls through the controller.

`ProductionComposeBehaviorTest.player_entry_exposes_transport_and_track_controls` drives a completed Library row with local video/audio asset paths through the production `OfflineYTPlayerApp` Library surface, presses `Play`, and verifies the player surface is reached with transport, speed, subtitle, audio, and back controls visible. The RMD-901 through RMD-906 canonical TODO evidence records exact-head qualification for the service-owned playback implementation on `f62aa7aa5d72d3dccd87b9d4e50f60ec62ce7754`.

## RMD-1003 — Library Details action

`GeneratedUniffiLibraryDetailsGateway` is the production details boundary. It calls generated `FfiLibraryDetailsService.libraryDetails`, maps persisted source identity, display title, duration, quality, completion state, playback position, total bytes, and per-asset metadata into `CoreLibraryDetails`, and refuses main-thread blocking calls via `checkNotMainThread()`.

`ProductionComposeBehaviorTest.library_details_action_reads_repository_detail_gateway_off_main_thread` presses the Library `Details` action, verifies the details gateway receives the item id, and verifies the displayed details include title, quality, duration, total size, and asset count. This satisfies the action wiring and visible asset/source/size/detail-state requirement for the Library Details surface.

## RMD-1004 — Library Rename action

`GeneratedUniffiLibraryMutationGateway.renameDisplayTitle` is the production rename boundary. It calls generated `FfiLibraryRenameService.renameDisplayTitle`, returns the bounded persisted display title only when the generated service reports success, and surfaces structured `library_rename` errors instead of silently mutating UI-only state.

The product decision is metadata-only rename: rename changes the Library display title through the persistence boundary rather than attempting to rename media files on disk. That preserves owned-asset paths and avoids filename/path churn.

`ProductionComposeBehaviorTest.library_rename_and_remove_actions_use_repository_mutations_after_confirmation` presses `Rename`, confirms `Save rename`, and verifies the mutation gateway receives the item id and display title. Core path safety for rename is already covered under RMD-1303, where metadata-only rename is listed as part of the safe-root/path validation surface.

## RMD-1005 — Library Remove action

`GeneratedUniffiLibraryMutationGateway.removeLibraryItem` is the production remove boundary. It calls generated `FfiLibraryRemoveService.removeLibraryItem(libraryRoot, itemId, confirmed)`, requires the explicit confirmation boolean, returns the durable removal outcome, and surfaces structured `library_remove` errors on failure.

`ProductionComposeBehaviorTest.library_rename_and_remove_actions_use_repository_mutations_after_confirmation` presses `Remove`, then `Confirm remove`, and verifies the mutation gateway receives the item id with `confirmed=true`. The durable owned-asset deletion lifecycle remains covered under RMD-404 and RMD-1303: owned media, thumbnail, subtitle, partial, and resume assets are deleted through safe-root/path-validated core deletion paths, traversal/symlink escape is rejected, delete failures are explicit, and interrupted deletion is recoverable.

## RMD-1006 — Operational Downloads screen

RMD-602 already records the production Downloads repository wiring: `MainActivity.bootstrapProductionUi()` opens the app-private `GeneratedUniffiCoreGateway`, reads the durable queue via `listDownloadQueue()`, and `AppStateRefresher` republishes queue state off the UI thread. `DownloadsScreen` filters real durable states, and row presentation maps actual transferred/total bytes, state, progress, speed/ETA, and sanitized errors rather than fabricated values.

RMD-500 records the real pause/resume/cancel/retry control path: UI and notification actions route to the same `AppDownloadControlGateway`; pause/cancel reach durable worker stop points; resume makes paused work eligible; retry is legal only for eligible failed states and reuses durable identity. RMD-507 records real progress/speed/ETA behavior and the invariant that unknown-length transfers do not fabricate numeric progress. RMD-508 records network-policy integration.

`ProductionComposeBehaviorTest.downloads_actions_invoke_the_real_control_boundary` verifies production Compose Downloads rows call the control gateway rather than a no-op callback. `DownloadScreenPolicy` and `DownloadRowPolicyTest` cover legal-state presentation and non-fabricated metrics. Together these satisfy RMD-1006 for durable queue rendering, real filters, actual progress/state/error/speed/ETA, action wiring, and illegal-action disabling.

## Exact-head evidence available before this note

- RMD-901 through RMD-906 playback implementation evidence: exact master `f62aa7aa5d72d3dccd87b9d4e50f60ec62ce7754` passed CI `37675821415`, Android smoke `37675821448`, Android FGS timeout `37675821387`, Supply chain `37675821421`, CI evidence `37675821384`, and Deterministic E2E fixture `37675821443`.
- RMD-1001 operational Library evidence: exact master `516cb4ae03516b0a085ffb8ed06ae1b727cc6aae` passed CI `37680961218`, Android smoke `37680961234`, Android FGS timeout `37680961176`, Supply chain `37680961237`, CI evidence `37680961299`, and Deterministic E2E fixture `37680961326`.
- RMD-602 Downloads repository evidence, RMD-500 control/orchestration evidence, RMD-507 progress evidence, RMD-508 connectivity evidence, RMD-1303 path-safety evidence, and RMD-1402 through RMD-1405 UI qualification evidence are already recorded in the canonical TODO.

## Checklist impact after this note qualifies

After the documentation head containing this note passes exact-head CI on `master`, the following canonical TODO subtasks are eligible for reconciliation:

- RMD-1002: Open canonical playback session for selected completed item.
- RMD-1003: Add real details screen/sheet.
- RMD-1003: Show local assets, source identity, duration, size, subtitle info, and integrity/recovery state as appropriate.
- RMD-1004: Implement bounded rename in persistence.
- RMD-1004: Decide whether rename changes display title only or filename; prefer metadata-only unless product requires file rename.
- RMD-1004: Add validation/tests.
- RMD-1005: Add destructive confirmation.
- RMD-1005: Invoke real delete/asset lifecycle path.
- RMD-1005: Update list only after durable outcome.
- RMD-1005: Surface partial failure/recovery state.
- RMD-1006: Render durable queue.
- RMD-1006: Apply real filters.
- RMD-1006: Show actual progress/state/error/speed/ETA.
- RMD-1006: Wire pause/resume/cancel/retry.
- RMD-1006: Disable actions illegal for current state.

This note does not complete RMD-907 system-controller/headset behavioral qualification, any RMD-1500 end-to-end item, RMD-1800 final closeout, or the external policy/legal release gate.
