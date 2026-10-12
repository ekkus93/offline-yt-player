# RMD-1802 — Resume sidecar size-bound review (2026-10-12)

## Finding

The production `load_resume_representation` path in `core/src/resume.rs` used `fs::read` on a persisted `.resume.json` file with no upper bound. A corrupt or imported sidecar could consume unbounded memory before JSON decoding. A metadata-length precheck alone would leave a file-growth race.

## Correction

The loader now reads at most 65,537 bytes using `Read::take` on an opened file and fails closed with a nonretryable `IntegrityFailure` when the 64 KiB bound is exceeded. The writer rejects representations exceeding the same bound before touching any sidecar/temp path. The loader does not discard the partial bytes on malformed/oversized metadata; the caller receives an explicit integrity failure for repair.

## Deterministic regression

- `core/src/resume.rs::tests::oversized_sidecar_fails_closed_without_consuming_partial` verifies oversized on-disk input is rejected, remains nonretryable, and does not delete a partial asset.
- `core/src/resume.rs::tests::oversized_resume_representation_is_not_written` verifies the writer does not create a sidecar for oversized serialized metadata.
- Existing `metadata_round_trips_and_clears`, symlink and corruption tests remain relevant.

## Qualification and closeout

This document records an implementation-level independent review finding, **not** a passing CI claim. Verify exact-head Rust tests and the complete required eight-workflow matrix on the resulting `master` SHA. Keep RMD-1802 independent-review, RMD-1803 frozen-candidate qualification, RMD-1804 post-merge, RMD-1805 engineering closeout, and external service-policy/legal approval separate and unchecked until their respective evidence exists.
