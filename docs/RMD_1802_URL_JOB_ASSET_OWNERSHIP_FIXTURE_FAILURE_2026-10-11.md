# RMD-1802: URL job asset ownership fixture mismatch (2026-10-11)

Independent Android exact-head qualification on `426e1f563e968b12ca3e85686227422f253d9b99` exposed a regression in the RMD-1501 host-driven cold-start fixture following the new URL job asset-directory ownership validation in `core/src/ffi_download_worker.rs`.

## Reproduced evidence

Android cold start workflow run `38138462964`, job `114467139807`, failed the RMD-1501 seed instrumentation with `expected:<COMPLETED> but was:<QUEUED>` at `Rmd1501ProductionPipelineColdStartInstrumentedTest.kt:229`. CI, Supply chain, CI evidence, Deterministic E2E fixture, Android FGS timeout, and Android real network E2E passed on the same implementation SHA; Android smoke was still running at the time of investigation. This is a failed exact-head candidate, not qualified closeout.

## Root cause and corrective requirement

The fixture seeds URL job ID `https://www.youtube.com/watch?v=dQw4w9WgXcQ` but persists asset path `items/rmd-1501-cold-start/offline.wav`. The new worker FFI validation requires all URL job assets under `items/by-job-<sha256(job_id)>/`, which is a legitimate cross-job isolation requirement. The fixture's expected relative path should be `items/by-job-0424974c68530290458c8d58674e2637f65abc127057957d7b3acbd24c208f93/offline.wav` (SHA-256 of the exact URL job ID), consistently in seed, local-file assertions, and playback descriptor assertions. Keep the production ownership validation intact.

The RMD-1502 and RMD-1503 fixture job IDs are non-URL IDs and are intentionally outside the URL-specific ownership restriction. The existing Rust test `url_job_rejects_foreign_asset_directory` validates that URL jobs cannot write into another job's directory.

A direct `master` edit of the RMD-1501 Kotlin test was attempted through Ralph Bridge using both `commit_files` and `create_or_update_file`, but both write requests were rejected by upstream safety checks before acceptance. No source correction was committed by these attempts. Retry the minimal fixture correction when source writes are accepted, then run the full exact-head CI matrix. Keep RMD-1802/RMD-1803/final engineering closeout unchecked.
