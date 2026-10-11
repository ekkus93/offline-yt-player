# RMD-1802 — cross-job asset-path identity review (2026-10-10)

Independent review found that production source plans can use `items/<media_id>/...` output paths while the durable queue uses the input URL as job ID. Two different URLs resolving to the same media ID can therefore write the same local path. The worker's existing duplicate-path guard only checks paths within a single plan.

The new production work builder in `core/src/ffi_download_enqueue_work.rs` scopes each generated asset path under a deterministic SHA-256 job-ID namespace before persistence, preserving the provider's relative suffix. `distinct_job_urls_with_same_media_id_have_isolated_asset_paths` is the Rust regression. The implementation is on master; exact-head CI qualification is pending.

This protects newly generated executable plans. Older persisted work plans may still use unscoped paths; their migration or fail-closed collision handling requires separate review and evidence. Do not close RMD-1802 or final engineering qualification based solely on this fix.
