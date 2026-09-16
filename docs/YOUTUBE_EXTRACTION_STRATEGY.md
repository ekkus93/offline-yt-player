# YouTube Extraction Strategy

This document closes the OYP-701 engineering spike. It is an architecture decision, not a claim that public distribution of a YouTube downloader has been approved. OYP-706 remains an independent release gate.

## Requirements

The extractor must run on Android without a separately installed runtime, remain replaceable behind the portable `MediaSource` boundary, return normalized metadata/stream data rather than provider-specific objects, bound network/resource use, avoid persisting or logging signed media URLs, and be testable from recorded deterministic fixtures.

## Option A — pure Rust extraction

A Rust implementation can ship in the existing native core without Python, Node, a subprocess, or an additional Android service. That preserves the current ownership rule: Rust owns source/download logic and Kotlin owns Android lifecycle/presentation. It also gives one portable implementation for future desktop/Swift bindings.

The cost is maintenance risk. YouTube player/bootstrap formats and signature/throttling transformations can change independently of this application. Extraction therefore must be isolated behind a narrow internal extractor contract, use bounded HTTP requests, classify incompatible upstream responses as `SourceChanged`, and carry fixture/regression coverage so breakage is detected without depending on live YouTube in CI.

## Option B — embed or wrap an external extractor

A mature external extractor can react to upstream changes faster and usually supports more edge cases. On Android, however, subprocess-oriented tools commonly imply shipping an interpreter/runtime, native executable machinery, or a second language dependency. That increases APK size, ABI/package complexity, startup/resource cost, update mechanics, and supply-chain/license review surface. A separately downloaded executable is also a poor fit for a deterministic mobile release and creates additional integrity/update-policy obligations.

External extractors remain a fallback if measured fixture coverage demonstrates that the pure-Rust implementation cannot be maintained reliably. Any such adoption requires a separate dependency/license review and an explicit Android packaging/update design before merge.

## Initial decision

Use a **pure-Rust YouTube adapter with an internal replaceable extractor boundary** as the initial implementation. `MediaSource` remains the public provider abstraction. `youtube.rs` owns strict URL recognition/canonicalization; extraction code returns the neutral `ExtractedYouTubeMedia`/`ExtractedStream` representation; `youtube_extract.rs` sanitizes and normalizes that representation into generic `MediaInfo`, `MediaFormat`, and curated `QualityChoice` values.

The first production extractor should consume bounded HTTP responses and player data directly in Rust. It must not expose cookies, authorization material, player signatures, or signed stream URLs through diagnostics. Live-network tests are not a release gate: recorded fixtures are the deterministic regression contract, with optional live probes kept separate from normal CI.

## Updateability and failure semantics

Extractor compatibility ships with the application/core version. Upstream-format incompatibility maps to `SourceChanged`; connectivity and timeout failures retain their network categories. This distinction lets the UI tell the user whether retrying connectivity is sensible versus whether an application update may be required.

If upstream churn makes app-release-coupled extraction operationally unacceptable, revisit the external-extractor option rather than leaking provider parsing into Android UI or the generic download engine.

## Legal and distribution boundary

Technical feasibility does not determine permitted use or distribution. Before any public/app-store release, OYP-706 must document the supported-use statement, service-terms/app-store review state, and any unresolved distribution restrictions. Public distribution remains blocked until that gate is explicitly reviewed.
