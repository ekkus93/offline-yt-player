# YouTube live-source qualification

The production YouTube adapter has a deliberately manual live-service smoke path. Hosted pull-request and push CI remain deterministic and do not contact YouTube.

## Preconditions

Use `.github/workflows/youtube-live-smoke.yml` only with a video that the operator is authorized to access for this qualification. The workflow accepts only a bare 11-character YouTube video ID; it does not accept URLs, signed media URLs, cookies, tokens, credentials, or query strings. No secret is required by the workflow.

Engineering qualification does not approve public/app-store release. `docs/YOUTUBE_POLICY_RELEASE_GATE.md` remains the separate human policy/legal gate and must stay unresolved until a release owner records the required review.

## What the smoke proves

The workflow runs the ignored Rust test `live_youtube_resolves_real_metadata_and_formats` against the exact checked-out SHA. The test exercises the production `YouTubeSource`, requires canonical YouTube identity, non-empty real metadata, at least one normalized format, and at least one curated quality choice. This is intentionally separate from deterministic fixture CI.

## Expected failure behavior

A failed live smoke must not be converted into fabricated metadata or fixture success. Diagnose failures using the structured YouTube diagnostic categories:

- `youtube.unsupported_url`: invalid or unsupported input contract.
- `youtube.network_unavailable` / `youtube.network_timeout` / `youtube.http_failure`: transport or HTTP failure.
- `youtube.unavailable_media`: the selected video is unavailable/private.
- `youtube.source_changed`: the provider response shape or delivery mechanism no longer matches the bounded parser; treat this as an adapter compatibility failure requiring engineering review/update rather than a generic retry loop.
- `youtube.no_compatible_format`: metadata resolved but no supported downloadable format was found.

Do not weaken parser bounds, log provider secrets, or add runtime-downloaded executables merely to make a live smoke pass. Record the exact SHA and workflow run ID when using this path as release-candidate evidence.
