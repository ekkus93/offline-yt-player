# RMD-400 core correctness reconciliation — 2026-09-20

This note reconciles the detailed RMD-400 acceptance criteria against production code on `master` after PR #245. It is evidence only; it does not replace or compress the detailed remediation TODO.

## RMD-401 — retryability authoritative

Implemented. Retry decisions preserve the authoritative `CoreError.retryable` value and regression coverage includes non-retryable HTTP/source failures plus transient retryable failures. See the download/retry policy implementation and its Rust tests. PR #245 independently reviewed this evidence; exact-head CI for `39ad72bcbe51e60eeb7c19fbfd9e3c4121996de0` passed in runs `35515774802` and `35516082761`.

## RMD-402 — authoritative retry policy

Implemented. Production download policy owns the bounded maximum-attempt policy, durable queue records preserve attempt/next-eligible retry state across process restart, backoff is bounded, and deterministic timing hooks are covered by Rust tests. PR #245 independently reviewed this evidence; exact-head CI passed in runs `35515774802` and `35516082761`.

## RMD-403 — remote-read versus local-filesystem I/O

Implemented. Remote response-body failures are classified independently from local persistence/storage failures, transient remote failures remain retryable where appropriate, and fixture tests cover truncated/disconnected responses while local write failures retain storage semantics. PR #245 independently reviewed this evidence; exact-head CI passed in runs `35515774802` and `35516082761`.

## RMD-404 — deletion removes owned assets

Implemented in `core/src/deletion.rs`. `delete_library_item_owned_assets` deletes every persisted owned asset before metadata, validates relative paths and canonical parents to prevent traversal/symlink escape, returns explicit persistence errors for deletion failures, and retains metadata after partial failure so retry reconciles interrupted deletion. Tests prove all assets are absent after success, partial failure is recoverable, and symlink escape is rejected.

## RMD-405 — stored hashes detect corruption

Implemented in `core/src/asset_validation.rs`. Cheap validation retains existence/size checks. Deep validation verifies a stored SHA-256, rejects malformed hashes, detects same-length corruption, and returns explicit `Missing`/`Corrupt` health states suitable for repair UI instead of treating the asset as playable. Tests cover same-length corruption and matching hashes.

## RMD-406 — safe network diagnostics

Implemented for production download/source diagnostics. Raw transport errors are converted to structured safe diagnostics, sensitive URL/query/token material is not surfaced through user-facing core/FFI errors, and secret-marker regression tests cover the redaction boundary. PR #245 independently reviewed the production download path; exact-head CI passed in runs `35515774802` and `35516082761`.

## RMD-407 — deterministic quality ranking

Implemented in `core/src/youtube_extract.rs`. `curate_quality_choices` sorts before equal-height deduplication. The rank orders by height, compatibility, stream role, bitrate, FPS, codec, container, and format ID. Behavioral tests deliberately use adversarial provider ordering and prove direct-play combined streams beat mux-required/split alternatives at equal height, compatible split beats mux-required, and ties are deterministic.

## RMD-408 — concurrency/resource policy

Implemented in `core/src/concurrency.rs` and the UniFFI policy mapping. `MAX_CONCURRENT_DOWNLOADS` is the portable-core hard ceiling; `DEFAULT_CONCURRENT_DOWNLOADS` is the product default; `bounded_download_concurrency` clamps platform/user requests into the core-safe range; the worker constructor applies that bound. Rust tests prove invalid limits are rejected and admission never exceeds the configured bound, while FFI mapping tests prove platform values cannot exceed the core ceiling.

## Qualification

The RMD-400 production implementation is covered by the regular exact-head Rust fmt/clippy/test lane plus Android/UniFFI packaging lanes. This reconciliation must itself pass exact-head CI before merge. Final engineering closeout remains governed by RMD-1800 and the external release gate remains separate.
