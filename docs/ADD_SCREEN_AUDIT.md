# Add Screen Audit

OYP-1501 has a deterministic compact-screen policy seam.

- URL field, Paste action, Analyze action, and supported-source hint are all primary visible controls.
- The primary Add surface is explicitly non-scrolling so functional controls remain on-screen on the compact portrait target.
- Analyze accepts only HTTP(S) URL candidates; full source validation remains delegated to the source/core boundary.
- `AddScreenPolicyTest` qualifies these invariants in Android unit CI.
