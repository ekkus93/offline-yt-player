# RMD-400 core correctness reconciliation

This evidence note records implementation already present on `master` without changing unchecked detailed TODO items that have not been individually proven.

## RMD-401 — retryability authoritative

Implemented in `core/src/retry.rs`. `is_retryable` requires both the explicit `CoreError.retryable` flag and a retryable failure class. Regression tests prove a nonretryable HTTP 404 and nonretryable `SourceChanged` failure execute only once, while transient network and HTTP failures retry.

## RMD-402 — unified retry policy

Implemented in `core/src/retry.rs`. `plan_retry_after_failure` uses `DownloadPolicy.max_attempts` as the authoritative bound, persists attempt/error/next retry deadline into `DurableDownloadSnapshot`, uses injectable `RetryTiming`, and relies on bounded `retry_delay` jitter/backoff. Tests cover persisted retry deadlines, terminal max-attempt behavior, nonretryable deadline clearing, and deterministic timing.

## RMD-403 — network-read vs filesystem I/O

Implemented in `core/src/download.rs`. Remote response-body reads use `remote_body_read_error` rather than the local `io_error` mapper. Timeout, disconnect, truncation and related remote failures remain retryable network/integrity failures; local ENOSPC remains `InsufficientStorage`. Deterministic raw HTTP fixture tests cover mid-body disconnect, delayed body timeout, direct timeout mapping and ENOSPC separation.

## RMD-406 — redacted network diagnostics

Implemented for the download transfer path in `core/src/download.rs`: `map_reqwest_error` returns bounded safe messages and does not expose raw `reqwest::Error` text or request URLs. `malformed_response_does_not_reflect_signed_url_or_tokens` injects synthetic signed/token query markers and asserts none are reflected in the user-facing diagnostic.

## Remaining fail-closed items

RMD-404, RMD-405, RMD-407 and RMD-408 are not closed by this note. They require their own implementation/evidence audit before their detailed TODO items can be reconciled. RMD-406 also remains subject to final end-to-end log/FFI review under RMD-1302/RMD-1802.

Evidence baseline for this audit: `master` `8467681395863475489a786cc514e9499a4c2de5`; PR #244 exact-head push CI `35513369850` and PR CI `35515461727` passed before that merge. Post-merge master CI is tracked separately and must pass before this baseline is used for closeout evidence.
