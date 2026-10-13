# RMD-1802 — Library Details large asset-count layout review (2026-10-13)

## Independent finding

`LibraryDetailsPanel` in `ui/LibraryDownloads.kt` used a non-scrollable Column placed before the weighted Library collection. Its content height depended on the count of persisted assets. When many assets were present, the panel could extend past the physical viewport, hide the Close details button, and starve the Library row collection. This violates the behavioral intent of RMD-1404 for this edge case, even if the ordinary single-video fixture passes.

## Correction

Bound the Details panel to at most 320dp and make its contents vertically scrollable, retaining the visible summary and a reachable Close control. Render at most 64 detailed asset records with a truthful notice indicating the full recorded asset count, preventing unbounded Compose node creation from corrupt/imported metadata. The durable asset list is unchanged and no records are deleted.

## Behavioral qualification

`ProductionComposeBehaviorTest.libraryDetailsWithManyAssetsKeepsCloseActionScrollableAndReachable` invokes the real Library Details action with a fake gateway returning 80 asset records, verifies the truncation notice, scrolls to Close details, clicks it, and checks that the panel dismisses. This is not a policy-only assertion.

Require a full successful CI matrix on the exact new master head, including Android smoke and this executable method, before claiming the finding qualified. RMD-1802 independent production review, RMD-1803 candidate freeze, RMD-1805 engineering closeout and external service/legal policy remain open.
