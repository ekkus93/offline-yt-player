# OYP-501 download transfer foundation audit

This audit records the implemented OYP-501 HTTP-transfer foundation on the exact repository state after PR #99.

## Bounded timeouts

`DownloadPolicy` owns explicit connect and request timeout values. `DownloadEngine::new` applies both values to the blocking `reqwest::Client`, so connection establishment and request/body transfer cannot wait without the configured bounds. Reqwest timeout failures map to the typed `NetworkTimeout` core error.

## Redirect handling

The client installs `reqwest::redirect::Policy::limited(8)`. Redirect following is therefore bounded rather than unlimited or implicit.

## Content length and range handling

`DownloadEngine::transfer` validates declared response body length against the configured maximum asset size, verifies the received body length when `Content-Length` is present, and validates caller-provided expected total bytes before promotion. Resume requests use `Range: bytes=<existing>-`; append is accepted only for HTTP 206 with the expected `Content-Range` start and a matching persisted remote representation identity. Unsafe or unprovable partial state is discarded and restarted from byte zero.

## Safe temporary paths

Transfers write to a deterministic hidden sibling `.partial` file under the validated library-relative destination. Parent directories are created under the configured library root, completed content is promoted with `rename`, and orphan-partial cleanup is bounded to the configured root. Resume sidecars are removed after successful promotion and alongside orphan cleanup.

## Filename and path sanitization

Every transfer calls `validate_relative_library_path` before deriving filesystem paths. Remote URLs independently pass `validate_http_url`. The transfer engine therefore does not accept absolute, traversal, or otherwise invalid library-relative paths as download destinations; filename/path policy is centralized in the security boundary rather than inferred from remote metadata.

## Qualification

The deterministic local HTTP fixture in `core/src/download.rs` exercises full transfer and verified byte count, validator-backed range resume, unsafe-partial restart, storage preflight, and pre-canceled behavior without live external services. PR #99 additionally qualified representation-safe resume integration on exact-head CI before merge.

These implementation and test boundaries satisfy all five OYP-501 checklist requirements. Canonical TODO reconciliation should mark OYP-501 complete after this audit's exact-head CI passes and the change is merged.
