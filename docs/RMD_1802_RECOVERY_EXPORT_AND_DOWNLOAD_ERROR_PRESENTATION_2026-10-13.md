# RMD-1802 — Recovery Export and Downloads Error Presentation Review (2026-10-13)

## Finding

The Library recovery action exported up to 2048 raw error characters through an Android ACTION_SEND chooser. User-facing repository failures/unavailability in Library and Downloads, and per-download row failures, also directly rendered gateway diagnostic strings. Source analysis, download enqueue, and Library mutations already passed their diagnostics through a shared formatter, but these paths did not. An imported/compromised state or transport diagnostic containing signed URL query fields, HTTP Authorization/Cookie headers, or control characters could therefore expose credentials in UI or share payloads.

## Production correction

`app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt` now applies `SourceMetadataPolicy.diagnostic` to repository unavailable/failure messages, per-download row errors, and the recovery share payload. The formatter bounds displayed/exported diagnostics, normalizes control characters, and redacts header secrets and signed URLs. This changes only presentation/export text, not durable library or queue state. Recovery condition classification still uses the original structured failure message before redaction.

## Behavioral proof and status

`ProductionComposeBehaviorTest.recoveryExportUsesRedactedDiagnosticInsteadOfRawGatewayText` supplies a fake Context that intercepts the production chooser startActivity, invokes the rendered Export diagnostics action, inspects the nested ACTION_SEND payload and asserts credentials are absent and the header is redacted.

`ProductionComposeBehaviorTest.downloadsAndLibraryUnavailableDiagnosticsAreRedactedInRenderedUi` renders the actual Downloads failure/row error and Library unavailable screens with injected secret-bearing diagnostics and asserts the redacted text is visible and the marker absent.

Both tests require exact-head Android smoke evidence; the other required CI workflows and independent source review must also pass before RMD-1802/RMD-1803/RMD-1805/global closeout. External service-policy/legal approval is unchanged.
