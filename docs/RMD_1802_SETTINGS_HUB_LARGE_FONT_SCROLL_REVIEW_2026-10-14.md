# RMD-1802 — Settings hub accessibility scrolling review (2026-10-14)

**Status:** Production change and packaged instrumentation added; exact-head CI remains pending.

## Finding

`app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt::SettingsHub` rendered a non-scrollable `Column` containing an explanatory heading and five weighted Settings-category buttons. The fixed viewport and each button's minimum touch height can overflow on compact screens or with large accessibility text, potentially making the last Settings category unreachable. This is separate from the dedicated `SettingsPage` scrolling correction in `docs/RMD_1802_DOWNLOAD_SETTINGS_LARGE_FONT_SCROLL_REVIEW_2026-10-14.md`.

## Correction

Give the hub a vertical `rememberScrollState()` owner and remove proportional `weight(1f)` from its children, allowing full-height buttons with minimum touch size and reachable overflow. Do not change button action routing or Settings persistence behavior.

## Qualification

`app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeLayoutAccessibilityTest.kt::large_font_settings_hub_keeps_last_category_reachable_by_scrolling` renders the production hub at font scale 2.0, scrolls `About` into view, and verifies its bounds inside the Compose root.

- [ ] Qualify this implementation on exact-head Android smoke, including packaged layout tests.
- [ ] Qualify exact-head Rust/core, Android JVM/build/UniFFI, other Android lanes and supply chain.
- [ ] Reconcile the canonical remediation TODO with precise run IDs and complete independent review before final closeout.
- [ ] Keep external service-policy/legal release approval separate and unresolved.
