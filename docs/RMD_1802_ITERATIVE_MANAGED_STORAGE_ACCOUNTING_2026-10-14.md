# RMD-1802 — Iterative managed-storage accounting (2026-10-14)

## Independent review finding

`StorageSettingsManager.summarize` used `File.managedEntries()`, which built a complete `List<File>` through recursive `children.flatMap(File::managedEntries)`. It then traversed that list multiple times to calculate database, incomplete, and media totals. `managedSize` similarly materialized each cache tree. Large or deeply nested app-private trees could consume unbounded intermediary memory or overflow the JVM call stack, even though the UI scan already ran off the main thread.

## Correction

The shared no-follow walker now uses an explicit `ArrayDeque<File>` and a lazy `Sequence<File>`, without one call frame per nested directory. Storage summary visits each regular file once and accumulates category totals; cache accounting streams file sizes. Incomplete cleanup materializes only the actual candidate targets before deletion, retaining the existing root/symlink guards, directory-enumeration errors, and non-following deletion semantics.

## Behavioral proof

`StorageSettingsRuntimeConsumptionTest.deepManagedTreeIsCountedWithoutRecursiveTraversalOrFollowingSymlinks` creates 256 nested app-owned directories, verifies media and incomplete byte totals, verifies incomplete cleanup, and ensures an external file referenced by a symlink remains untouched. Existing real-files and symlink-root tests remain mandatory.

This is a bounded Rust-independent JVM resource-hardening correction. Exact-head Android JVM CI, required Android smoke, the remaining standard workflows, and independent RMD-1802 review must qualify the new SHA before any checklist closeout. The external YouTube/service/legal release gate remains separate.

## Deep cache cleanup follow-up (2026-10-14)

The initial iterative accounting correction left `deleteManagedEntry` recursively descending through cache subdirectories. Clearing a deeply nested app-private cache could still overflow the JVM stack. Cache cleanup now uses an explicit post-order stack, checks every descendant remains within the managed root, and unlinks symlinks without traversing them. `StorageSettingsRuntimeConsumptionTest.deepCacheCleanupUsesPostorderWithoutFollowingSymlinks` exercises 256 nested directories, verifies cleanup byte totals, preservation of an external symlink target, retention of the managed cache root, and empty post-cleanup accounting. This follow-up also requires exact-head CI and does not close independent review.
