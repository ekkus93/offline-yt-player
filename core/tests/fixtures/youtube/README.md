# Sanitized YouTube provider fixtures

These deterministic fixtures contain no live provider tokens, signed URLs, cookies, account data, or copied production payloads. They model only the bounded fields consumed by the production YouTube adapter. `combined_av.json` covers a direct-play combined stream, `split_av.json` covers separate video/audio streams, `unavailable.json` covers provider unavailability, and `malformed.json` covers missing required provider structure. Oversize behavior is represented by generated bounded test data rather than committing a multi-megabyte fixture.
