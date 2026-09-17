# OYP-504 integrity and completion audit

This audit records the implemented download-integrity and completion boundary.

## Expected-size validation

`TransferRequest.expected_bytes` is checked against the configured maximum before networking and, when present, must exactly match the final transferred byte count before promotion. Independently, declared HTTP `Content-Length` is bounded and the bytes actually read for that response must match it. Ranged responses additionally validate their expected `Content-Range` start before append.

## Optional checksum hook

`TransferRequest.expected_sha256` is optional. The engine computes SHA-256 over the complete partial asset after transfer and before promotion; when an expected digest is supplied, a mismatch returns `IntegrityFailure`. The computed digest is returned in `TransferResult` for downstream persistence/verification.

## Incomplete content is never promoted as completed

All network bytes are written to the hidden sibling `.partial` path. Size and optional checksum verification happen before `fs::rename` promotes the asset to its final library-relative path. Any verification failure therefore leaves the final path unpromoted. The persistence layer's atomic library-completion boundary separately keeps incomplete assets/jobs distinct from completed library items, as qualified by `docs/PERSISTENCE_LIBRARY_AUDIT.md`.

## Qualification

The deterministic transfer fixture verifies exact byte count and final content; resume tests verify complete content after append/restart. Existing persistence qualification covers atomic promotion and interruption recovery. Integrity-failure paths are typed and cannot reach the final rename before their checks succeed.

These implementation and test boundaries satisfy all three explicit OYP-504 checklist requirements. Canonical TODO reconciliation should mark OYP-504 complete after this audit's exact-head CI passes and the change is merged.
