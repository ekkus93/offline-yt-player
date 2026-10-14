# RMD-1802 — Fail closed on symlinked managed storage roots (2026-10-14)

Independent Android production review found that `StorageSettingsManager.cleanup` already rejected symlinked or non-directory managed roots, but `summarize` did not. For a symlinked `filesRoot` or `cacheRoot`, the safe no-follow enumeration silently returned an empty list and displayed zero-byte totals, while destructive cleanup later failed. This presented misleading accounting and an enabled cleanup UI for an invalid root.

`StorageSettingsManager.summarize` now applies the same real-directory, no-follow root validation before enumerating either tree. The common guard is also used by `cleanup` to keep their ownership policy consistent. Host-JVM behavioral regression `StorageSettingsRuntimeConsumptionTest.storageSummaryRejectsSymlinkedManagedRootsInsteadOfReportingMisleadingZeros` constructs real external files, symlinked roots, and verifies both scans fail with `IOException` without modifying the external data. The existing real app-owned filesystem summary/cleanup test remains in place.

Implementation and host-JVM evidence are not final qualification. Run the exact-head CI matrix and keep RMD-1802/1803/1805 and global closeout unchecked until the full independent audit and Android qualification are complete. External service/legal approval remains separate.
