# Android shell audit

This document qualifies the current Android application shell against `docs/OFFLINE_YT_PLAYER_TODO.md` OYP-201 through OYP-204.

## Scope

Audited commit: `bf0a25eda742decb775baf26ea914c22644daf3f`

The audit covers the portrait-only Compose shell, bottom navigation, fixed-region layout primitives, and deterministic JVM qualification added for those constraints. Device/emulator screenshot and golden tests remain tracked by the later OYP-1700 and OYP-2300 closeout gates; they are not required to establish the shell skeleton.

## OYP-201 — Compose application

Qualified by:

- `app/build.gradle.kts` enables the Android application plugin, Compose compiler plugin, Compose build feature, and Material 3/Compose dependencies through the pinned version catalog.
- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt` calls `setContent { OfflineYTPlayerApp(...) }`.
- `app/src/main/java/com/ekkus/offlineytplayer/ui/Theme.kt` defines the Midnight Transit semantic palette, interaction tokens, spacing tokens, radius tokens, and minimum touch-target token.
- `OfflineYTPlayerTheme` wraps the app in Material 3 `MaterialTheme` with dark, light, and system theme selection.

## OYP-202 — Portrait-only enforcement

Qualified by:

- `app/src/main/AndroidManifest.xml` sets `android:screenOrientation="portrait"` on `.MainActivity`.
- No `src/main/res/*land*` resource directory is allowed by `AndroidShellPolicyTest.manifestLocksMainActivityToPortraitAndNoLandscapeResourcesExist`.
- The same deterministic test asserts the manifest portrait lock, giving CI a practical configuration-level guard without requiring an emulator rotation pass.
- README/local-development documentation states that Android is portrait-only and that landscape resources and rotate/fullscreen-landscape actions must not be added.

## OYP-203 — Navigation shell

Qualified by:

- `AppDestination` defines exactly four destinations: Library, Downloads, Add, and Settings.
- `OfflineYTPlayerApp` launches into Library unless a share intent starts Add directly.
- `FixedRegionScaffold` uses a fixed `NavigationBar`/`NavigationBarItem` bottom bar, not a drawer or horizontal carousel.
- Destination and subpage state use `rememberSaveable` where state must survive recomposition/configuration restoration.
- `AndroidShellPolicyTest.bottomNavigationUsesFixedPortraitDestinations` keeps the fixed four-destination contract under unit test.

## OYP-204 — Fixed-region layout primitives

Qualified by:

- `FixedRegionScaffold` establishes a fixed top app bar, bounded content callback, safe-drawing insets, and fixed bottom navigation.
- Primary Add and Download Setup actions are placed in bounded rows, and advanced choices move to a dedicated subpage instead of hiding below the fold.
- Settings use a hub plus focused subpages, limiting the number of primary rows on any compact portrait screen.
- `PortraitLayoutPolicy` records the compact portrait and large-font assumptions used by deterministic layout-policy tests.
- `AndroidShellPolicyTest.compactPortraitPrimaryControlsAreBudgeted` checks compact portrait and large-font budget assumptions.
- `AndroidShellPolicyTest.midnightTransitTokensMeetBasicContrastAndTouchTargets` checks contrast thresholds and the 48 dp minimum touch-target token.

## Qualification evidence

Automated evidence expected for this audit branch:

- `./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug`
- Repository CI exact-head run for the audit commit.

The deterministic JVM test intentionally checks policy-level invariants that can run in ordinary CI. Later OYP-1700/OYP-2303 work must still provide broader screenshot/golden and closeout UX evidence before v1 release.
