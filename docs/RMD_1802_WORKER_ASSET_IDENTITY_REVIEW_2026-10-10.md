# RMD-1802 worker asset collision finding

Review of `core/src/worker.rs::DownloadWorker::execute_one` at master `c0ca17077873fe054cfaf29dbc6bc94275e51e5b` found that a plan can contain two assets with the same output `relative_path` or `asset_id`. The worker checks only whether a playable asset exists before executing each transfer. Duplicate output paths can overwrite an earlier downloaded asset, and duplicate asset IDs can create ambiguous staging records.

Proposed correction: before entering Downloading, reject blank or duplicate asset paths and IDs with a permanent `InvalidInput` error. Add a Rust fixture regression using a valid job ID and two conflicting assets. Verify the worker fails without making network requests, staging assets, or promoting a Library item. This is an open RMD-1802 review finding, not closeout evidence. The canonical TODO remains unchecked pending implementation and exact-head CI.

**Correction on `master`:** `DownloadWorker::execute_one` now rejects blank/duplicate asset IDs and normalized output-path collisions before transfer or staging. Rust worker regression tests cover these identities; file-backed FFI tests additionally prove durable nonretryable failure settlement. Candidate SHA `4eda2f410c0cadfdcefb286a80d3771505ad0a65`; exact-head CI/Android qualification and full independent review remain pending.
