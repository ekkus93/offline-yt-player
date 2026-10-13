# RMD-1802 — Library Details response identity validation (2026-10-13)

## Finding

The production Library screen requested item details through `AppLibraryDetailsGateway.getDetails(row.id)` but accepted an arbitrary successful `CoreLibraryDetails` object without ensuring its `itemId` matched the requested row. A binding, cache, or malformed repository response could display another item's private metadata under the clicked item's context. The earlier missing-gateway correction is separate: this case has a gateway that returns the wrong identity.

## Correction

Clear any previously expanded detail panel on a new Details action. When the gateway returns a non-null details object, check `itemId == row.id` before rendering; otherwise present the existing explicit unavailable status and do not expand foreign metadata. The original read remains off the main thread and normal matching-ID results are unchanged.

## Executable qualification

`ProductionComposeBehaviorTest.libraryDetailsRejectsGatewayRecordForDifferentItem` clicks the rendered Details action on `item-1`, supplies a fake repository gateway returning `another-private-item`, observes the unavailable state, confirms the foreign title and panel are absent, and verifies that the gateway was asked for `item-1`. The existing real-button matching-ID success test continues to qualify the allowed path.

This addition requires complete exact-head Rust/JVM/packaging/Android instrumentation/goldens/fixture/FGS/cold-start/network/supply-chain CI; it cannot close independent RMD-1802, final RMD-1803/1804/1805 or global checkboxes before that evidence and the remaining review. External release/legal approval is not implied.
