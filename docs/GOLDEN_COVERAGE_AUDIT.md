# Golden Coverage Audit

## Scope

OYP-1702 requires visual coverage for Library, Add, Download Setup, Downloads, Player, Settings hub, each settings subpage, compact portrait, and large portrait profiles.

## Implementation

`GoldenCoveragePolicy` defines a deterministic host-side coverage manifest with the required UI surfaces and both compact/large portrait profiles. This gives CI an executable gate for the screenshot/golden coverage matrix even before bitmap baselines are introduced.

## Qualification

`GoldenCoveragePolicyTest` verifies that every required surface is represented and that each one is covered for both compact and large portrait profiles. The policy intentionally marks bitmap goldens as deferred until the Compose screenshot harness is stable; OYP-1702's current deterministic gate prevents silent omission of any required screen/profile from that future harness.
