# RMD-1106 About Reconciliation — 2026-09-30

RMD-1106 is implemented on current `master` and is ready for canonical TODO reconciliation after this documentation head qualifies.

- `AboutMetadataProvider.current()` reads `BuildConfig.VERSION_NAME`, `BuildConfig.VERSION_CODE`, and the build-provided `SOURCE_REVISION`; the About UI no longer presents a hard-coded `0.1.0` value.
- `AboutSettingsPage` renders version/build and source revision plus the supported license, privacy, diagnostics, and support disclosures.
- Local builds fail closed to the explicit `local build` revision label rather than fabricating a source SHA.
- The About page is part of the production Settings destination and is covered by the existing Compose settings/golden qualification surface.

Qualification baseline: exact master `3508e0df82f514b84769d7ca234c571bf2302734` passed CI `36699824078`, Android smoke `36699823941`, Android FGS/UIDT `36699823965`, Supply chain `36699823904`, CI evidence `36699823973`, and Deterministic E2E fixture `36699824064`.
