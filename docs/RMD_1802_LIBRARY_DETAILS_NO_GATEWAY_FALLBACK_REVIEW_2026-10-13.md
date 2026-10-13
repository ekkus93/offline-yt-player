# RMD-1802 — Library Details no-gateway fallback review (2026-10-13)

## Finding

The Library Details action must retrieve persisted item details through the application-owned `AppLibraryDetailsGateway`. When the details gateway was missing, the production callback in `ui/LibraryDownloads.kt` displayed `LibraryRowModel.title` and `LibraryRowModel.detail` concatenated with a colon. This was the *row preview*, not a successful details lookup; it could falsely imply that full repository details were available despite a failed or absent service.

## Production correction

When `detailsGatewayProvider()` returns null, report `Library details are unavailable for <title>.` and clear any stale expanded details panel instead of presenting an invented details summary. Leave the genuine details-gateway success path unchanged and continue to fetch it off the main thread.

## Behavioral qualification

`ProductionComposeBehaviorTest.library_empty_and_populated_states_are_behavioral` clicks the real Details control with a missing gateway and verifies that a bounded unavailable message appears, that the old `title: row-detail` fabrication is not shown, and that no `Library details` panel is exposed. The existing `library_details_action_reads_repository_detail_gateway_off_main_thread` covers a successful repository details lookup.

Do not infer completion from source presence: require all eight exact-head CI workflows, including packaged Android Compose instrumentation, along with the remaining production-path code review, candidate freeze, post-merge audit and preserved external YouTube/service-policy/legal approval gate. RMD-1802, RMD-1803, RMD-1805 and RMD-G06 remain unchecked.
