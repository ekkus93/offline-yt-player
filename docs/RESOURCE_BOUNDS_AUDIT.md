# OYP-1803 Resource Bounds Audit

`ResourceBoundsPolicy` centralizes deterministic safety limits for network connection/read timeouts, bounded metadata response bodies, concurrent downloads, and disk-space preflight.

The policy uses finite timeout values, a 2 MiB metadata/body ceiling, at most three concurrent downloads, and a 128 MiB free-space recovery reserve beyond the expected transfer size. Negative or otherwise invalid resource measurements fail closed.

Unit tests qualify each boundary and its immediate rejection edge. Runtime network/download adapters should consume these constants rather than introduce unbounded defaults.
