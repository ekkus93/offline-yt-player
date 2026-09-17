# Download Setup UI Audit

OYP-1502 has a deterministic compact Download Setup policy seam.

- Thumbnail, title, and duration remain visible summary metadata.
- Quality selection is curated rather than exposing raw provider format identifiers.
- Estimated size is rendered only when the resolver supplies a trustworthy non-negative estimate.
- Options and Download are fixed primary actions.
- The primary setup surface is explicitly non-scrolling so its primary controls remain visible on the compact portrait target.
- `DownloadSetupPolicyTest` qualifies these invariants in Android unit CI.
