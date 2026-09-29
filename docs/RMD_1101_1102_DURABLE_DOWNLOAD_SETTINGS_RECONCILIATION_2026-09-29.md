# RMD-1101/RMD-1102 Durable Download Settings Reconciliation — 2026-09-29

## Scope

This reconciliation records current-master evidence for the durable settings store and download-settings runtime consumption requirements:

- RMD-1101: add a documented durable settings store, typed observable settings, defaults/migration boundary, and persistence tests.
- RMD-1102: persist default quality, Wi-Fi-only preference, concurrency bounded by core/runtime policy, subtitle default where applicable, avoid decorative retry settings, and prove runtime consumption.

## Production implementation evidence

- `app/src/main/java/com/ekkus/offlineytplayer/settings/AppSettingsStore.kt` defines `AppSettingsStore`, `AppSettingsSnapshot`, `AppSettingsMutation`, typed `AppearanceSetting`/`LibraryLayoutSetting` enums, `SettingsSubscription`, `AppSettingsDefaults`, and `APP_SETTINGS_SCHEMA_VERSION`.
- `SharedPreferencesAppSettingsStore` is the documented durable store for this first settings slice. It opens `offline_yt_player_settings`, persists schema version, provides a migration/default boundary through `ensureSchemaVersion()`, exposes `snapshot()`, `update(...)`, and `observe(...)`, and unregisters listeners on close.
- The persisted download settings are `defaultQuality`, `wifiOnlyDownloads`, `maxConcurrentDownloads`, and `subtitleDefault`. `maxConcurrentDownloads` is clamped to the documented runtime upper bound rather than accepting arbitrary UI values.
- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt` opens the durable settings store during production bootstrap, observes settings changes into Compose state, passes `settingsSnapshot` to production UI, and wires `onUpdateSettings` back to `settingsStore.update(...)`.
- `app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt` consumes the download settings in the Add and Settings surfaces: default quality selection, Wi-Fi-only summary, concurrency summary/control, subtitle default, and a non-persisted retry label that explicitly reflects automatic runtime policy rather than pretending a decorative retry setting affects runtime.
- `app/src/main/java/com/ekkus/offlineytplayer/ui/AddWorkflowPolicy.kt` centralizes download-setting display and preferred-quality selection behavior for JVM-testable policy coverage.

## Behavioral qualification

- `app/src/test/java/com/ekkus/offlineytplayer/settings/AppSettingsStoreContractTest.kt` verifies the documented SharedPreferences store, schema/default/migration boundary, typed snapshot/mutation/observation API, and persisted download settings.
- `app/src/test/java/com/ekkus/offlineytplayer/settings/DownloadSettingsRuntimeConsumptionTest.kt` verifies production `MainActivity` opens/observes the durable settings store, Add/Settings screens consume download settings, and retry remains an automatic runtime policy rather than a persisted decorative control.

## Boundary

This closes the evidence slice for RMD-1101 and RMD-1102 only. It does not close playback settings (RMD-1103), storage settings (RMD-1104), appearance settings (RMD-1105), About metadata (RMD-1106), or final RMD-1800 closeout.
