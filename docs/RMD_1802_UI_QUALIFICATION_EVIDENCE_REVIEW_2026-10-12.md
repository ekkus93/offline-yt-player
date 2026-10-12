# RMD-1802 — UI qualification evidence review (2026-10-12)

## Finding: self-attested acceptance masquerading as behavioral tests

The old `NoHiddenControlsPolicy.kt` declared `requiresVerticalScrollForPrimaryActions=false`, `hasHorizontalControlScrolling=false` and `exposesLandscapeOnlyAffordance=false` on each screen. `NoHiddenControlsPolicyTest.kt` read those declarations back, so the test would pass even if the rendered UI overflowed the viewport, hid controls, or required horizontal scrolling.

`AccessibilityQualificationPolicy.kt` similarly assigned `hasTalkBackLabel=true`, `hasLogicalFocusOrder=true`, `hasNonColorStatusCue=true` and `supportsLargeText=true` to each screen; its host-side tests only checked those constants. No Compose semantics nodes or rendered targets were examined.

These declarations are not production UI gates and cannot support RMD-G01/G02, RMD-1400, RMD-1703 or RMD-1805.

## Correction

On `master`, commit `bcd3f2be4fc8abd8f93ef7a11c5b1ce740c64d40` deletes both policy objects and their two circular host tests. This removes invalid evidence rather than altering app runtime behavior. `docs/NO_HIDDEN_CONTROLS_AUDIT.md` now identifies real Android instrumentation as the source of qualification.

## Relevant executable tests

- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeLayoutAccessibilityTest.kt`: rendered compact destinations, primary control bounds, scrolling, minimum target sizes, accessibility semantics and non-color failure cues, large-text Library/Settings.
- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt`: rendered controls that invoke UI/gateway behaviors.
- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeGoldenTest.kt`: pinned raster SHA-256 checks for screenshots. Its 12 named golden test methods are not the same as the 22 required combinations from `GoldenCoveragePolicy.RequiredScenarios`; do not cite the scenario inventory as execution evidence.

## Remaining qualification work

1. Enumerate all required target screen/profile pairs, primary controls and visibility conditions; map each to an executed Android/Compose assertion or screenshot.
2. Implement any missing compact/large portrait golden and accessibility/layout assertions; verify against rendered UI and pinned raster artifacts, not policy-only booleans.
3. Verify the suite actually runs in the required Android gates. Record exact SHA, CI run IDs and artifact evidence.
4. Continue independent Rust/Android production audit and canonical TODO reconciliation; keep external policy/legal approval separate.

**Status: OPEN.** The deleted declarations are resolved as false evidence; complete RMD-1802/1803/1805 review and exact-head qualification still require executable coverage and evidence.
