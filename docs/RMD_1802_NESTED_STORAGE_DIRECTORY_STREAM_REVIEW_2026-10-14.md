# RMD-1802 — Nested storage directory enumeration review (2026-10-14)

## Independent finding

The earlier streaming cache cleanup eliminated a root-level `File.listFiles()` allocation, but `StorageSettingsManager.deleteManagedEntry` still materialized a full child array at every nested directory. The shared `File.managedEntries` accounting traversal did the same. Large nested media/cache directories could therefore allocate avoidably large arrays during Storage Settings scans or cleanup.

## Production correction

At `master` implementation `2d81f14fe6a2d1dab27e48c8640474baa68220be`, both nested traversals use scoped `Files.newDirectoryStream` enumeration. The existing explicit stacks, post-order deletion, no-follow symlink checks, root-boundary validation, and I/O error propagation are retained. The change avoids eager directory child arrays; the explicit traversal stack is not claimed to use constant memory.

## Regression and qualification

Host-JVM `StorageSettingsManagerTest.nestedWideCacheAccountingAndCleanupPreserveMedia` (commit `ae909a48fb27569d83fb64fb32b3119f889afedc`) creates 512 nested cache files, verifies byte accounting, complete cleanup, idempotence and preservation of completed media. Existing deep-tree and symlink-isolation tests remain mandatory.

Require passing exact-head main CI and all eight standard workflows before qualification. RMD-1802 independent review, RMD-1803 candidate freeze, RMD-1804 post-merge verification, RMD-1805 engineering definition of done, and external service/legal approval remain open.
