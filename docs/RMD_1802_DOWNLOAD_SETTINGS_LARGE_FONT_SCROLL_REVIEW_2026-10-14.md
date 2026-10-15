# RMD-1802 — Downloads Settings large-font reachability review (2026-10-14)

**Status:** Production correction and packaged Compose regression added to `master`; exact-head qualification pending. This finding is not an engineering-closeout claim.

## Independent source finding

At inspected `master` `18b6ae2629c5084a009bb0cced9443fcf53e32c0`, `app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt::SettingsPage` displayed the Downloads settings section in a full-size, non-scrollable `Column`. The header, interactive setting rows, Wi-Fi toggle and Retry policy row may exceed the available viewport when Android accessibility font scaling is high. Unlike the settings hub, the page had no vertical scrolling mechanism. This is a source-identified reachability risk, not a claim that a user-reported failure was reproduced.

## Correction and executable acceptance

- Add `verticalScroll(rememberScrollState())` to the production settings-page content column, preserving the existing page header, Back button and settings mutation handlers.
- Add `ProductionComposeLayoutAccessibilityTest.large_font_download_settings_keep_last_action_reachable_by_scrolling`: render the actual Downloads Settings page at 2.0× font scale, scroll the Subtitles action into view with Compose semantics, assert it is displayed, and check its bounds remain inside the root.
- Qualify on the new exact `master` head with Android smoke, Android/JVM, Rust, fixture E2E, supply chain and remaining required Android lanes. Do not substitute a host policy-only assertion for the rendered page test.

No RMD-1802/1803/1804/1805 or global proof checkbox is marked complete by this source/test addition. The external service-policy/legal approval remains a separate gate.
