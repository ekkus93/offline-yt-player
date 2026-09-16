# Android shell audit

This document qualifies the current Android application shell against `docs/OFFLINE_YT_PLAYER_TODO.md` OYP-201 through OYP-204.

## Scope

Audited commit: `002c90dbbf2b2a16b989921de625f5001954eca1`

The audit covers the portrait-only Compose shell, bottom navigation, fixed-region layout primitives, explicit Midnight Transit typography and semantic tokens, and deterministic JVM qualification for those constraints. Device/emulator screenshot and golden tests remain tracked by the later OYP-1700 and OYP-2300 closeout gates; they are not required to establish the shell skeleton.

## OYP-201 — Compose application

Qualified by:

- `app/build.gradle.kts` enables the Android application plugin, Compose compiler plugin, Compose build feature, and Material 3/Compose dependencies through the pinned version catalog.
- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt` calls `setContent { OfflineYTPlayerApp(...) }`.
- `app/src/main/java/com/ekkus/offlineytplayer/ui/Theme.kt` defines the Midnight Transit semantic palette, interaction tokens, spacing tokens, radius tokens, minimum touch-target token, and explicit Material 3 typography tokens.
- `OfflineYTPlayerTheme` wraps the app in Material 3 `MaterialTheme` with dark, light, and system theme selection and supplies the Midnight Transit typography rather than relying on bare Material defaults.

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
- Primary Add and Download Setup actions are placed in bounded rows, and advanced choices move to a dedicated subpage instead of hiding them below the fold.
- Settings use a hub plus focused subpages, limiting the number of primary rows on any compact portrait screen.
- `PortraitLayoutPolicy` records the compact portrait and large-font assumptions used by deterministic layout-policy tests.
- `AndroidShellPolicyTest.compactPortraitPrimaryControlsAreBudgeted` checks compact portrait and large-font budget assumptions.
- `AndroidShellPolicyTest.midnightTransitTokensMeetBasicContrastAndTouchTargets` checks contrast thresholds and the 48 dp minimum touch-target token.

## Qualification evidence

- Android shell qualification branch exact-head CI `35061088299` passed at `788872a04f886895858f177286069839243093fa`.
- Explicit Midnight Transit typography exact-head master CI `35100367233` passed at `002c90dbbf2b2a16b989921de625f5001954eca1`.

OYP-201 through OYP-204 are implementation- and automated-qualification complete. The canonical TODO checkboxes remain a reconciliation step and must only be flipped after this refreshed evidence is merged. Later OYP-1700/OYP-2303 work still owns broader screenshot/golden and final portrait UX evidence before v1 release.
