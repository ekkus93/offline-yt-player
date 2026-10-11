# RMD-1802: Android scheduler rejection retry gap (2026-10-11)

Independent production-path review at master 7c9b13fd959677034c25b83e2fbe6786c69429bc found a follow-up to the durable-enqueue scheduler retry correction.

`SchedulingDownloadControlGateway.scheduleAfterMutation` reports `scheduler_rejected` after a successful durable Resume (Paused -> Queued) or Retry (Failed -> Queued). The next Resume is a no-op because the durable state is already Queued; the next Retry is rejected because the state is no longer Failed. Neither operation schedules work again. The new queued-work `FfiDownloadControlService.enqueue_inner` retry path only helps callers that invoke Enqueue again, not the Downloads screen's Resume/Retry actions. This leaves otherwise executable durable work stranded after a transient Android scheduler rejection.

Required correction: preserve idempotent retry of Android scheduling for an already-Queued executable job through the original Resume and Retry user paths, without mutating a newer active/paused/canceled attempt or fabricating success. Add JVM tests that reject the first schedule, accept a later one, and assert the same durable job/plan is scheduled exactly once when accepted. Add Rust/FFI tests for queued work without a plan and for concurrent state transitions. Qualify the exact implementation head with CI, Android smoke, and the required full matrix before RMD-1802/1803 closeout.

Separate known failure: master 7c9b13fd959677034c25b83e2fbe6786c69429bc fails CI run 38135165771 at `cargo fmt --all -- --check` in `core/src/ffi_download_control.rs`, due to a single multiline assertion formatting difference. No correction is claimed by this document.
