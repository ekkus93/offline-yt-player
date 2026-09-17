# OYP-502 pause/resume audit

This audit records the implemented OYP-502 continuation and safe-resume boundary after PR #99.

## Persist continuation data

The download core persists `ResumeRepresentation` sidecar metadata beside partial assets. The representation records the source URL, remote ETag or Last-Modified validator, and known total representation length. Sidecars survive process lifetime, are removed with orphan partials, and are cleared after successful promotion.

## Resume ranged downloads when supported

`DownloadEngine::transfer` detects an existing partial and issues `Range: bytes=<existing>-`. It derives the current representation identity from response headers and appends only when the response is HTTP 206, its `Content-Range` starts at the exact existing byte count, and `prepare_partial_reuse` proves the persisted and observed remote identities match.

## Safe fallback when resume is unsupported or unproven

If the server does not provide the required partial response semantics, or if the current representation cannot prove that the persisted bytes belong to the same remote object, the engine discards unsafe continuation state and performs a fresh request from byte zero. Responses without a usable remote validator are deliberately non-resumable rather than guessed safe.

## Verify partial data before reuse

Partial reuse is identity-gated before append. URL, validator, and known representation size participate in the comparison; corrupt resume metadata is an explicit integrity failure. The transfer path also validates the returned range start and verifies expected final size and optional SHA-256 before promotion.

## Cancellation/pause boundary

The transfer copy loop checks its cooperative `AtomicBool` cancellation token between bounded reads. With the default policy, cancellation retains the partial and its resume representation so a later transfer invocation can continue safely. The durable download-control state machine separately exposes pause/resume transitions; orchestration can therefore stop a running transfer cooperatively and resume it through the same representation-safe transfer primitive without introducing a second continuation format.

## Qualification

Deterministic local HTTP fixture tests cover full transfer, validator-backed range resume, restart of an unproven partial, size verification, storage preflight, and pre-canceled behavior. Resume-unit tests additionally qualify representation matching, mismatch discard, corrupt metadata rejection, and process-independent sidecar persistence.

These implementation and test boundaries satisfy all four explicit OYP-502 checklist requirements. Canonical TODO reconciliation should mark OYP-502 complete after this audit's exact-head CI passes and the change is merged.
