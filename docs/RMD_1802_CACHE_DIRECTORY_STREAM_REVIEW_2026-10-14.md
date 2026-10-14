# RMD-1802 — Wide cache-directory allocation review (2026-10-14)

## Independent production finding

At exact `master` `6cde2fb20ba33af9234882a6a85aad8d14e45994`, `StorageSettingsManager.cleanup(ManagedCleanup.Cache)` used `cacheRoot.listFiles().asSequence()`. Calling `listFiles()` eagerly allocates an array of every direct cache child before deletion starts. Converting that array to a `Sequence` does not make enumeration lazy. An unusually wide cache directory can therefore cause avoidable allocation pressure and a long pause before cleanup begins, despite the previous iterative recursive-tree hardening.

## Correction

Enumerate the cache root with `Files.newDirectoryStream(cacheRoot.toPath()).use { ... }`, process each returned child with the existing no-follow `managedSize` and root-checked iterative `deleteManagedEntry`, and close the directory handle on success or exception. Preserve the separate lazy incomplete-file traversal and the fail-closed root validation. The implementation remains off the Android UI thread through the existing Storage settings coroutine.

## Regression and qualification

`StorageSettingsManagerTest.cacheCleanupStreamsWideDirectoryAndPreservesMedia` creates 768 direct cache files, checks accurate accounted/deleted bytes, verifies completed media survives, and verifies idempotent repeated cleanup. Existing symlink, nested-directory, and inaccessible-root regressions remain mandatory. This is a bounded resource-hardening correction, not proof of safe concurrent cleanup during an active transfer.

The correction requires Rust/Android fast gates, JVM tests, packaged Android smoke, and all eight exact-head workflows before the relevant finding can be called qualified. RMD-1802 independent review, RMD-1803 final freeze, RMD-1804/1805 closeout, and external YouTube/service/legal approval remain open.
