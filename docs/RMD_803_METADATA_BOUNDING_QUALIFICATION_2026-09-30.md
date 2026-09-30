# RMD-803 metadata bounding qualification — 2026-09-30

This slice ports the useful bounded-presentation work from the historical `ralph/oyp-1203-metadata-presentation` branch onto current `master` without reviving that stale branch.

`MetadataPresentationPolicy` now delegates title and quality normalization to the production `SourceMetadataPolicy`, bounds source identity to the channel/source budget, bounds detail labels and values, and keeps long details in a dedicated deterministic region rather than an unbounded primary-screen metadata wall. JVM tests cover malformed/blank fields and values beyond the title, quality, source, label, and description limits.

This advances the RMD-803 requirements to bound title/channel/description-like fields and test malformed/very-long metadata presentation. It does **not** close RMD-803: current production Downloads rows still use the durable job identifier as their displayed title. The remaining production fix is to expose `DownloadPlan.title` from durable `download_work_items` alongside queue state through the Rust/UniFFI read model and consume that bounded resolved title in `MainActivity.toDownloadsScreenState()`. The canonical checkbox must remain open until that production path is implemented and exact-head qualified.
