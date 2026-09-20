# Security, Privacy, and Legal Model

This document records the current Offline YT Player security, privacy, and external-approval model for the remediation track. It is not a release approval and it does not supersede unchecked acceptance gates in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Status and scope

Offline YT Player is still under engineering remediation. Passing deterministic CI, building an APK, or documenting a workflow does not grant public-release approval. External source-service terms, platform-policy, copyright, and legal review remain separate human approval gates.

Engineering work may make the application technically capable of resolving and downloading supported media. That capability must remain conditioned by the unresolved external policy/legal gate. Do not describe the project as approved for public distribution until a separately authorized human decision records that approval.

## Input model

All inbound provider and share input is untrusted.

Accepted entry points are intentionally narrow:

- Android `ACTION_SEND` with bounded `text/plain` content.
- Add-flow URL entry for supported HTTP/HTTPS media URLs.
- Provider responses consumed only by isolated source adapters.
- Local application-owned database/media files.

Current input safeguards include:

- Shared text is size bounded before parsing.
- Non-web schemes are rejected for media input.
- Android-side share validation is fail-closed to supported YouTube video URL forms before reaching setup UI.
- The Rust core validates HTTP/HTTPS URLs with hosts and rejects embedded URL credentials.
- Provider-specific parser records stay inside the provider adapter and are converted to generic domain records before reaching UI/library layers.
- Remote titles, identifiers, and generated relative paths are sanitized and bounded before storage use.
- Library paths are validated as relative, non-traversing, and non-empty before file operations.

Unsupported URLs, malformed input, provider structure changes, unavailable/private media, and network failures are distinct categories. They must not be collapsed into an infinite generic retry path.

## Diagnostic and redaction guarantees

Diagnostic output must be actionable without exposing sensitive provider or user material.

Do not include the following in generic UI errors, FFI error summaries, logs intended for support, or durable diagnostic records:

- Authorization, cookie, set-cookie, API-key, or equivalent header values.
- Signed media URLs, query-string tokens, signatures, expiry parameters, or equivalent request material.
- Provider response payloads that can include request signatures or personalized metadata.
- Local filesystem details beyond what is necessary to identify an application-owned asset state.

Current redaction behavior includes:

- `redact_sensitive` removes known sensitive header values and redacts URL query strings.
- Network provider request failures map to structured user-facing diagnostics instead of raw `reqwest::Error` text.
- FFI/domain summaries do not carry provider stream URLs across the normalized metadata boundary.
- Synthetic tests include signed URL/secret markers to prove they do not appear in normalized/FFI debug output.

Diagnostics should prefer categories such as unsupported source, source changed, network unavailable, storage pressure, integrity failure, missing local asset, permission denied, or nonretryable provider failure.

## Storage and deletion behavior

The application owns its private database, incomplete download staging area, promoted media assets, thumbnails, subtitles, and cached metadata. Users should not manually edit those files while the app is running.

Storage behavior must preserve these invariants:

- Incomplete or temporary assets are not completed Library items.
- Asset promotion happens only after verification succeeds.
- Deletion is restricted to application-owned relative paths rooted in the library/media store.
- Traversal, absolute paths, prefixes, and empty paths are rejected.
- File deletion failures are surfaced rather than hidden.
- Interrupted deletion/reconciliation work remains a durable recovery concern.
- Missing or corrupt local assets must not be silently reported as healthy completed items.

When hashes are available, explicit/deep validation should use them to detect same-length corruption. Cheap existence and size checks may still be useful as preflight checks, but they are not a complete integrity proof when stored hashes exist.

## Privacy expectations

Offline YT Player is intended to keep completed media local to the device. Completed local playback must not require uploading the user library or recontacting the provider merely to play a valid local item.

Network access is expected for explicit resolution/download operations and provider interactions required by those operations. Network diagnostics must avoid leaking provider request material. Application telemetry, analytics, account synchronization, or cloud backup behavior are not established by this remediation document and must not be implied without explicit implementation and review.

## Notification and platform permissions

Android notification permission denial must not mutate durable queue state or silently report queued work as complete. In-app durable queue/download state remains authoritative when notifications are unavailable or denied.

Background execution behavior varies by API level and is documented in `docs/ANDROID_BACKGROUND_EXECUTION.md`. That platform model is separate from legal approval to download from any particular source.

## External policy/legal gate

The YouTube/source-service policy and legal approval gate is external and unresolved unless a separate human authority records approval. Engineering agents must not mark that approval complete, infer approval from tests, or present technical capability as permission to distribute.

Before public release, a human review must confirm at minimum:

- Which sources/providers are allowed for the intended distribution channel.
- Which use cases are allowed or disallowed.
- Whether the provider adapter behavior complies with source-service terms and applicable law.
- Whether app-store policy requirements are satisfied.
- Whether the pending project license and shipped dependency notices are suitable for distribution.

Until that review is complete, documentation should use development/remediation wording, not release-approved wording.

## Related remediation tasks

This document supports RMD-1705 only. It does not close the following tasks by itself:

- RMD-1300 security hardening implementation and tests.
- RMD-1602 dependency, advisory, and license tooling.
- RMD-1802 independent post-remediation code review.
- RMD-1803 exact-head full qualification.
- Any external source-service/legal approval gate.
