# Advanced Download Options Audit

OYP-1503 is qualified through a deterministic UI policy seam.

- Subtitle, audio, and advanced choices are excluded from the primary Download Setup surface.
- Those choices live on the dedicated advanced-options subpage.
- Primary controls on that subpage are explicitly non-scroll-dependent on the compact portrait target.
- `AdvancedDownloadOptionsPolicyTest` qualifies separation and visibility invariants in Android unit CI.
