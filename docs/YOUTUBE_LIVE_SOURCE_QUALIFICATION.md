# Controlled YouTube live-source qualification

This path is an explicit, manual production-adapter smoke test. Normal CI remains deterministic and does not contact YouTube.

## Policy and legal prerequisites

Run the workflow only with an approved, public video that the operator is authorized to access for interoperability testing. Do not supply private, age-restricted, paywalled, authenticated, or otherwise access-controlled media. Do not add cookies, account credentials, signed media URLs, tokens, or other secrets. The workflow accepts only the public watch/share URL and uses the same unauthenticated production adapter shipped by the application.

The test is intended to validate source compatibility, not to bypass access controls or download media. It resolves metadata and available formats but does not execute a download plan.

## Running the smoke test

Use the `YouTube live smoke` workflow's manual dispatch and provide an approved public `youtube_url`. The workflow checks out the exact dispatched commit, installs the pinned Rust toolchain, and runs only the ignored `live_youtube_source_resolves_supported_url` test.

A successful run proves that the production `YouTubeSource` can recognize the URL, resolve canonical identity and metadata, and discover at least one format against the live provider at that point in time.

## Expected provider-change failure

The adapter is deliberately fail-closed. If YouTube changes the watch-page marker, player-response shape, stream fields, or bounded caption structures, the smoke test should fail rather than silently fabricate metadata. Parser/shape failures surface as `SourceChanged`; network failures remain separately classified. Treat a new `SourceChanged` failure as a compatibility regression: capture a bounded sanitized fixture, update the parser and deterministic fixture tests, then rerun normal CI before repeating the live smoke.

Do not weaken response-size, URL-validation, redirect, or metadata bounds merely to make the live smoke pass.
