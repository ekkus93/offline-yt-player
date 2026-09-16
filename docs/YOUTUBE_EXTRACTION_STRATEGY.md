# YouTube extraction strategy spike

This document records the OYP-701 engineering decision. It is an architecture decision, not a statement that public distribution or a particular use of YouTube content is permitted. OYP-706 remains the release-policy gate.

## Constraints

The extractor must remain behind the portable `MediaSource` boundary. Provider-specific request/response types, signatures, player-script details, and signed media URLs must not leak into generic domain, persistence, UI, or download APIs. Android packaging must remain reproducible and must not require downloading executable code at runtime.

## Option A: pure Rust extraction

A pure Rust implementation has the best packaging and portability properties: one native core, no Python interpreter, no subprocess, and no separately updated executable. It can share the existing HTTP/security/error infrastructure and can be fixture-tested deterministically.

The reliability cost is substantial. YouTube extraction is a moving protocol target. Player responses, signature/n-parameter transforms, client variants, consent/age/region behavior, and stream manifests can change independently of an app release. A hand-maintained extractor therefore needs explicit `SourceChanged` diagnostics, captured regression fixtures, bounded parsing, and an update path through ordinary signed app releases.

Decision: pure Rust is viable only as a deliberately narrow adapter with fixture coverage and fail-closed behavior. It must not pretend that unknown page/player changes are generic network failures.

## Option B: embed or wrap an external extractor

Mature extractors can provide broader compatibility and faster response to upstream changes. On desktop, a subprocess boundary can also isolate extractor churn.

For Android v1 this is a poor default. Embedding a Python-based extractor introduces interpreter/runtime packaging, native-extension and ABI concerns, a materially larger artifact, startup/runtime complexity, and another update/security surface. Shipping or downloading an extractor executable independently of the app also complicates store policy, provenance, reproducibility, and the rule against runtime executable updates. A native external library would still require license, ABI, maintenance, and supply-chain review before adoption.

Decision: do not bundle Python, `yt-dlp`, FFmpeg, or another extractor/muxer executable in Android v1 merely to obtain YouTube support. Desktop ports may revisit a subprocess adapter later because their deployment constraints differ.

## Initial implementation choice

Implement the initial YouTube adapter as a narrow pure-Rust `MediaSource` implementation. Keep recognition, metadata parsing, stream normalization, and player/signature handling inside that adapter. Reuse generic `DownloadPlan` and transfer machinery only after the adapter has converted provider data into portable domain types.

The adapter must:

1. recognize only explicitly supported canonical/share URL forms and reject other forms;
2. sanitize all remote metadata before it reaches filenames or presentation;
3. convert provider failures into typed `UnsupportedSource`, `Network*`, `SourceChanged`, or `NoCompatibleFormat` errors;
4. never log cookies, authorization data, signed media URLs, or player tokens;
5. use checked-in sanitized fixtures for regression tests and avoid live YouTube in CI;
6. fail closed when signature/player behavior is unknown rather than downloading an unverified stream;
7. receive updates through the normal reviewed/signed application release path.

If this implementation proves too brittle in measured testing, the fallback is not an unreviewed runtime downloader. Re-open this ADR and evaluate a separately licensed native extractor or, for non-Android platforms, a sandboxed subprocess adapter.

## Packaging and updateability

The chosen v1 path adds no interpreter or executable payload. Rust code is compiled into the existing core for the supported Android ABIs. Extractor changes therefore require a normal application update. This is less agile than an independently updated extractor but preserves reproducible builds, provenance, and a smaller attack surface.

## Reliability qualification

Before OYP-701 can support a release claim, OYP-702 through OYP-705 must provide URL fixtures, metadata/format fixtures, source-change diagnostics, and sanitized failure behavior. Live-service smoke tests may be useful during development but cannot be the deterministic CI oracle.

## Legal and policy boundary

Technical feasibility does not grant permission to download content. Service terms, copyright, user authorization, app-store rules, supported-use language, and distribution jurisdiction require explicit review under OYP-706. Public/app-store release remains blocked until that gate is reconciled. No extractor dependency may be added without its license and transitive obligations being added to the dependency/license audit.
