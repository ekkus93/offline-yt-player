# ADR 0001: Rust core and Kotlin/Android boundary

Status: accepted for v1

## Decision

Portable product logic lives in the Rust `core` crate. Android/Kotlin owns platform presentation and lifecycle integration.

Rust owns source recognition/resolution, normalized media and quality models, download planning and transfer policy, durable download state, persistence schema/repository behavior, integrity checks, path/input hardening, and provider-independent error categories. These APIs must not expose Android classes, `Uri`, `Context`, Media3 types, Compose state, or Android lifecycle concepts.

Kotlin owns Compose UI/navigation, Activity/service lifecycle, Android permissions and intents, foreground-service/notification integration, connectivity policy observation, Media3 player/session construction, platform storage selection, and dispatch of blocking core calls away from the main thread.

The boundary is coarse-grained. Kotlin requests operations and consumes immutable summaries/events rather than driving individual HTTP chunks or SQLite statements. Provider-specific extractor types never cross the boundary.

## Rationale

This split keeps download/source/library behavior testable on a host CI runner and preserves a path to future Swift/desktop bindings. It also prevents Android lifecycle concerns from contaminating durable core state while allowing Android-native Media3 and Compose behavior to remain idiomatic.

## Consequences

- Filesystem locations cross the boundary as portable relative paths or explicitly supplied platform roots, never Android-only URIs in portable persisted records.
- Long-running operations use durable job identifiers and explicit pause/resume/cancel semantics.
- Progress is coalesced before crossing FFI.
- Android must use background dispatchers for blocking FFI calls.
- Any future platform-specific implementation in Rust must sit behind a portable trait/adapter rather than enter domain models.
