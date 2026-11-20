# UniFFI / native-boundary coverage inventory — 2026-11-20

**Repository:** `ekkus93/offline-yt-player` — coverage expansion COV-701 and COV-702.
**Baseline examined:** `b6b2a57fd164e30debada188874af23a7645ba5b`. This inventory describes Rust and Android adapter responsibilities, not a claim of arm64 runtime testing.

## Production API inventory

| Boundary | Rust source and exported responsibility | Android/UniFFI integration | Key existing tests |
| --- | --- | --- | --- |
| Core library and durable queue | `core/src/ffi.rs`: `FfiCoreService`, `FfiSourceIdentity`, `FfiMediaSummary`, `FfiDownloadQueueResult`, `FfiErrorKind`, `FfiDurableDownloadSnapshot`, `FfiCancellationToken`. | Generated Kotlin bindings from Rust `uniffi::setup_scaffolding!()`; app coregateway adapters. | `core_service_exposes_durable_queue_and_survives_reopen`, `all_core_error_categories_preserve_kind_and_retryability_across_ffi`, `cancellation_is_cooperative_sticky_and_typed`. |
| YouTube/source | `core/src/ffi_source.rs`: `FfiYouTubeSourceService`, `FfiSourceService`, resolved and choice-list records, cooperative cancellation. | `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppSourceAnalysisGateway.kt`: `GeneratedUniffiSourceAnalysisGateway.open()` performs `System.loadLibrary("offline_yt_core")`, typed constructor and unsupported-host preflight; analysis on Android IO dispatcher. | `GeneratedUniffiSourceGatewaySmokeTest` on x86_64 API 29 / 26 / 33 / 34 and host `ffi_source.rs` pre-/in-flight cancellation tests. |
| Durable controls | `core/src/ffi_download_control.rs`: `FfiDownloadControlService` enqueue/choice/options, pause, resume, cancel, retry, and state CAS. | App download-control adapter and foreground service; errors remain typed; invalid inputs fail closed. | `stale_control_cannot_overwrite_a_concurrent_pause_or_completion`, `stale_retry_cannot_reset_an_already_reclaimed_job`. |
| Scheduling, worker, startup | `core/src/ffi_download_enqueue_work.rs`, `core/src/ffi_download_worker.rs`, `core/src/ffi_startup_reconciliation.rs`. | Android platform scheduling, durable worker and startup reconciliation gateway. | Host worker stop/cancel, file-backed SQLite races, Android API 29 cold start and API 35 FGS timeout. |
| Library and playback | `core/src/ffi_library_{details,playback,remove,rename}.rs` and `core/src/ffi_playback_position.rs`. | App library and Media3 playback gateway path. | Rust library metadata, deleted/renamed item, saved position and Android offline playback tests. |

## Boundary-value and error inventory

- **14 public error kinds**: InvalidInput, UnsupportedSource, NetworkUnavailable, NetworkTimeout, HttpStatus, SourceChanged, NoCompatibleFormat, InsufficientStorage, IntegrityFailure, MissingAsset, CorruptAsset, Persistence, Canceled, Internal; both retryability values are tested by `all_core_error_categories_preserve_kind_and_retryability_across_ffi`.
- **Unsigned and nullable fields**: `FfiDurableDownloadSnapshot` uses `u64` sizes/retry time, `u32` attempt and optional totals/error; `FfiQualityChoice` optional size and video height; `FfiMediaSummary` optional duration, canonical URL and thumbnail. Max unsigned and `None` were already covered; `zero_unsigned_fields_preserve_presence_and_bounded_text_across_ffi_records` adds explicit zero-versus-null and bounded text regression (CI pending as of initial authoring).
- **Malformed requests and absent values**: `FfiSourceService` must return typed nonempty error, not an empty-success result; `malformed_source_requests_fail_with_typed_error_not_empty_success` adds unsupported scheme, host, empty input, and query-token variants. `SourceAnalysisUseCase` maps empty success to `EMPTY_SOURCE_ANALYSIS`, with unit tests for sanitized error classification.
- **Cancellation and cleanup**: pre-cancellation blocks provider dispatch, controlled in-flight cancellation returns typed nonretryable Canceled; Android `GeneratedUniffiSourceGatewaySmokeTest` repeats packaged native open/close and pre-canceled resolutions 12 times. No unsupported claim that cancellation interrupts a real external YouTube socket instantly.
- **ABI evidence**: CI packages `liboffline_yt_core.so` plus JNA for `arm64-v8a` and `x86_64`; x86_64 actually executes device tests. Cross-building arm64 is **not** runtime proof.

## Remaining gaps (explicit, not silently accepted)

COV-703 requires an actual native Android in-flight race beyond host-controlled source I/O; COV-705 requires arm64 runtime smoke; COV-706 requires Android UI diagnostic-redaction qualification at exact head. Physical Samsung Galaxy A54 verification remains a user/device acceptance gate. Rust line/function data cannot be treated as an exhaustive unexecuted-branch map. No coverage nonregression thresholds are enabled.
