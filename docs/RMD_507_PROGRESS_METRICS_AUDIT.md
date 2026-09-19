# RMD-507 Progress Metrics Audit

RMD-507 requires progress, speed, and ETA to come from real transfer state rather than UI-fabricated values.

## Implemented contract

- Durable snapshots continue to carry authoritative `bytes_downloaded` and optional `total_bytes`.
- `TransferMetricEstimator` calculates `bytes_per_second` from a bounded recent sample window.
- ETA is emitted only when total bytes are known and elapsed progress produces a positive speed.
- Unknown-length transfers preserve transferred bytes and optional speed, but do not fabricate percentage or ETA.
- Android row presentation maps only positive speed and meaningful ETA values into visible UI state.
- Retry row controls now route through the same app-owned download-control gateway family as Pause/Resume/Cancel.

## Behavioral evidence

- `core::events::tests::metrics_calculate_speed_and_eta_from_recent_samples`
- `core::events::tests::unknown_total_never_fabricates_eta`
- `app/src/test/java/com/ekkus/offlineytplayer/ui/DownloadRowPolicyTest.kt`
- `app/src/test/java/com/ekkus/offlineytplayer/ui/DownloadRowControlBindingTest.kt`
