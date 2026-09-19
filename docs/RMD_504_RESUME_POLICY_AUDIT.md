# RMD-504 Resume Policy Audit

This audit records the production contracts added for the RMD-504 resume path.

- Core resume control transitions paused work back to the durable queued state; the worker owns the later claim and `Resolving`/`Downloading` transitions.
- The existing transfer engine revalidates retained partial data through `ResumeRepresentation` sidecar metadata before any range append. Changed URL, changed validator, changed known length, corrupt metadata, missing validator, or unexpected range response prevents unsafe append.
- Android resume now uses `DownloadResumeCoordinator`, which first evaluates the current `DownloadNetworkPreference` against observed `DownloadConnectivity`.
- When the current network violates the stored/current preference, resume waits without mutating the durable queue or scheduling work.
- When the policy allows work, resume calls the shared generated-UniFFI download-control gateway and then schedules the same durable queue item through `DownloadExecutionScheduler`.
- Unit coverage: `DownloadResumeCoordinatorTest` verifies waiting behavior, shared control/scheduler routing, scheduler rejection reporting, and the policy invariants.
