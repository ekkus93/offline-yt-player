# RMD-1802 — Worker work-item identity review (2026-10-10)

Independent Rust production-path review found that `DownloadWorker::claim_eligible` built its work-item lookup with `collect::<HashMap<_, _>>()`, silently selecting the last supplied executable plan when multiple items shared a durable job ID. The public worker boundary must reject duplicate or blank job IDs before any durable claim, rather than potentially downloading a different plan under the same queue identity.

The remediation validates all incoming work IDs before claiming queued work. Regression tests in `core/src/worker.rs` assert that duplicate and blank IDs produce non-retryable `InvalidInput`, leave unrelated/target durable queue entries in `Queued` with attempt zero, and do not stage or promote assets. These tests exercise the real worker and in-memory SQLite-backed `LibraryStore`, not an assertion-only capability flag.

**Qualification status:** implementation committed to `master`; exact-head CI, Android qualification, and the remaining independent review are required before RMD-1802/RMD-1803 or global closeout checkboxes can be marked complete. The durable work table already has a primary-key constraint, but the worker's independently callable input boundary must fail closed too.
