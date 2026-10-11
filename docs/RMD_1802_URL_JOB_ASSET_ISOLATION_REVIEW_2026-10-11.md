# RMD-1802 URL-job asset-directory isolation (2026-10-11)

Independent review found that the worker accepted persisted URL-job asset paths inside the app-private Library root without requiring ownership of the job's SHA-256-derived directory. The production builder in `core/src/ffi_download_enqueue_work.rs` uses `items/by-job-<SHA-256(job_id)>/`, but a malformed plan could target another job's directory.

Commit `00bcb8868f16c64bfb2eb6cee8cd5343b58bc2a0` adds a worker preflight guard and Rust tests `url_job_rejects_cross_job_asset_path_before_transfer_or_staging` and `url_job_accepts_its_own_hashed_asset_directory`. CI run `38137858984` failed at `cargo fmt --all -- --check` on the new test formatting. The attempted formatting correction has not been accepted by the repository write tool; this issue remains **open and unqualified**.

Do not check RMD-1802, RMD-1803, global completion, or engineering closeout until formatting, exact-head Rust/Android qualification, and independent review are complete. The external YouTube/service-policy/legal approval remains separate.
