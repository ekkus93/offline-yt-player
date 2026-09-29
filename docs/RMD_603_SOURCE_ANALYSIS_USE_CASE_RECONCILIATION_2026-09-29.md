# RMD-603 Source Analysis Use Case Reconciliation — 2026-09-29

## Scope

RMD-603 requires the app source-analysis path to centralize URL validation plus production source-registry resolution, expose actionable loading/resolved/unsupported/network/source-changed states, and prevent superseded analysis requests from publishing stale results.

## Implementation evidence

- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/AppSourceAnalysisGateway.kt` remains the generated-binding boundary backed by `FfiYouTubeSourceService`, normalizes inputs with the shared Android/core `SupportedUrlPolicy`, and maps provider-neutral source metadata and curated quality choices into app-owned models.
- `app/src/main/java/com/ekkus/offlineytplayer/coregateway/SourceAnalysisUseCase.kt` adds the app-owned use-case layer for source analysis. It begins requests by validating through `SupportedUrlPolicy`, emits `Loading` tickets for accepted requests, maps resolver output into `Resolved`, `Unsupported`, `NetworkFailure`, `SourceChanged`, or generic `Failed` states, and rejects stale completions as `Superseded` when a newer request has replaced the active ticket.
- The use case is deliberately JVM-testable so URL validation, state mapping, and superseded-request handling are proved off-emulator under the acceleration plan. Android runtime proof for production generated bindings remains covered by the existing source-analysis gateway and Compose/instrumentation lanes.

## Behavioral qualification

- `app/src/test/java/com/ekkus/offlineytplayer/coregateway/SourceAnalysisUseCaseTest.kt` verifies unsupported input fails before calling the gateway, resolved source analysis preserves curated quality choices, network/source-changed/unsupported/generic failures map to actionable states, and stale analysis completions cannot overwrite a newer request.

## Boundary

This closes the app source-analysis use-case/state-mapping obligation for RMD-603. It does not close RMD-703/RMD-704 setup-option persistence, RMD-705 share-flow E2E, or the final RMD-1500 end-to-end Android qualification tasks.
