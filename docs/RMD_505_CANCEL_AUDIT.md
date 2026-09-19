# RMD-505 Cancel Audit

The durable cancel path is shared across the production control surfaces and worker:

- `FfiDownloadControlService.cancel` persists the terminal `Canceled` state in the durable queue.
- `DownloadForegroundControlDispatcher` routes the notification Cancel action through the same `AppDownloadControlGateway.cancel` operation used by app control clients; its Android unit test covers Pause/Resume/Cancel gateway routing.
- `DownloadWorker::transfer_with_durable_stop` polls durable state during active transfers. A durable `Canceled` state trips the same bounded cooperative transfer stop used for Pause, but the worker distinguishes terminal Cancel from Pause and reports/persists Cancel.
- `DownloadPolicy.retain_partial_on_cancel` is the authoritative partial policy. The default policy retains the resumable partial and its representation metadata; the final asset is never promoted for a canceled transfer. A policy with retention disabled removes the partial and resume sidecar in `DownloadEngine`.
- `worker_cancel_tests::durable_cancel_stops_active_work_and_persists_terminal_state` exercises an active deterministic HTTP transfer, issues Cancel through the real FFI control service, proves the worker stops, proves durable terminal `Canceled`, proves no final asset is promoted, and verifies the default retained-partial policy.
