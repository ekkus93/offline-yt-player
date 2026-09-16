# Android portrait application-shell audit

This audit records implementation evidence for OYP-201 through OYP-204 in `docs/OFFLINE_YT_PLAYER_TODO.md`. It is intentionally scoped to the application shell and fixed-region layout contract; later screen-specific UX and golden-test milestones remain separate gates.

## OYP-201 — Compose application

- `app/build.gradle.kts` defines the Android application module, enables Compose, imports the pinned Compose BOM, and includes Material 3.
- `MainActivity` installs `OfflineYTPlayerApp` with `setContent`.
- `ui/Theme.kt` defines the Midnight Transit semantic palette: background/surfaces, primary/accent, text, success/warning/error, disabled/pressed/focus/error-container interaction tokens.
- The same theme module defines reusable 14/18 dp radii, 16/12 dp spacing, the 48 dp minimum touch target, dark/light schemes, and a dark-default `ThemePreference` contract.

## OYP-202 — Portrait-only enforcement

- `AndroidManifest.xml` declares `MainActivity` with `android:screenOrientation="portrait"`; no alternate landscape activity is present.
- The UI uses one Compose implementation rather than orientation-specific layout resources, so there is no landscape resource path to maintain.
- `docs/ARCHITECTURE_DECISIONS.md` ADR-005 and `docs/USER_GUIDE.md` document portrait-only support as an intentional product constraint.
- The manifest lock is the platform enforcement mechanism: rotating a device cannot cause Android to recreate this activity in a supported landscape configuration.

A future instrumented-device suite may add a physical rotation assertion, but that is not required to establish the configuration contract already enforced by the manifest.

## OYP-203 — Navigation shell

- `AppDestination` has exactly four destinations: Library, Downloads, Add, Settings.
- `OfflineYTPlayerApp` initializes to Library unless a validated share flow intentionally routes directly to Add.
- The selected destination is held with `rememberSaveable`, preserving navigation state across normal Compose recreation.
- `FixedRegionScaffold` renders every destination in one fixed Material 3 `NavigationBar`; there is no drawer or horizontally scrolling navigation surface.
- Each navigation item has a visible label and an explicit accessibility description.

## OYP-204 — Fixed-region layout primitives

- `FixedRegionScaffold` owns a fixed `TopAppBar`, bounded content slot, and fixed bottom `NavigationBar`.
- `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` applies system safe-drawing insets to the shell.
- `PortraitLayoutPolicy` records the compact portrait height (640 dp), large-font qualification scale (1.30), destination/control counts, and a deterministic `primaryControlsFit` rule so compact/large-font constraints can be tested without depending on screenshots.
- Primary shell controls use `sizeIn(minHeight/minWidth = MidnightTransit.MinimumTouchTarget)` where appropriate; naturally unbounded screen collections remain the only scrolling regions.
- Download Setup and settings already split advanced/category content onto bounded subpages instead of hiding primary controls below a scrolling fold.

## Qualification boundary

The repository CI runs Android unit/lint/build checks and reports the exact commit SHA. This audit does not close OYP-1303/OYP-1702/OYP-1703: device-profile/golden and broader no-hidden-controls qualification remain their own milestones. It only establishes that the reusable Android shell required by OYP-201 through OYP-204 is implemented and structurally follows the portrait/fixed-region contract.
