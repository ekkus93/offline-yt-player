# RMD-1105 Appearance Settings Reconciliation — 2026-09-29

## Scope

RMD-1105 requires durable System/Light/Dark appearance selection, immediate Compose theme application, and durable Library layout preference when that control is offered.

## Production implementation evidence

- `app/src/main/java/com/ekkus/offlineytplayer/settings/AppSettingsStore.kt` persists `AppearanceSetting` and `LibraryLayoutSetting` through `SharedPreferencesAppSettingsStore.update(...)` using `Keys.Appearance` and `Keys.LibraryLayout`.
- `AppSettingsSnapshot` carries the typed appearance and library-layout values, and `MainActivity` observes the durable settings store into Compose state.
- `app/src/main/java/com/ekkus/offlineytplayer/ui/Theme.kt` applies `AppearanceSetting.System`, `AppearanceSetting.Light`, and `AppearanceSetting.Dark` in `OfflineYTPlayerTheme(...)`, using `isSystemInDarkTheme()` only for the System mode.
- `app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt` passes `settingsSnapshot.appearance` to `OfflineYTPlayerTheme(...)` and exposes the Appearance settings page mutation for theme and library layout.
- `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt` consumes `settings.libraryLayout` and updates the durable layout preference through `onUpdateSettings`.

## Behavioral qualification

- `app/src/test/java/com/ekkus/offlineytplayer/settings/AppearanceSettingsRuntimeConsumptionTest.kt` verifies that appearance and library-layout preferences are durably persisted and consumed by the production theme and Library UI paths.

## Boundary

This closes the focused RMD-1105 durable appearance/layout evidence slice only. It does not close playback settings, storage settings, or final engineering closeout.
