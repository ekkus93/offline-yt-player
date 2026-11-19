# Offline YT Player — Coverage Baseline and Test Inventory (2026-11-19)

**Source baseline examined:** `b46ba5c899eba4ae191afd4867d3762241f4a1d5` (GitHub `master` at inventory)  
**Track:** `docs/TEST_COVERAGE_EXPANSION_TODO_2026-11-19.md`  
**Coverage percentages:** **UNMEASURED** in the baseline; a green CI matrix is not a measured percentage.

## Existing qualification and timing

The exact preceding fully green candidate was `355a955dd5bfcc1eff15f1dead4923c2fe0ea56b`: all eight workflows completed successfully (GitHub Actions runs in `docs/ANDROID_CI_REVALIDATION_AND_A54_QA_2026-11-19.md`). The current documentation-only `b46ba5c899eba4ae191afd4867d3762241f4a1d5` began another eight-workflow matrix on 2026-11-19; its test status must be refreshed before any closeout claim.

Approximate *workflow* wall-clock durations measured from creation to completion on the previously successful `355a955...` commit (not isolated test durations):

| Workflow | Observed wall-clock duration |
| --- | ---: |
| CI | 6m 47s |
| Android smoke / API 29 | 37m 48s |
| Android FGS timeout / API 35 | 14m 22s |
| Supply chain | 4m 41s |
| CI evidence | 7s |
| Deterministic E2E fixture | 40s |
| Android cold start | 19m 54s |
| Android real-network E2E | 11m 48s |

Run-ID records: CI `44410812188`, smoke `44410812148`, FGS `44410812106`, supply `44410812076`, evidence `44410812065`, fixture `44410812000`, cold start `44410812150`, network `44410812056`. These durations justify keeping additional emulator/API tiers separate until stable.

## Test inventory and candidate missing behaviors

The table is **risk-based**. Existing examples are observed in repository code and workflow configuration, but an unexecuted branch cannot be declared uncovered until a coverage report or explicitly targeted test proves it.

| Area | Production implementation (selected modules) | Existing executable evidence | Follow-up hypothesis, not a measured gap |
| --- | --- | --- | --- |
| YouTube source | `core/src/{youtube,youtube_source,youtube_extract,ffi_source}.rs` | `youtube_source/tests.rs`, `youtube_source_fixture_tests.rs`, `youtube_extract.rs` tests; `AddResolverAvailabilityInstrumentedTest`, `GeneratedUniffiSourceGatewaySmokeTest` | Provider shape/error/status matrix, concurrent cancellation and duplicate UI action: COV-200 |
| Durable downloads | `core/src/{worker,persistence,startup_reconciliation,worker_pause}.rs`, `core/src/persistence/*` | `persistence/tests.rs`, startup reconciliation tests, process-death core tests, API 29 cold-start | File-backed failure injection across all transaction phases, multi-connection races: COV-300 |
| Android background | `app/src/main/java/com/ekkus/offlineytplayer/downloads/` scheduler/service/notification/reboot; `MainActivity.kt` | API 29 smoke, API 35 FGS timeout, real-network E2E, download notification and connectivity instrumentation | API 26/33/34 transitions, permission revocation, rapid connectivity changes: COV-400 |
| HTTP transfer | `core/src/{download,resume,resume_http,security}.rs`; `core/src/download/*` | `download/tests/transfer.rs`, `download/tests/resume_limits.rs` | Reproducible combinatorial range/validator invariants: COV-500 |
| Media3 playback | `PlaybackSessionService.kt`, playback UI/state, Rust library-playback gateway | `PlaybackSessionBehaviorInstrumentedTest`, `Rmd1502SplitAvOfflineInstrumentedTest`, `Rmd1503SubtitleOfflineInstrumentedTest`, cold-start playback | Audio-focus, service recreation and missing/corrupt local asset behavior: COV-600 |
| UniFFI boundary | `core/src/ffi*.rs`, Kotlin `coregateway/GeneratedUniffi*Gateway` | `GeneratedUniffiCoreGatewaySmokeTest`, `GeneratedUniffiSourceGatewaySmokeTest`, two ABI packaging checks | Typed error boundary matrix; arm64 native runtime (cross-build alone insufficient): COV-700 |

Rust production module inventory: `core/src/lib.rs` exports `asset_validation`, `concurrency`, `deletion`, `domain`, `download`, `durable_work`, `events`, nine `ffi_*` gateways plus `ffi`, `offline_assets`, `persistence`, `playback_position`, `resource_bounds`, `resume`, `resume_http`, `retry`, `security`, `source`, `startup_reconciliation`, `state`, `subtitle`, `thumbnail`, `worker`, `worker_pause`, `youtube`, `youtube_diagnostics`, `youtube_extract`, `youtube_source`. Unit tests also exist inline and in separate `*_tests.rs` and `*/tests/` modules.

Android application paths examined: `app/src/main/java/com/ekkus/offlineytplayer/` with `downloads/`, `playback/`, `ui/` and `coregateway/` responsibilities; `app/src/test/java/` JVM tests and `app/src/androidTest/java/` platform/Compose tests. This is a module-level mapping, **not an exhaustive class-by-class inventory**; COV-002/COV-003 should remain unchecked until the measured report identifies the exact source-file denominators and missing branches.

## Existing CI measurement limitations

- `.github/workflows/ci.yml`: `cargo fmt`, `cargo clippy`, `cargo test --workspace --all-features`, Gradle `lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest`. It did **not** produce numeric line/branch coverage at this reviewed baseline.
- `.github/workflows/android-smoke.yml`: API 29 x86_64 instrumentation, required-method evidence, and raster golden checks. Success shows behavior, not Kotlin instrumentation line coverage.
- `.github/workflows/android-fgs-timeout.yml` (API 35), `android-cold-start.yml` (API 29), `android-real-network-e2e.yml` (API 29): additional behavior, no instrumented coverage denominator.
- `tests/test_actions_artifact_tag_policy.py` enforces that ordinary master pushes cannot upload new coverage artifacts. Coverage totals can be published in GitHub job summary and logs without uploading raw binary reports.
- Hardware-only Samsung A54 OS/battery/peripheral effects remain **unverified** through CI.

## Measurement plan and status

1. Rust core: pinned `cargo-llvm-cov` + `llvm-tools-preview`, LCOV and executable-line denominator in `core/src`; report missing files as unmeasured. **Await first CI report**.
2. Kotlin JVM: version-pinned Kover (or validated equivalent) covering production Kotlin code and excluding generated bindings. **Unmeasured** until working job/report.
3. Android instrumentation: separate API 29 JaCoCo/AGP prototype required to distinguish device coverage from JVM tests. **Unmeasured**.
4. Proposed future thresholds (not CI gates): Rust 85% core line, critical durable state 90% line, Kotlin business logic 80% line. Do not enforce until baseline and stability are demonstrated.

## Updates

Add exact source SHA, CI run link, tool versions, measured line/branch totals and uncovered file/function list here *only after the reporting workflow runs*. Do not equate an installed tool with a validated coverage baseline.
