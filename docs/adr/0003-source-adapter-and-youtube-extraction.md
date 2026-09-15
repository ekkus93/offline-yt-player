# ADR 0003: Replaceable source adapters and initial YouTube extraction strategy

Status: accepted for v1 engineering; public distribution remains gated on policy/legal review

## Context

Offline YT Player needs deterministic portable download logic without coupling its domain model, UI, persistence schema, or player to one provider's volatile extraction details. YouTube extraction is especially change-prone and must be replaceable independently of the rest of the application.

## Decision

All provider behavior remains behind the Rust `MediaSource` contract and `SourceRegistry`. Generic code consumes only normalized `MediaInfo`, format, quality-choice, subtitle/thumbnail, download-plan, and typed-error values. Provider-specific identifiers or extractor records must not cross into Android presentation APIs.

The v1 YouTube implementation will begin as a native Rust adapter with fixture-driven parsing and normalization. Network/extraction mechanics are isolated inside that adapter. We will not embed Python, Node.js, a JavaScript runtime, `yt-dlp`, or FFmpeg in the Android APK merely to accelerate the first implementation.

This is an engineering choice, not a claim that a pure-Rust extractor will remain sufficient indefinitely. If real compatibility evidence shows that maintaining extraction logic is materially less reliable than integrating a mature external extractor, the adapter may be replaced without changing the portable domain or Android UI contracts.

## Pure-Rust viability

Advantages:

- Small Android packaging surface and no secondary language runtime.
- Straightforward Rust error mapping, cancellation, redaction, and deterministic fixture tests.
- Easier ABI and supply-chain auditing.
- Keeps the application architecture consistent with the portable-core requirement.

Risks:

- Provider-side player/signature changes can break extraction abruptly.
- JavaScript-dependent transformations may require a maintained interpreter or equivalent implementation.
- Compatibility maintenance can become a significant ongoing burden.

The pure-Rust path therefore passes the spike only as an initial implementation strategy, not as an irreversible dependency decision.

## External extractor evaluation

A future external extractor integration is permitted only behind `MediaSource` and only after a written dependency review covers:

1. Android arm64-v8a/x86_64 packaging and runtime requirements.
2. Binary/runtime size and cold-start impact.
3. Updateability when provider extraction changes faster than app releases.
4. License obligations for the extractor and every bundled runtime/native dependency.
5. Secret handling, subprocess/runtime isolation, cancellation, and diagnostic redaction.
6. Deterministic CI fixtures that do not require live YouTube access.

`yt-dlp` is the reference mature-extractor candidate because of its broad compatibility and update cadence, but embedding it would also imply a Python/runtime packaging problem on Android. It is therefore not selected for the initial APK.

## Muxing boundary

Extraction and muxing are separate decisions. The source adapter may return separate compatible audio/video assets. FFmpeg or another muxer must not be added solely because an extractor happens to support it. OYP-803 owns the measured compatibility and licensing gate for any muxer dependency.

## Reliability and diagnostics

The YouTube adapter must distinguish at least:

- invalid/unsupported URL,
- ordinary network failure or timeout,
- HTTP/provider rejection,
- extractor incompatibility or provider/source change,
- no compatible downloadable format.

Remote titles and other metadata are untrusted input. Logs must never contain cookies, authorization headers, bearer material, signed media URLs, or unredacted query tokens. Regression fixtures should preserve representative extraction shapes without storing credentials or expiring signed URLs.

## Updateability

Provider extraction logic should be kept in a narrow module with fixture tests so it can be patched without touching persistence, playback, or UI code. v1 does not implement remotely downloaded executable extraction code. Any future dynamic-update mechanism would require a separate security and release-design review.

## Policy and legal release gate

Technical ability to resolve or download media does not determine whether a particular use is authorized. Before any public/app-store release, the project must document the then-current service terms, platform/store policy implications, copyright considerations, and a supported-use statement. Public distribution remains blocked until that review is explicitly recorded under OYP-706/OYP-2305.

The application must not implement credential theft, paywall/access-control bypass, DRM circumvention, or hidden collection of user authentication material. If authenticated source support is ever added, it requires a separate security design.

## Reconsideration triggers

Revisit this ADR when any of the following is demonstrated by tests or release engineering evidence:

- Pure-Rust extraction repeatedly breaks on supported public URLs in a way a mature extractor handles reliably.
- Required signature/player transformations cannot be maintained safely without a runtime.
- Android packaging for a mature extractor becomes small, auditable, and reliably updateable.
- A provider offers a stable supported API that satisfies the product's offline-use requirements.

Until then, the initial implementation remains a replaceable native Rust YouTube adapter with deterministic fixtures and no live-service dependency in CI.
