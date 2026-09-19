# RMD-503 worker pause policy

Pause is a durable queue-state request, not a service-local boolean.

- `FfiDownloadControlService.pause` persists `Paused` in the durable queue before returning success.
- `worker_pause::propagate_durable_stop` maps durable `Paused`/`Canceled` state to the transfer engine's cooperative stop flag.
- The durable-state polling budget is 25 ms. `DownloadEngine` additionally checks its stop flag before each 64 KiB response-body read, so pause observation is bounded by the poll interval plus the currently blocked network read/request timeout.
- A paused snapshot remains `Paused`; it is not converted to terminal cancellation by the control gateway.
- Partial files use the existing validated resume representation (`ETag`/`Last-Modified` metadata when available). `DownloadEngine` retains or removes an interrupted partial according to `DownloadPolicy.retain_partial_on_cancel`; retained bytes are reused only after representation/range revalidation, otherwise the engine restarts from byte zero.
- Unit tests cover durable Pause and Cancel propagation and prove active work does not spuriously raise the stop flag.

The worker execution-loop integration consumes this bridge around each in-flight transfer; resume remains separately qualified under RMD-504.
