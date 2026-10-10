# RMD-1802 worker asset collision finding

Review of `core/src/worker.rs::DownloadWorker::execute_one` at master `c0ca17077873fe054cfaf29dbc6bc94275e51e5b` found that a plan can contain two assets with the same output `relative_path` or `asset_id`. The worker checks only whether a playable asset exists before executing each transfer. Duplicate output paths can overwrite an earlier downloaded asset, and duplicate asset IDs can create ambiguous staging records.

Proposed correction: before entering Downloading, reject blank or duplicate asset paths and IDs with a permanent `InvalidInput` error. Add a Rust fixture regression using a valid job ID and two conflicting assets. Verify the worker fails without making network requests, staging assets, or promoting a Library item. This is an open RMD-1802 review finding, not closeout evidence. The canonical TODO remains unchecked pending implementation and exact-head CI.
