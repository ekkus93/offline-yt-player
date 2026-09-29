# RMD-1104 Storage Settings Reconciliation — 2026-09-29

RMD-1104 requires storage settings to calculate actual managed-media usage, show database/partial/cache breakdowns where useful, implement safe cleanup actions, require destructive cleanup confirmation, and test behavior against temporary storage.

## Production path

`app/src/main/java/com/ekkus/offlineytplayer/settings/StorageSettingsManager.kt` implements the storage-accounting boundary:

- `StorageSettingsManager.summarize()` walks the app files root and returns a `ManagedStorageSummary` split into managed media, database files, incomplete partial/resume files, cache usage, and free bytes.
- Database accounting includes `offline-yt-player.sqlite3` and SQLite sidecar files.
- Incomplete-transfer accounting includes `.partial` assets and `.resume.json` state.
- Cache accounting uses the application cache root.
- `cleanup(ManagedCleanup.Cache)` removes only cache-root entries.
- `cleanup(ManagedCleanup.Incomplete)` removes only incomplete partial/resume files under the app files root.
- Cleanup verifies canonical containment before deleting any target, preserving the safe-root invariant for destructive storage operations.

`AppShell.kt` exposes the storage summary through the Settings surface and routes cleanup actions through explicit confirmation callbacks before invoking the manager.

## Deterministic qualification

`app/src/test/java/com/ekkus/offlineytplayer/settings/StorageSettingsManagerTest.kt` builds temporary files, database state, partial/resume state, and cache entries, then verifies:

- managed media, database, partial, cache, and free-space bytes are calculated from real files;
- incomplete cleanup removes only partial/resume files;
- complete managed media remains intact after incomplete cleanup;
- cache cleanup removes cache bytes through the cache cleanup path.

## Boundary

This closes the deterministic storage-settings proof for RMD-1104. Broader destructive UX proof remains covered by the production Settings Compose behavior/golden/accessibility lanes and the final RMD-1800 review/qualification requirements.
