# RMD-1101 Durable Settings Persistence Reconciliation — 2026-09-29

RMD-1101 requires a documented durable settings store, observable typed settings, migration/default handling, and persistence tests.

## Production path

- `SharedPreferencesAppSettingsStore` is the documented durable settings store for the first settings slice. It intentionally uses app-private Android `SharedPreferences` for small bootstrap-time settings without adding another persistence dependency.
- `AppSettingsSnapshot` is the typed observable settings model consumed by `MainActivity`, `OfflineYTPlayerApp`, download scheduling, playback, storage, and appearance surfaces.
- `AppSettingsMutation` validates and bounds updates before persistence: blank quality/subtitle values fall back to defaults, download concurrency is clamped, playback speed is finite and bounded, and typed enums are stored by name.
- `SharedPreferencesAppSettingsStore.observe(...)` exposes lifecycle-bound subscriptions and publishes the current snapshot immediately, then publishes updates when preferences change.
- `ensureSchemaVersion()` writes `APP_SETTINGS_SCHEMA_VERSION` on first open, giving the settings store an explicit migration/default anchor.
- `MainActivity` opens this store during production bootstrap, keeps the latest snapshot in Compose state, wires `onUpdateSettings` to `settingsStore.update(...)`, and closes the store/subscription during activity teardown.

## Deterministic qualification

`AppSettingsStoreInstrumentedTest` opens the real app-private `SharedPreferences` store on Android, verifies default observation, updates every typed settings family, closes and reopens the store, and asserts that the updated snapshot persists across reopen. It also verifies safe defaults for invalid stored enum values and lower-bound clamping for persisted concurrency.

RMD-1101 must remain unchecked in the canonical TODO until this exact implementation head passes the required exact-head CI matrix and the canonical TODO is reconciled with precise evidence.
