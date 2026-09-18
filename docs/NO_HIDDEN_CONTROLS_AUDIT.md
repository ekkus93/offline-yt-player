# No Hidden Controls Audit

## Scope

OYP-1703 requires an automated or deterministic UI gate proving that primary actions remain visible without scrolling on target profiles, that controls do not require horizontal scrolling, and that no controls depend on landscape-only affordances.

## Implementation

`NoHiddenControlsPolicy` defines the primary-control budget for Library, Add, Download Setup, Downloads, Player, and Settings surfaces. The policy explicitly records whether each surface requires vertical scrolling for primary actions, uses horizontal control scrolling, or exposes a landscape-only affordance.

## Qualification

`NoHiddenControlsPolicyTest` verifies every primary surface has visible actions without scrolling, forbids horizontal control scrolling, forbids landscape-only affordances, and keeps compact/large portrait profiles in scope. This deterministic host-side gate complements the screen-specific layout tests and the OYP-1702 golden coverage manifest.
