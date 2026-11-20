# Offline YT Player coverage expansion — Ralph handoff (2026-11-20)

**Repo:** `ekkus93/offline-yt-player` on `master`.
**Canonical TODO:** `docs/TEST_COVERAGE_EXPANSION_TODO_2026-11-19.md` (do not substitute historical remediation TODO).
**Latest implementation SHA reviewed:** `bfba51ec3a3f5807670e2bf1f895ec6f8519b17d`.
**Policy:** Ralph Bridge **exclusively** for repository/CI reads, CAS commits to the exact current `master` HEAD; no task branches/PRs. Refresh full TODO, head and exact-head CI before changing code.

## Implemented this loop

1. `core/src/persistence/tests.rs`: failed v0 migration DDL rollback preserves original data and no partial tables/index; malformed download snapshot remains present across reopen. COV-302.
2. `core/src/persistence/tests.rs`: deterministic SQL-trigger write failures at enqueue, claim, staging, cancellation and completion, with reopen and recovery; existing promotion rollback test completes COV-301.
3. `core/src/persistence/tests.rs`: barrier-synchronized pause-vs-retry CAS, three concurrent duplicate schedule inserts, plus existing cancel-vs-complete and stale recovery tests; COV-304.
4. `core/src/startup_reconciliation.rs`: repeated real file-backed crash checkpoint recovery, preserving queued/paused/retry-wait state and sidecars; COV-305 has remaining explicit stale-lease/filesystem move scope **unchecked**.
5. `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/AddResolverAvailabilityInstrumentedTest.kt`: plain/shared watch URL normalization, privacy-safe typed timeout UI and retry-to-success without duplicate submissions. **COV-205/206 pending device-side test execution**.
6. `core/src/ffi.rs` and `core/src/ffi_source.rs`: zero/null/max unsigned, bounded title text, malformed and signed-query source errors, no empty-success record; plus `docs/UNIFFI_NATIVE_BOUNDARY_COVERAGE_INVENTORY_2026-11-20.md`; COV-701/702.

## Exact-head qualification

Rust fmt, clippy, `cargo test --workspace --all-features` in main CI **44726100035**: passing Rust job. Rust LCOV **44726100060**: passing; production `core/src` **8,688 / 9,584 = 90.65% lines**, LLVM LCOV branch counters **unmeasured**. CI evidence workflow **44726100177** passed; deterministic fixture workflow **44726100154** passed. **Check fresh exact-head CI before claiming other jobs passed.** Android emulator smoke and JaCoCo, API matrix, cold-start, FGS and real network were not yet qualified at authoring; do not invent results. A subsequent documentation-only CAS commit changes HEAD and starts new workflows.

## Remaining priority work

- Finish COV-205/206/207 with verified API 29 instrumentation test XML and full CI; fix any actual Android assertion or build failure.
- Finish COV-305 by testing a genuine stale worker lease/crash-between-move point, not only staged metadata.
- COV-502/504/505/506: HTTP Range/validator/body property combinations, disk-write corruption and path-safety adversarial fixtures.
- COV-402/403/404/405/406: Android API 26/33/34 scheduling cases, notification controls, service/reboot constraints and rapid OS network changes.
- COV-601–606: deterministic Media3 lifecycle, corruption/audio-focus/track-switching.
- COV-703/705/706: native Android in-flight cancellation and redaction, **arm64 runtime verification**; x86_64 CI plus arm64 cross-build does not qualify arm64 runtime.
- COV-003/109/802: actual per-function/branch evidence, distinguish raw compiler-mangled Rust FNDA symbols from named source functions. COV-806/807 remain open until all acceptance gates have evidence.
- Hardware Samsung Galaxy A54 and human release authorization remain user/device-dependent.

## Security and test discipline

Keep tag-only artifact policy, no live YouTube dependency in mandatory test jobs, no secrets/logged URLs, preserve native JNA ABI checks, do not weaken Golden/layout tests or smoke required-method assertions. CI dependency fetch failures (Maven/JUnit/JaCoCo) must be distinguished from test assertion failures and only retried on exact SHA.

**User assistance needed eventually:** access to Samsung Galaxy A54 and, for arm64 runtime if no suitable arm64 runner/emulator is available, physical-device execution. Proceed with independent software tasks without stalling on hardware.
