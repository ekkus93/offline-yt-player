# Offline YT Player — v1 Architecture Decisions

These decisions define the v1 engineering boundaries. They are intentionally conservative: Android-specific behavior stays in the app, portable domain behavior stays in Rust, external providers remain replaceable, and release-sensitive native dependencies are added only when demonstrated necessary.

## ADR-001 — Rust/Kotlin boundary

**Status:** Accepted for v1.

Rust owns portable domain models, source selection, normalized media choices, download state/retry/integrity policy, persistence semantics, library completion/recovery, and deterministic fixture qualification. Kotlin/Android owns Compose presentation, Activity/service lifecycle, Android intents and notifications, Media3/MediaSession, permissions, and platform storage/network integration.

The boundary is coarse grained. Android requests domain operations and consumes durable snapshots/coarse events; it must not reproduce provider or download state-machine logic. Rust must not persist Android `Context`, `Uri`, `Intent`, `Service`, Compose, or Media3 types. This keeps the core suitable for future non-Android bindings and prevents UI lifecycle concerns from contaminating portable state.

## ADR-002 — UniFFI for the v1 FFI

**Status:** Accepted, with manual JNI as a bounded fallback.

UniFFI is the default binding mechanism. The repository pins a binding-generation tool and CI generates representative Kotlin bindings and cross-builds the Rust core for `aarch64-linux-android`. The public FFI should expose coarse operations and Kotlin-safe domain/error types rather than chatty callbacks or provider-specific structures.

Manual JNI is a fallback only if a required API cannot be represented safely by the pinned UniFFI version, generated bindings cannot satisfy supported Android ABI/build requirements, or measured FFI behavior creates a correctness/performance problem that cannot be fixed without abandoning the generator. Any fallback must retain the same portable API semantics and receive equivalent CI coverage; convenience alone is not sufficient reason to switch.

Blocking Rust work must execute off the Android main thread. Cancellation is explicit and durable state is authoritative after process restart.

## ADR-003 — Replaceable source adapters

**Status:** Accepted.

All provider extraction is behind the generic `MediaSource` contract and source registry. Adapters recognize URLs, resolve sanitized metadata, normalize raw formats, produce curated choices/download plans, and optionally expose thumbnail/subtitle assets. Provider-specific response types, signed URLs, cookies, tokens, and extractor internals must not leak into generic UI/library APIs.

A direct/local fixture adapter is the deterministic CI reference implementation. Live YouTube behavior is not a CI dependency. YouTube extraction may evolve or be replaced without changing the generic library/download/playback contract. Diagnostics distinguish unsupported input, network failure, and extractor/source incompatibility without logging sensitive request material.

Public distribution remains gated on the separate service-policy/legal review.

## ADR-004 — Local media strategy and muxing gate

**Status:** Accepted for v1.

Prefer directly playable Android Media3-compatible assets and persist the relationship between completed local video/audio/subtitle assets. Completed playback is local-only: network URIs are rejected at the playback boundary.

Separate adaptive video/audio assets may remain separate when Media3 can coordinate them locally. A muxer such as FFmpeg is not a baseline dependency. Add a muxer only after deterministic compatibility tests identify a supported v1 case that cannot be played correctly from the persisted local asset plan. Before adding one, record binary-size, ABI, licensing, maintenance, and supply-chain impact and update this ADR.

This avoids carrying a large native dependency for hypothetical compatibility needs.

## ADR-005 — Portrait-only Android UI

**Status:** Accepted for v1.

Android is portrait only. The Activity is orientation-locked and v1 does not maintain landscape layouts, rotate actions, or fullscreen-landscape affordances. Primary actions occupy fixed/bounded regions and must remain discoverable without horizontal scrolling or scrolling merely to reach controls. Naturally unbounded collections may scroll inside bounded content regions.

If controls cannot fit a supported portrait profile, split them into a focused subpage/tab instead of placing primary actions below the fold. Touch targets, text scaling, system insets, and accessibility semantics remain requirements; portrait-only is not an exemption from compact-screen or large-font qualification.

## Consequences and review

These ADRs favor deterministic offline behavior and portability over provider coupling and UI/platform leakage. Changes that cross these boundaries require an explicit architecture update in the same qualified change. The implementation TODO remains the acceptance checklist; an ADR records the decision but does not substitute for automated qualification.
