# No Hidden Controls Audit

## Scope

OYP-1703 requires executable evidence that required primary controls are reachable in supported portrait layouts without unintended horizontal scrolling, without landscape-only affordances, and with acceptable touch targets and focus/semantic behavior.

## Independent evidence correction (2026-10-12)

The former `NoHiddenControlsPolicy` and `NoHiddenControlsPolicyTest` encoded a manually maintained table of booleans and tested the table against itself. Those tests **did not inspect actual rendered UI** and must not be cited as qualification. The parallel `AccessibilityQualificationPolicy` and its host-side tests had the same problem. These four files were removed directly from `master` by `bcd3f2be4fc8abd8f93ef7a11c5b1ce740c64d40`.

## Behavioral evidence

`app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeLayoutAccessibilityTest.kt` creates rendered Compose screens and checks real semantics/bounds, including compact Library/Add/Downloads/Player/Settings actions, minimum touch targets, TalkBack descriptions, visible failure cues, large-text controls, and long-library scrolling. `ProductionComposeBehaviorTest.kt` exercises clickable Compose flows. `ProductionComposeGoldenTest.kt` captures and hashes rendered bitmaps against pinned golden variants.

These are Android runtime tests, not claims generated from manually set constants. The final acceptance review must check that *every* required screen/profile and action is covered, compare `GoldenCoveragePolicy.RequiredScenarios` (22 intended surface/profile pairs) against the executed captures, and keep any uncovered cases open. Passing a subset is not blanket accessibility or no-hidden-controls approval.

## Closeout status

**OPEN pending independent coverage mapping and final exact-head qualification.** Removing misleading tests prevents false positives; it does not by itself demonstrate every OYP-1703 condition. Keep canonical RMD-1802, RMD-1803 and RMD-1805 checkboxes open until proof is complete.
