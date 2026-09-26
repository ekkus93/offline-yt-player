# RMD-1403/RMD-1404/RMD-1405 UI Qualification Reconciliation — 2026-09-25

This note records the merged production-path Android UI qualification evidence for:

- RMD-1403 deterministic screenshot/golden tests.
- RMD-1404 no-hidden-controls behavioral gate.
- RMD-1405 accessibility qualification.
- The RMD-1601 screenshot/golden CI-matrix item.

The canonical completion source remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`. This note is intended to support a subsequent precise canonical TODO reconciliation rather than replacing the detailed checklist.

## Qualified implementation

PR #376 merged as exact master `6d21f1db5e5a5444793aeb1ddf7eb62a3ed100bd`.

The qualified exact implementation head was `e76ec6491a32a4a6800422bc474e16b4f00cf1cc`.

Exact-head qualification before merge:

- Push CI: `36218369711`
- Push Android smoke: `36218369756`
- Push Android FGS timeout: `36218369751`
- PR CI: `36218371977`
- PR Android smoke: `36218371983`
- PR Android FGS timeout: `36218371976`

Post-merge master qualification for merge SHA `6d21f1db5e5a5444793aeb1ddf7eb62a3ed100bd`:

- Master CI: `36219448765`
- Master Android smoke: `36219448785`
- Master Android FGS timeout: `36219448766`

## RMD-1403 — Deterministic screenshot/golden tests

Implementation evidence:

- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeGoldenTest.kt` adds deterministic Compose raster capture through `captureToImage()` and raster SHA-256 comparison.
- The test writes PNG evidence and `golden-raster-sha256.txt` to device storage and the Android smoke workflow pulls those artifacts into `app/build/reports/androidGoldens`.
- `.github/workflows/android-smoke.yml` runs `ProductionComposeGoldenTest` as an explicit Android smoke phase and prints the raster SHA manifest on every run.

Coverage evidence:

- `golden_library_empty` captures the Library empty state.
- `golden_library_populated` captures populated Library rows.
- `golden_add_invalid` captures invalid Add/analyze behavior.
- `golden_add_resolved` captures resolved Add/analyze behavior.
- `golden_download_setup_options` captures Download Setup/options.
- `golden_downloads_active` captures Downloads active state.
- `golden_downloads_failure` captures Downloads failure state.
- `golden_player` captures Player UI.
- `golden_settings_hub` captures Settings hub.
- `golden_smallest_supported_portrait` captures the smallest-supported portrait case.
- `golden_large_font_library` and `golden_large_font_settings` capture representative large-font cases.
- Pinned hash sets in `expectedHashes` fail the test on unintended raster changes while allowing bounded, observed emulator raster variants.

## RMD-1404 — No-hidden-controls behavioral gate

Implementation evidence:

- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeLayoutAccessibilityTest.kt` renders production Compose surfaces at the compact emulator viewport rather than relying on policy-only booleans.
- `assertInsideRoot(...)` verifies primary controls are visible and within the root bounds, catching hidden/offscreen controls and horizontal overflow.
- `long_library_uses_designed_vertical_collection_scrolling` proves bounded vertical collection scrolling remains available where designed.

Coverage evidence:

- `compact_app_shell_keeps_every_primary_destination_reachable` covers primary navigation destinations.
- `compact_library_keeps_fixed_primary_actions_visible_without_horizontal_scroll` covers Library search/layout/add controls.
- `compact_add_and_resolved_setup_keep_primary_actions_reachable` covers Add/analyze and resolved setup actions.
- `compact_downloads_keep_filters_and_row_actions_reachable` covers Downloads filters and row actions.
- `compact_settings_hub_keeps_categories_reachable_in_logical_order` covers Settings categories and visual traversal order.
- `compact_player_keeps_transport_and_secondary_controls_reachable` covers Player transport and secondary controls.
- `large_text_keeps_library_primary_actions_visible` and `large_text_keeps_settings_primary_actions_visible` cover large-font reachability.

## RMD-1405 — Accessibility qualification

Implementation evidence:

- `ProductionComposeLayoutAccessibilityTest` verifies semantic content descriptions for primary navigation controls.
- `assertMinimumTouchTarget(...)` verifies representative actionable controls satisfy 48dp minimum size.
- `compact_settings_hub_keeps_categories_reachable_in_logical_order` verifies logical top-to-bottom traversal ordering for Settings.
- `failed_download_state_is_expressed_textually_not_by_color_alone` verifies failure state is expressed textually and not by color alone.
- Large-font tests verify primary actions remain visible under representative text scaling.

Coverage boundary:

Automated Compose semantics coverage now exists for the required representative TalkBack semantics surfaces. Any future manual TalkBack spot-checks may remain useful for release notes, but RMD-1405 no longer depends on a policy-only proxy.

## RMD-1601 screenshot/golden CI item

`.github/workflows/android-smoke.yml` now includes screenshot/golden execution as an explicit Android smoke phase. The workflow also uploads bounded evidence artifacts:

- `app/build/reports/androidTests`
- `app/build/reports/androidGoldens`
- `app/build/outputs/androidTest-results`
- `app/build/outputs/managed_device_android_test_additional_output`

This satisfies the RMD-1601 screenshot/golden CI-matrix item while leaving the deterministic fixture E2E lane unchecked until RMD-1500 is implemented and qualified.

## Still open

This reconciliation does not close RMD-1500 deterministic fixture E2E work, RMD-1602 dependency/advisory checks, RMD-1603 CI evidence-quality closeout, or final RMD-1800 engineering closeout.
