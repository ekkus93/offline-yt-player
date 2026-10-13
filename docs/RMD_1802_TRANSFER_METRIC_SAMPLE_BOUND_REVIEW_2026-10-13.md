# RMD-1802 — Bound high-rate transfer metric samples (2026-10-13)

## Independent review finding

`core/src/events.rs::TransferMetricEstimator::observe` appended a new `ProgressSample` for every worker transfer-chunk callback, but `prune` only evicted samples older than the configured millisecond window (and retained at least two). For a high-frequency stream where many callbacks occur in one clock tick or within a 5-second window, the `VecDeque` could grow in proportion to untrusted transfer chunk count, violating the bounded-sample resource promise. A byte-size cap on the overall download is not a fixed in-memory sample bound.

## Production correction and deterministic proof

- Retain elapsed-time pruning to calculate meaningful recent transfer speed.
- Independently cap the deque at `MAX_TRANSFER_METRIC_SAMPLES = 256`, discarding oldest observations while preserving the most recent transfer progress.
- `core/src/events.rs::tests::high_rate_chunk_callbacks_cannot_grow_metric_history_without_bound` sends 10,000 same-timestamp samples, asserts bounded deque length on each callback, then verifies speed remains computable after time advances and that absent total bytes still produce no ETA.

## Qualification policy

The implementation and new Rust regression must pass a new exact-head main CI (fmt, Clippy, Rust tests) and the required Android/FGS/fixture/cold-start/network and supply-chain matrix. This does not close RMD-1802's wider independent review, RMD-1803 freeze, RMD-1805 or human external service/legal approval.
