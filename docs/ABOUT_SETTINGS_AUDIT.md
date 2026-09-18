# About Settings Audit

OYP-1606 adds a bounded About settings policy for version/build metadata, licenses, privacy, diagnostics export, and legal/source-service notice surfaces.

The Android app already exposes stable build metadata through `BuildMetadata.display`. `AboutSettingsPolicy` defines the required About rows and keeps diagnostics export explicit. Diagnostics export is guarded by a policy flag requiring secret redaction before user-visible export.

`AboutSettingsPolicyTest` qualifies that the About surface includes version/build, licenses, privacy, diagnostics/export, and legal/source-service notice rows, while preserving a bounded optional-section model for product variants.
