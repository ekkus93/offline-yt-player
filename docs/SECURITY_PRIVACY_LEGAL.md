# Security, Privacy, and Legal Model

This document records the current Offline YT Player security/privacy contract for the remediation track. It describes engineering behavior and constraints; it does **not** grant source-service, copyright, distribution, or app-store approval.

## Untrusted input model

Treat all externally supplied data as untrusted, including Android Share text, pasted URLs, provider responses, HTTP headers/bodies, remote metadata, subtitle content, thumbnails, filenames/path-like metadata, and persisted state that can become stale after process death or an interrupted write.

Engineering invariants:

- Accept only bounded input forms required by the product. Shared/pasted source input must be a valid supported HTTP/HTTPS URL before provider handling.
- Apply explicit size/count bounds to provider responses and metadata before allocation, persistence, or UI rendering.
- Keep provider-specific parsing and payload types inside the provider adapter boundary; generic UI/domain models receive normalized data and structured diagnostics.
- Sanitize remote metadata before deriving local filenames or paths.
- Canonicalize and constrain application-owned filesystem operations to their configured roots. Remote metadata must never be able to escape those roots through traversal or absolute-path injection.
- Treat redirects, range responses, content lengths, partial-transfer state, and persisted retry state as data requiring validation rather than trusted control instructions.
- Do not treat a UI-visible capability flag or policy constant as proof that the production path is safe or operational; remediation acceptance requires production wiring and behavioral evidence.

## Diagnostics and redaction

Diagnostics should preserve enough information to distinguish actionable failure categories while minimizing sensitive material. Generic diagnostics may identify categories such as unsupported input/source, provider/extractor incompatibility, network failure, storage pressure, integrity failure, unavailable media, or missing local assets.

The following must not be exposed through normal logs, UI diagnostics, persisted diagnostic records, CI artifacts, or provider-neutral error models:

- authentication cookies or authorization headers;
- API tokens, credentials, or secrets;
- signed media URLs or query parameters whose disclosure grants access;
- sensitive request/response headers;
- raw provider payloads when a bounded normalized diagnostic is sufficient.

Provider adapters are responsible for converting provider-specific failures into structured diagnostics before they cross the generic boundary. Error handling and CI evidence should prefer redacted summaries over dumping complete requests or responses. Any newly discovered diagnostic path that can disclose credentials or signed request material is a remediation/security defect, not an acceptable debugging shortcut.

## Local storage and deletion

Application-owned persistent state includes the durable queue/library database and media/assets needed for offline operation. Temporary and partial transfer files are not completed Library items and must not be promoted until verification succeeds.

Storage invariants:

- Keep database/media roots application-owned and explicitly configured.
- Never derive an unrestricted delete target directly from remote/provider metadata.
- Deletion of a Library item is expected to remove its owned media and associated application-owned assets as defined by the deletion remediation state machine, not merely hide the database row.
- Surface deletion failures rather than silently reporting success while owned files remain.
- Interrupted deletion and orphan cleanup require reconciliation; cleanup must stay inside application-owned roots.
- Integrity validation may use existence/size checks for cheap paths, but stored hashes must be honored where the remediation contract requires deep or suspected-corruption validation.
- Missing/corrupt local assets are explicit states. They must not be fabricated as healthy merely because metadata remains in the database.

The exact production deletion, integrity, and reconciliation behavior remains governed by RMD-404, RMD-405, RMD-604, RMD-805, RMD-1200, and related acceptance gates until those checklist items are reconciled with exact-head evidence.

## Privacy model

Offline YT Player is designed so a completed local Library item can be played without uploading the library or contacting the source provider merely to start playback. Network access is used for explicit resolution/download work and provider interactions required by those operations.

The application should persist only data needed for durable queue/library/playback behavior and diagnostics. Provider-specific secrets are not part of the generic library model. The app must not claim stronger privacy properties than the implemented and qualified production paths demonstrate.

Android notification permission denial must not corrupt durable queue state or cause work to be silently reported complete. In-app durable state remains authoritative. Background-execution details and remaining recovery limits are documented in `docs/ANDROID_BACKGROUND_EXECUTION.md`.

## External source-service and legal gate

Engineering completion and external approval are separate gates.

The repository does not, by implementation, CI success, documentation, or engineering closeout, assert that downloading from any source service is permitted by that service's terms, copyright law, app-store policy, or another applicable policy. Public/app-store distribution remains blocked until the designated human authority separately reviews and approves the source-service terms/policy/legal position.

Until such approval is explicitly recorded outside the engineering checklist:

- keep the release gate unresolved;
- do not mark it complete because provider extraction works;
- do not interpret fixture/live-source tests as legal authorization;
- do not represent `LicenseRef-OYP-Pending` as a finalized distribution license;
- keep provider extraction replaceable behind the `MediaSource` abstraction.

## Related remediation ownership

This document reconciles the documentation requirement in RMD-1705 but does not close implementation tasks by assertion. Security/privacy behavior remains subject to the corresponding production-path tasks, especially RMD-300 provider isolation/bounds, RMD-400 correctness/integrity/deletion, RMD-1300 security/privacy hardening, RMD-1600 supply-chain qualification, and RMD-1800 final independent review and exact-head qualification.

The authoritative engineering status is `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`. Unchecked items there remain unresolved regardless of descriptive language in this document.
