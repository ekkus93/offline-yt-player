# RMD-1802 — Expected-size transfer resource-bound review (2026-10-14)

**Status:** Open production finding; no checkbox is qualified or closed by this document.

## Independent source review

At exact `master` `aff35244fd6d3101b36a5fd2183446db98053c73`, `core/src/download.rs::DownloadEngine::transfer_with_progress` checks `request.expected_bytes` only after the entire HTTP response body has been streamed, synchronized and written to the partial asset. The earlier streaming loop enforces `DownloadPolicy::max_asset_bytes` (64 GiB by default), but not the usually much smaller known `expected_bytes`. It also rejects a declared `Content-Length` only against the global limit. A provider or endpoint returning more bytes than the selected asset's expected size can therefore consume disk space and bandwidth before an inevitable `IntegrityFailure`. Unknown-length/chunked responses have the same issue.

This is a resource-bound hardening gap, not evidence that the existing end-of-transfer integrity comparison is absent. Do not infer exploitation or claim an observed runtime failure from source review alone.

## Required correction and acceptance

1. Once the response and append offset are known, reject a declared body length when `start + Content-Length` overflows or exceeds `request.expected_bytes`; reject an already-excessive resume offset. Preserve the global `max_asset_bytes` check and its existing error semantics.
2. During each streaming chunk, reject `total + count > expected_bytes` **before writing that chunk**. Preserve resumable partial integrity and distinguish size/integrity failure from local filesystem I/O failure.
3. Add deterministic Rust tests with a fixture response whose declared length exceeds the expected size; prove no partial/final asset is created. Add an unknown-length streaming fixture to prove early bounded abort without consuming the entire response, including resume behavior.
4. Qualify the implementation on its exact head with Rust/core CI and the required Android/fixture matrix. Reconcile this finding in the canonical remediation TODO only after production code, regression tests, and passing exact-head evidence exist.

**Qualification:** Pending. This report is an independently identified follow-up under RMD-1304/RMD-1802, not a completed remediation. External service-policy/legal release approval remains separate.
