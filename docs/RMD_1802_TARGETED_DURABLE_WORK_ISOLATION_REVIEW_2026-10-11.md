# RMD-1802: isolate durable work reads by job (2026-10-11)

## Independent review finding

The FFI download worker and the Android scheduler-retry path previously called `DurableDownloadWorkStore::load_all()` to locate a single job. That eagerly deserialized every stored plan, so a malformed JSON record for an unrelated job could prevent a valid queued download from executing or being rescheduled. The unrelated record's corruption must remain observable when that specific job is read, but it must not cause a cross-job denial of service.

## Correction

`core/src/durable_work.rs::load_one` now selects one `job_id` through a parameterized SQLite query and deserializes only that plan. `core/src/ffi_download_worker.rs::execute_job_inner` and `core/src/ffi_download_control.rs::enqueue_inner` use this targeted lookup. The bulk `load_all` method remains available for explicit inventory reads.

## Behavioral qualification

The Rust regression `targeted_load_isolates_an_unrelated_corrupt_plan` verifies that a valid job is returned even while `load_all` fails on an unrelated malformed row, and that reading the corrupt row itself still returns a persistence error. The FFI worker's `foreign_url_asset_path_is_a_durable_nonretryable_failure` additionally injects an unrelated corrupt plan and verifies that the requested job still reaches its own durable failure settlement, without network transfer or Library promotion.

Implementation commit: `89fac1113a6b98ccbe2e9140d4e24c425c0dc895`. The FFI regression is added with this review document. Both changes require exact-head CI and Android qualification before any RMD-1802/1803/final closeout checkbox is marked complete. External YouTube/service-policy/legal approval remains a separate human gate.
