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

## Measured coverage results and exact-SHA evidence

These **separate denominators** were produced by passing jobs on exact SHA
`af38f8ffc31a27d6edd7111800f3a32c3fd1280e`:

| Lane | Covered / measured production lines | Line % | Covered / measured branches | Evidence |
| --- | ---: | ---: | ---: | --- |
| Rust core (`core/src`) | 8,125 / 9,129 | **89.00%** | Unmeasured (LLVM LCOV has no BRDA data) | [Rust coverage run 44501336363](https://github.com/ekkus93/offline-yt-player/actions/runs/44501336363) |
| Kotlin JVM business logic | 1,435 / 3,796 | **37.80%** | 906 / 2,952 (**30.69%**) | [CI / Kover run 44501336322](https://github.com/ekkus93/offline-yt-player/actions/runs/44501336322) |
| Android on-device instrumentation | **Unmeasured** | — | **Unmeasured** | Pending COV-106; API 29 functional smoke is not line coverage |

Kotlin production package line coverage from Kover 0.9.11 (XML and JSON validated; 
Kotlin tests only):

| Package | Covered / measured lines |
| --- | ---: |
| `com.ekkus.offlineytplayer.ui` | 272 / 1,347 (20.19%) |
| `com.ekkus.offlineytplayer.coregateway` | 335 / 868 (38.59%) |
| `com.ekkus.offlineytplayer.downloads` | 378 / 793 (47.67%) |
| `com.ekkus.offlineytplayer` | 223 / 406 (54.93%) |
| `com.ekkus.offlineytplayer.playback` | 101 / 177 (57.06%) |
| `com.ekkus.offlineytplayer.settings` | 79 / 155 (50.97%) |
| `com.ekkus.offlineytplayer.resilience` | 47 / 50 (94.00%) |

Initial Rust baseline (before loopback HTTP/SQLite tests): `f590cc1f3a883b7fb358e291a5998f4891609dc4`,
[Rust coverage run 44499021060](https://github.com/ekkus93/offline-yt-player/actions/runs/44499021060),
8,090 / 9,123 = **88.68%**; 45 measured files. The subsequent 89.00% result
measures the same scoped production Rust core after targeted regression tests.

Largest measured Rust uncovered *files* in the 89.00% run (not function
coverage):

| File | Covered / measured executable lines |
| --- | ---: |
| `core/src/youtube_source.rs` | 91 / 282 |
| `core/src/worker.rs` | 422 / 492 |
| `core/src/ffi_download_control.rs` | Reported in LCOV; detailed function gaps not yet classified |
| `core/src/persistence.rs` | Reported in LCOV; detailed function gaps not yet classified |

The original `youtube_source.rs` measurement was 61/276; the local-watch
HTTP tests and 408/429 retryability change improved it to 91/282 on the
qualified commit. File-level totals and Kotlin package-level totals are a
prioritization aid, **not** proof that every unexercised branch has been mapped.

**Validated tool versions / execution:** Rust 1.98.1; `cargo-llvm-cov 0.9.1`;
`llvm-tools-preview`; `cargo llvm-cov --workspace --all-features --lcov
--output-path coverage/rust.lcov`; Kover Gradle Plugin 0.9.11,
`./gradlew :app:koverXmlReportDebug`; `scripts/summarize_rust_coverage.py` and
`scripts/summarize_kotlin_coverage.py`. Host-side parser tests reject
malformed/empty reports and exclude test/generated source packages. They
passed as part of the Rust coverage and CI governance jobs on the stated SHA.
No master-push artifact was uploaded by these coverage jobs.

**Remaining qualification on this SHA:** At the time of this checkpoint the
API 29 smoke, API 35 FGS, cold-start, and real-network E2E runs were still
pending. The main CI, Rust coverage, supply-chain, CI evidence, and
deterministic fixture workflows passed. Recheck *all* workflows on the
latest HEAD before checking COV-110 or COV-804.


## Additional exact-commit regression coverage checkpoint

At implementation SHA `a31ea408bbd5dfe01ed5684372c82f5e525214d5`, Rust LCOV workflow [44505561276](https://github.com/ekkus93/offline-yt-player/actions/runs/44505561276) passed:

- Rust scoped production executable lines: **8,200 / 9,204 = 89.09%**, across 45 measured files.
- Raw LLVM function symbols: **895 / 1,506 = 59.43%**. **These are not a reliable count of distinct named Rust source functions**: the LCOV FNDA identifiers are compiler-mangled and may represent repeated instantiations and closures. Do not enforce this as a function threshold until symbols are demangled/deduplicated and source methods mapped to risks.
- Branch coverage remains **unmeasured** by this LCOV mode.
- Tests added for missing/wrong-shaped/duplicate provider IDs, 408/429 retry classification, file-backed SQLite rollback and two connection races, malformed Content-Range parsing and seeded sidecar validator rules.
- Deterministic fixture E2E [44505561262](https://github.com/ekkus93/offline-yt-player/actions/runs/44505561262) also passed.
- The Android workflow matrix and Kover remeasurement at this exact SHA were still running at checkpoint time; an earlier 37.80% Kotlin/JVM baseline remains the last verified measurement. **Do not infer final 9/9 success**.

The coverage TODO checks COV-201, 303 and 503 based on their individual Rust tests; tasks requiring broader provider error cases, API matrix and on-device instrumentation remain open.

## Verified 11-workflow exact-head checkpoint (2026-11-20 UTC)

At SHA `1534875022dc3273bae07af9a5b423c4e2e8ad2e`, **all 11/11** CI workflows were successful. This is a distinct later measurement from the historical baseline above; it supersedes older “unmeasured” statements **only for these exact reports**, without implying unmeasured per-function Rust or arm64 runtime coverage.

| Measured lane | Covered / total lines | Line rate | Covered / total branches | Exact-head workflow |
| --- | ---: | ---: | ---: | --- |
| Rust core (LLVM LCOV 0.9.1) | 8,415 / 9,412 | **89.41%** | Unmeasured | `44646351133` |
| Kotlin JVM (Kover 0.9.11) | 1,433 / 3,799 | **37.72%** | 904 / 2,954 (**30.60%**) | `44646351095` |
| Android API 29 instrumented (AGP JaCoCo XML) | 493 / 4,139 | **11.91%** | 188 / 2,972 (**6.33%**) | `44646351080`, attempt 2 |

AGP report path `app/build/reports/coverage/androidTest/debug/connected/report.xml`; `summarize_android_instrumentation_coverage.py` validated actual hits in `AppShell.kt`, `AddWorkflowPolicy.kt`, `DownloadSetupModels.kt`, `LibraryDownloads.kt`, `Theme.kt`. The Android report executes `AddResolverAvailabilityInstrumentedTest`, does **not** include every app runtime path, and must not be added to the JVM Kover denominator. Kotlin JVM measured `ui` at 272/1347 and `downloads` at 376/796, and `coregateway` at 335/868. Main CI `44646351095` and all required emulator tests passed; see the coverage TODO evidence for individual run IDs. Tag-only artifact rules remain intact. Proposed Rust 85% / critical state 90% / Kotlin 80% goals are **not enforced**; module/function classification and low Kotlin coverage remain open.

## Risk-ranked function and branch follow-up (2026-11-20 UTC; COV-003 partial)

Measured at source SHA `e408880d1719b55026dd35c84a98db5cf7900d52` by Rust coverage workflow `44676046105` and main CI/Kover `44676046018`. **LLVM LCOV has no BRDA branch counters**, and Rust compiler-mangled FNDA symbols include instantiations and closures; a missing raw symbol must not be presented as proof an entire named method is untested. These are highest-risk *specific function/branch targets*, with the actual existing tests called out, not invented uncovered percentages:

| Priority / production target | Measurement and uncovered behavior to qualify | Existing exercises; next TODO |
| --- | --- | --- |
| P0 `youtube_source.rs::fetch_innertube_android`, `fetch_watch_page`, `download_plan_with_options` | File 185/298; watch status/timeout and sanitized parser paths are covered. Fixed-endpoint Innertube HTTP failure/retry path and changed format identity on *fresh* stream refresh remain unproven; Rust branches unmeasured. | `watch_page_http_status_classifies_retryability_without_leaking_body`, `watch_page_silent_server_times_out_without_exposing_provider_details`, `split_audio_and_subtitle_track_ids_map_to_fresh_provider_neutral_assets`; COV-200/206. |
| P0 `ffi_download_control.rs::{transition_inner,retry_inner,save_if_unchanged}` | File 289/381; file-backed stale-control and CAS races covered; phase-specific rollback, recovery and duplicate scheduling still require controlled failure injection. | `stale_control_cannot_overwrite_a_concurrent_pause_or_completion`, `stale_retry_cannot_reset_an_already_reclaimed_job`; COV-301/304. |
| P0 `worker.rs::{execute_ready_with_platform_stop_at,repair_interrupted_claims_at}` | File 422/492; stop/pause and startup reconciliation scenarios have tests, but crash between staged-file checkpoints, retry races and forced disk failures still lack complete phase matrix. | `Rmd1506ConnectivityE2EInstrumentedTest`, `independent_sqlite_worker_connections_race_for_one_authoritative_claim`; COV-301/305/504. |
| P1 `asset_validation.rs::validation_io_error` and `DownloadConcurrencyGate::try_acquire` | `validation_io_error` appears among raw unexecuted LCOV FNDA symbols; `try_acquire` appears with other compiler-generated symbol variants, **not** proof it lacks test coverage. Exercise permission-denied read and exact bounded concurrent acquisition without relying on symbol names. | `deep_validation_detects_same_length_corruption_using_stored_hash`, `symlinked_file_and_parent_never_validate_external_assets_as_healthy`, `try_acquire_never_exceeds_limit_and_drop_releases_capacity`; COV-500/300. |
| P0 Kotlin `ui` Add/Analyze and `coregateway` resolver/error boundary | JVM packages respectively 272/1347 and 335/868; branch-level uncovered functions not identified by present report. API 29 JaCoCo measures limited UI paths but not complete typed error recovery or rapid Analyze cycles. | `AddResolverAvailabilityInstrumentedTest` and `GeneratedUniffiSourceGatewaySmokeTest`; COV-205/206/700. |
| P1 Android foreground service and playback | JVM `downloads` 376/796; real connectivity loss/recovery now passes. Rapid flapping, notification revocation, reboot restriction and Media3 focus/session lifecycle remain specific missing cases, not safely inferred from an aggregate denominator. | `Rmd1506ConnectivityE2EInstrumentedTest`, API 26/33/34 matrix, `PlaybackSessionBehaviorInstrumentedTest`; COV-400/600. |

COV-003 remains **unchecked** pending a trustworthy line/function/branch-to-test reconciliation beyond this risk-ranked shortlist; COV-109 remains unchecked for the same function-level measurement limitation. This protects against declaring an unmeasured branch covered or uncovered without actual instrumentation.

## Updates

Add exact source SHA, CI run link, tool versions, measured line/branch totals and uncovered file/function list here *only after the reporting workflow runs*. Do not equate an installed tool with a validated coverage baseline.
