# RMD-1802 — Settings system-Back hierarchy review (2026-10-14)

**Status:** Source/test correction committed on `master`; exact-head Android qualification pending.

## Source finding

At `master` `6a0e66609e6f9a67db1708ec4d49caa69b27e90e`, `SettingsScreen` maintained a `rememberSaveable` selected section and exposed a visible Back button to return to the Settings hub, but did not install a nested Android `BackHandler`. The parent `OfflineYTPlayerApp` handler tracks only app destinations and playback. A hardware/gesture Back while viewing a Settings detail section could therefore pop the entire Settings destination rather than return to the hub. This is a source-based navigation defect, not a claim of a reported field failure.

## Implementation and qualification

- Install `BackHandler(enabled = section != null) { section = null }` in `SettingsScreen`. The nested handler is active only on a detail section; when on the hub, the parent destination handler retains its existing behavior.
- Add packaged `ProductionComposeLayoutAccessibilityTest.system_back_from_settings_detail_returns_to_hub`. It navigates through the real app to Downloads Settings, invokes the activity `OnBackPressedDispatcher`, and asserts the Settings hub is shown instead of leaving Settings.
- Require the exact-head Android smoke/Compose suite and complete CI matrix to pass before treating the fix as qualified. Do not substitute source-string or policy-constant tests for the production callback.

No final engineering or global proof checkbox is closed by this change. External service-policy/legal approval remains separate.
