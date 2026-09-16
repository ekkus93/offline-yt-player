# OYP-200 Android shell reconciliation

OYP-201 through OYP-204 are implemented and qualified on the exact branch head that introduced `docs/ANDROID_SHELL_AUDIT.md` and `AndroidShellPolicyTest`.

## OYP-201 — Compose application

Qualified implementation evidence:

- `app/` is the Kotlin Android application module.
- `app/build.gradle.kts` enables Compose and depends on the Material 3 Compose stack.
- `OfflineYTPlayerTheme` applies the Midnight Transit semantic palette.
- `MidnightTransit` defines semantic spacing, radius, interaction, and minimum-touch-target tokens.

## OYP-202 — Portrait-only enforcement

Qualified implementation evidence:

- `MainActivity` is declared with `android:screenOrientation="portrait"`.
- The repository contains no landscape resource contract required by the app shell.
- `AndroidShellPolicyTest` deterministically qualifies the portrait policy and compact-layout assumptions that can be checked on the JVM.
- `docs/ANDROID_SHELL_AUDIT.md` documents the portrait-only support policy.

The manifest orientation lock is the platform mechanism that prevents rotation from selecting a landscape activity layout. Device-level rotation remains part of final portrait UX qualification rather than a reason to leave the implementation task open.

## OYP-203 — Navigation shell

Qualified implementation evidence:

- `AppDestination` contains exactly Library, Downloads, Add, and Settings.
- Library is the launch destination unless a valid share flow intentionally routes to Add.
- destination state uses `rememberSaveable`.
- `FixedRegionScaffold` renders a fixed Material 3 `NavigationBar`; no drawer or horizontal navigation carousel is used.

## OYP-204 — Fixed-region layout primitives

Qualified implementation evidence:

- `FixedRegionScaffold` owns the fixed top app bar, bounded content region, and fixed bottom navigation.
- `WindowInsets.safeDrawing` handles system insets.
- `PortraitLayoutPolicy` plus `AndroidShellPolicyTest` qualifies compact portrait and large-font primary-control budgets.
- settings and advanced download controls move to dedicated subpages rather than relying on hidden below-the-fold primary actions.

## CI evidence

The implementation/audit qualification commit `788872a04f886895858f177286069839243093fa` passed both push CI run `35061088299` and pull-request CI run `35061345298`. PR #71 was merged to master as `86e07619f4d3172d4a518b7353b40840d8419bd4`.

`docs/OFFLINE_YT_PLAYER_TODO.md` still requires mechanical checkbox reconciliation. This file exists to make that reconciliation evidence explicit and prevent the implementation state from being confused with an unimplemented shell.
