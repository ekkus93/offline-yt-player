# RMD-1105 Appearance Settings Reconciliation — 2026-09-30

RMD-1105 is implemented on current `master` and is ready for canonical TODO reconciliation after this documentation head qualifies.

- `AppSettingsSnapshot` and `SharedPreferencesAppSettingsStore` durably persist `AppearanceSetting` (`System`, `Light`, `Dark`) and `LibraryLayoutSetting` (`List`, `Grid`).
- `SettingsPage` mutates both values through the typed settings store.
- `OfflineYTPlayerApp` passes `settingsSnapshot.appearance` directly into `OfflineYTPlayerTheme`; `Theme.kt` selects system/light/dark Material3 color schemes immediately from that observable snapshot.
- `LibraryScreen` consumes the persisted layout setting, so the advertised layout preference survives restart rather than remaining decorative.
- `AppSettingsStoreInstrumentedTest` exercises durable typed settings persistence across close/reopen.

Qualification baseline: exact master `3508e0df82f514b84769d7ca234c571bf2302734` passed CI `36699824078`, Android smoke `36699823941`, Android FGS/UIDT `36699823965`, Supply chain `36699823904`, CI evidence `36699823973`, and Deterministic E2E fixture `36699824064`.
