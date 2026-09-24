# RMD-408 concurrency/resource-policy reconciliation — 2026-09-24

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, RMD-408.

## Current-master production evidence

Current master `99642b9c416f4cac2dbcbc896688b9a54f015365` contains the concurrency/resource-policy implementation originally qualified on `ralph/rmd-408`.

- `core/src/concurrency.rs` owns the hard portable-core ceiling as `MAX_CONCURRENT_DOWNLOADS = 4` and the product default as `DEFAULT_CONCURRENT_DOWNLOADS = 2`.
- `bounded_download_concurrency` clamps any platform/user preference into the core-safe range; `DownloadConcurrencyGate::new` rejects zero and values above the core ceiling.
- `core/src/ffi.rs` exports `ffi_max_concurrent_downloads` and `ffi_bounded_download_concurrency`, so Android can consume the core-owned policy without defining a second maximum.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt` retains only `DefaultConcurrentDownloads = 2`; it deliberately has no Android-owned `MaxConcurrentDownloads` constant and clamps requested values against the core-provided maximum.
- Rust mapping tests in `core/src/concurrency.rs` cover zero, default, exact maximum, above-maximum, and `usize::MAX` requests, and verify the admission gate cannot exceed its configured bound.
- Rust FFI tests in `core/src/ffi.rs` verify the exported maximum and bounded mapping.
- JVM coverage in `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundServicePolicyTest.kt` verifies Android preference clamping and asserts that the service source does not reintroduce a duplicate maximum.

## Qualification evidence

Historical implementation head `a2e5110405d85f5ed59a90e5e42888fa487b356e` passed push CI run `35425575065` and pull-request CI run `35425829735` on that exact SHA. The implementation is present on current master `99642b9c416f4cac2dbcbc896688b9a54f015365`; fresh current-master CI/Android qualification is being observed before canonical checklist reconciliation.

## Checklist mapping

This evidence maps directly to all four RMD-408 requirements:

1. conflicting core/Android constants identified and the Android maximum removed;
2. one maximum is enforced by the core while the user/platform preference is bounded by it;
3. Android cannot request a value above the core-provided maximum;
4. Rust/FFI/JVM mapping tests cover the boundary behavior.

The canonical TODO remains the source of completion truth. Its RMD-408 checkboxes should be marked complete only after this reconciliation change itself passes exact-head qualification, merges to `master`, and the TODO is updated with the merged evidence.
