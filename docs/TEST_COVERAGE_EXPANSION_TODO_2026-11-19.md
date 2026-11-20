# Offline YT Player — Risk-Based Test Coverage Expansion TODO

**Created:** 2026-11-19  
**Status:** New independent workstream; every unchecked item requires implementation and evidence.  
**Spec:** `docs/TEST_COVERAGE_EXPANSION_SPEC_2026-11-19.md`  
**Baseline reviewed:** `e946a24b9939c03a7f77f667705a1f888ae44771`  
**Repository:** `ekkus93/offline-yt-player` (`master`)

## Execution and evidence rules

- Implement one independently actionable item at a time. Refresh `master` HEAD, affected implementation/tests, and exact-head CI before each change.
- Use Ralph Bridge for repository writes, CI and commits. Commit directly to `master` with exact-head compare-and-swap; do not create routine per-item branches or PRs.
- Read the full workstream and existing tests before implementation; extend existing tests where possible. Avoid repetitive source-string assertions or tests that only inspect constants.
- Every checked item must name **production code exercised**, **test file/method**, **exact commit SHA**, **CI run ID / tool output**, and what success proves. Add an evidence line below each section as it becomes qualified; do not fabricate results.
- Leave blocked device/API work unchecked with a reason. Do not collapse completed subtasks or mark a parent complete because CI happened to pass.
- CI runs on an old SHA do not qualify the current head. Do not bypass failing tests, existing smoke execution checks, golden raster checks, or artifact policy.
- Existing remediation TODO is a historical acceptance record; it remains untouched.

## COV-000 — Baseline, inventory and scope (P0)

- [x] **COV-001** Record exact `master` SHA and latest full CI matrix; distinguish pre-existing qualifications from this track.
- [x] **COV-002** Inventory production Rust modules (`core/src`) and Android packages (`app/src/main`) and map current unit, fixture, integration and instrumentation tests.
- [ ] **COV-003** Map highest-risk uncovered *functions/branches*, not just file count, to COV-200–700. Explicitly note tests that already cover an edge case.
- [x] **COV-004** Document baseline CI job durations, test stability and artifact-upload restrictions before expanding emulator jobs.
- [x] **COV-005** Create `docs/TEST_COVERAGE_BASELINE_2026-11-19.md` (or a dated successor if run later), listing unknown metrics as **unmeasured** until actual reports exist.

**Acceptance:** committed inventory traces at least one measured or explicitly unmeasured finding to each risk area; no invented percentages.

**Evidence (COV-001/002/004/005):** `docs/TEST_COVERAGE_BASELINE_2026-11-19.md` inventories `core/src/lib.rs` Rust modules, Android packages and representative Rust/JVM/instrumentation tests; captures prior exact-head fully green SHA `355a955dd5bfcc1eff15f1dead4923c2fe0ea56b` and 8 workflow IDs/durations; and documents the tag-only artifact policy. Baseline created at `f590cc1f3a883b7fb358e291a5998f4891609dc4`, with numeric measurements verified at `af38f8ffc31a27d6edd7111800f3a32c3fd1280e` (Rust run `44501336363`, main CI `44501336322`). This is module/package inventory, not complete per-function branch inventory; COV-003 remains open.

## COV-100 — Coverage measurement infrastructure (P0; implement first)

- [x] **COV-101** Pin `cargo-llvm-cov` and `llvm-tools-preview` compatible with the repository's Rust toolchain. Capture version and command in CI.
- [x] **COV-102** Add an isolated, reproducible Rust coverage job/script exercising the workspace; generate LCOV plus readable core-only line totals and supported region/branch metrics.
- [x] **COV-103** Confirm Rust denominator covers actual `core/src` logic, excluding tests, generated UniFFI bindings and external crates without silently hiding real source modules.
- [x] **COV-104** Evaluate/pin compatible Kotlin JVM coverage instrumentation (Kover or justified equivalent); wire `testDebugUnitTest` and produce validated machine-readable per-package results.
- [x] **COV-105** Confirm Android/JVM denominator excludes generated bindings/test code and includes key production `downloads`, UI-state and gateway logic.
- [x] **COV-106** Prototype Android instrumentation coverage on API 29 in a *separate* lane, verifying counters for a known app production path; if incompatible, document blocker and keep task open.
- [x] **COV-107** Publish bounded Rust/JVM totals with exact SHA and tool versions in logs and `GITHUB_STEP_SUMMARY`; preserve tag-only artifact uploads except existing narrowly permitted cases.
- [x] **COV-108** Add host-side tests validating coverage report parsing/denominators; missing/empty reports must be reported as errors or `unmeasured`, never 100%.
- [ ] **COV-109** Record measured baseline per component/package/function. Keep thresholds **report-only** initially and document proposed (not enforced) Rust 85%, critical state 90%, Kotlin business logic 80% line goals.
- [x] **COV-110** Run exact-head standard CI plus coverage lane; record separate per-workflow evidence and verify no unauthorized artifact uploads or altered existing smoke acceptance.

**Acceptance:** repeatable numeric Rust and JVM totals from production code, distinct Android instrumentation status, exact-SHA evidence, and no fake green report on missing data.

**Evidence (COV-101/102/103/104/105/107/108):** `.github/workflows/coverage-rust.yml`, `scripts/summarize_rust_coverage.py`, `tests/test_summarize_rust_coverage.py`, `app/build.gradle.kts` pinned Kover, `scripts/summarize_kotlin_coverage.py`, `tests/test_summarize_kotlin_coverage.py` and `.github/workflows/ci.yml`. At exact `af38f8ffc31a27d6edd7111800f3a32c3fd1280e`, Rust coverage run `44501336363` completed with **8,125/9,129 = 89.00%** core/src measured lines (45 source files; branch coverage **unmeasured**). Main CI `44501336322` passed including coverage-parser tests and Kover Gradle task; Kotlin source packages `com.ekkus.offlineytplayer` excluding generated UniFFI `core` returned **1,435/3,796 = 37.80%** JVM source lines and **906/2,952 = 30.69%** branches. Both summaries print exact SHA and tool versions in the job output and summary; no new master artifacts. Detailed package breakdown in the baseline. COV-106/109/110 remain unchecked (instrumentation measurement, function-level classification, and full CI).

**Evidence (COV-106/110, validated 2026-11-20 UTC):** Exact-head `1534875022dc3273bae07af9a5b423c4e2e8ad2e` passed all **11/11** required GitHub Actions workflows, including main CI `44646351095`, Rust coverage `44646351133`, API 29 Android JaCoCo coverage `44646351080` (attempt 2), API matrix `44646351120` (attempt 2), Android smoke `44646351074`, API 35 FGS timeout `44646351114`, cold start `44646351058`, OS network E2E `44646351039`, supply chain `44646351067`, deterministic fixture `44646351134` and CI evidence `44646351287`. Production `ui/AppShell.kt` and `ui/AddWorkflowPolicy.kt` were actually covered through `AddResolverAvailabilityInstrumentedTest` on an API 29 emulator; `scripts/assert_android_smoke_execution.py` required real executed class/method XML, and `summarize_android_instrumentation_coverage.py` validated nonzero JaCoCo production UI counters. Android on-emulator **493/4,139 = 11.91%** lines, **188/2,972** branches; Rust `core/src` **8,415/9,412 = 89.41%** lines (branch unmeasured), and JVM Kotlin **1,433/3,799 = 37.72%** lines and **904/2,954 = 30.60%** branches (separate denominators). `tests/test_actions_artifact_tag_policy.py` remained in passing governance; coverage workflows do not upload artifacts on ordinary master pushes. COV-109 remains open because per-*function* measurements are not yet trustworthy; do not enforce thresholds.

## COV-200 — YouTube source and Analyze resiliency (P0)

- [x] **COV-201** Extend sanitized `youtube_source` / `youtube_extract` fixtures for missing/null/mismatched, duplicated and reordered provider fields, with exact valid/invalid outcomes.
- [ ] **COV-202** Add 403/404/429/5xx and truncated body fixture tests; verify typed permanent/retryable classification, timeouts, bounds and no erroneous `Unavailable` success.
- [ ] **COV-203** Add option-ID fidelity tests across duplicate quality labels, split A/V, audio and subtitle choices, including provider-neutral download plan mapping.
- [ ] **COV-204** Exercise source resolution canceled before start and during controlled simulated blocking I/O; assert no stuck worker, duplicate request or false completion.
- [ ] **COV-205** Extend Android Add/Analyze behavioral tests for plain/share/query-parameter URLs, native initialization error visibility, duplicate Analyze taps and typed error recovery.
- [ ] **COV-206** Add adversarial malformed/oversized metadata and signed-query-token fixtures; prove bounded output and error/log redaction through FFI/UI.
- [ ] **COV-207** Run target Rust tests, exact-head API 29 Android smoke/required-method evidence and overall CI; note live-source/hardware exclusions separately.

**Acceptance:** deterministic fixture tests prove parser/adapter/FFI/UI behaviors without live YouTube dependency; original Analyze regressions remain covered.

**Evidence:** _pending._

**Evidence (COV-201):** Production `core/src/youtube_source/parse.rs::{parse_player,parse_stream}` and `core/src/youtube_source/tests.rs::{provider_field_shape_matrix_fails_closed_without_false_metadata,rejects_duplicate_provider_format_ids_instead_of_selecting_arbitrary_url,provider_stream_reordering_preserves_media_id_and_stream_attributes}` reject missing/wrong-shaped, duplicated and reordered sanitized provider data without live external requests. Exact-head Rust LCOV workflow `44505561276` at `a31ea408bbd5dfe01ed5684372c82f5e525214d5` passed, executing `cargo test --workspace --all-features`; deterministic fixture run `44505561262` also passed. Remaining COV-200 acceptance is unchecked.

## COV-300 — SQLite durability, worker races and startup repair (P0)

- [ ] **COV-301** Add file-backed SQLite transaction fault tests for enqueue, claim, stage, promote, cancellation, and completion boundaries, including reopen/recovery assertions.
- [ ] **COV-302** Cover future schema version, malformed/corrupt DB, foreign-key/orphan data and failed migration; verify fail-closed errors and no destructive fallback.
- [x] **COV-303** Exercise concurrent independent DB connections/workers racing to claim and settle the same job; verify a single authoritative winner.
- [ ] **COV-304** Add cancel-vs-complete, pause-vs-retry, stale worker, and duplicate schedule races without nondeterministic sleeps; use barriers or test-controlled clocks.
- [ ] **COV-305** Add repeated startup reconciliation/partial-staged-file tests, including missing assets, symlinks, stale lease and crash-between-checkpoint boundaries; prove idempotence and outside-root safety.
- [x] **COV-306** Add a bundled-SQLite upgrade regression smoke for `rusqlite 0.41.x`: file open/migrate, FK enforcement, transaction, snapshot persisted after reopen.
- [ ] **COV-307** Run new Rust tests under normal CI and coverage; document observed durable-state invariant and exact-head CI.

**Acceptance:** no double promotion/completion, silent state loss or recovery escape; all fault injections are test-only.

**Evidence (COV-306):** `core/src/persistence/tests.rs::bundled_sqlite_file_reopen_preserves_durable_queue_and_foreign_keys` exercises `LibraryStore::open/migrate/save_download_snapshot/stage_asset/promote_completed` against a file-backed bundled SQLite database, reopens and checks saved queue/library, verifies `PRAGMA foreign_keys=ON` and rejects an orphan `assets` insert. The test is included in the passing Rust coverage lane `44501336363` at `af38f8ffc31a27d6edd7111800f3a32c3fd1280e` and normal Rust CI `44501336322`; no arbitrary fixture claims about other migration versions.

**Evidence (COV-303):** Production `LibraryStore` snapshot compare-and-swap and atomic promotion in `core/src/persistence/{snapshots}.rs` and `core/src/persistence.rs` are exercised by `core/src/persistence/tests.rs::{independent_sqlite_worker_connections_race_for_one_authoritative_claim,independent_sqlite_completion_and_cancel_race_never_promotes_canceled_item}` with two independent connections and synchronization barriers, requiring exactly one claim or terminal transition and library/staging consistency. Exact-head Rust coverage `44505561276` and deterministic fixture `44505561262` passed at `a31ea408bbd5dfe01ed5684372c82f5e525214d5`. Other fault-phase and job-scheduling races remain open.

## COV-400 — Android service, notification and API-level matrix (P1)

- [x] **COV-401** Audit existing API 29/35 tests, and establish feasible API 26/33/34 emulator profiles and bounded execution strategy.
- [ ] **COV-402** Add API 26-33 foreground fallback and API 34+ UIDT scheduling behavior tests; assert actual scheduling inputs and failure/restriction cases.
- [ ] **COV-403** Add notification-permission granted/denied/revoked and repeated pause/resume/cancel notification-action tests backed by durable queue state.
- [ ] **COV-404** Add process/service recreation and timeout callback tests ensuring legal teardown, noncancel platform stop, no duplicate worker and recovery ordering.
- [ ] **COV-405** Extend host-controlled network E2E for quick offline/online and metered/unmetered transitions; verify Wi-Fi-only classification and exactly-once resume behavior.
- [ ] **COV-406** Cover reboot-before-unlock restrictions and no direct boot-time dataSync FGS launch on applicable APIs, with an executable behavioral test.
- [x] **COV-407** Execute selected API 26, 33, 34 and existing 29/35 runs; record actual APIs exercised, links, skipped/unavailable tiers and flaky retry evidence.
- [ ] **COV-408** Retain hardware-only A54 battery/network behavior as separately documented pending device validation; do not check it as automatically passed.

**Acceptance:** executable platform behavior checked at named API levels; existing workflows remain required and intact.

**Evidence:** _pending._

**Evidence (COV-401/407, validated 2026-11-20 UTC):** `.github/workflows/android-api-matrix.yml` and `scripts/run_android_api_matrix.sh` invoke and require executed XML for four native/runtime/timeout/notification classes **separately** at APIs **26, 33 and 34**. All three matrix jobs passed on exact SHA `1534875022dc3273bae07af9a5b423c4e2e8ad2e`, Actions run `44646351120` **attempt 2** (API 26 retry followed Maven Central HTTP 429 for Gradle's JUnit Platform dependencies, not an assertion failure). Existing API 29 Android smoke `44646351074`, cold start `44646351058` and real OS-network `44646351039` passed, as did API 35 FGS timeout `44646351114`. Emulator results do not prove an actual Samsung A54 battery/firmware behavior, arm64 runtime, or every scheduling restriction on every API tier. COV-402/403/404/405/406/408 remain open for their full behavioral scope.

**Partial evidence (COV-405; remains unchecked):** Production `DownloadForegroundService.handleConnectivityChange` now requests a cooperative native stop on a durable connectivity pause, and the real API 29 host-controlled `Rmd1506ConnectivityE2EInstrumentedTest` passed both offline→online restore and unmetered→metered→unmetered Wi-Fi-only modes (`WIFI_ONLY_METERED_QUALIFIED`) in run `44646351039`. Extend rapid multi-transition and exactly-once resume assertions before checking the entire COV-405 requirement.

## COV-500 — HTTP resume, integrity and filesystem adversaries (P1)

- [x] **COV-501** Inventory `core/src/download/tests/transfer.rs` and `resume_limits.rs`; avoid re-adding existing 200/206/416, ENOSPC and body-disconnect cases.
- [ ] **COV-502** Add reproducible bounded property tests over Range/Content-Range, validators, content length and chunked/truncated response combinations using a local HTTP stub.
- [x] **COV-503** Assert unsafe combinations always restart or fail (never append unverifiable bytes); output failing seed/input for replay.
- [ ] **COV-504** Inject mid-write disk failures, cancellation and malformed resume metadata; verify retained/removed partials match policy and no accepted corrupt SHA/length.
- [ ] **COV-505** Check no path traversal, symlink escape, excessive writes or unbounded memory from generated inputs; retain minimized adversarial fixtures.
- [ ] **COV-506** Run deterministic tests repeatedly in CI; record coverage delta, exact-head Rust job and accepted invariants.

**Acceptance:** zero accepted corrupt/resumed outputs across bounded adversarial cases; reproducible seeds in failures.

**Evidence (COV-501):** `core/src/download/tests/{transfer,resume_limits}.rs` and `docs/TEST_COVERAGE_BASELINE_2026-11-19.md` inventory existing 200/206/416, validator/restart, mid-body disconnect, ENOSPC and size-limit tests before designing new property tests. Inspected at `b46ba5c899eba4ae191afd4867d3762241f4a1d5`; relevant existing tests passed as part of the exact-head Rust coverage run `44501336363` at `af38f8ffc31a27d6edd7111800f3a32c3fd1280e`. COV-502 through COV-506 still need implementation.

**Evidence (COV-503):** `core/src/resume_http.rs::{seeded_remote_validator_matrix_never_appends_unproven_partial_bytes,malformed_content_ranges_never_report_a_trustworthy_total}` exercise production representation extraction, `ResumeRepresentation::matches`, sidecar saving and `prepare_partial_reuse` across 128 repeatable validator/size combinations and invalid Content-Range values. Failures include seed/case data. Rust coverage `44505561276` and deterministic fixture E2E `44505561262` passed at `a31ea408bbd5dfe01ed5684372c82f5e525214d5`. The broader HTTP 200/206/416 property matrix and injected filesystem failures remain unchecked.

## COV-600 — Media3 local playback lifecycle (P1)

- [ ] **COV-601** Inventory existing playback unit/instrumented tests and prepare small deterministic combined A/V, split A/V and subtitle assets.
- [ ] **COV-602** Add real Media3 `PlaybackSessionService` instrumentation for bind/unbind and destruction/recreation, ensuring one canonical playback session and no leaks.
- [ ] **COV-603** Exercise audio-focus loss/gain, controller disconnect, rapid play/pause/seek and saved-position restoration after lifecycle/process restart.
- [ ] **COV-604** Test missing, truncated and corrupt local media/subtitles: user-visible error, no unintended network access or false completed state.
- [ ] **COV-605** Test selected local audio/subtitle track switching and representative media-button/notification behavior without relying on external providers.
- [ ] **COV-606** Run exact-head playback instrumented smoke, golden/layout tests if impacted, and standard CI; record limitations of emulator audio routing.

**Acceptance:** production Media3 session behavior is exercised by deterministic offline assets and retained across supported lifecycle transitions.

**Evidence:** _pending._

## COV-700 — UniFFI/native boundary correctness (P1)

- [ ] **COV-701** Inventory `core/src/ffi*.rs` and `GeneratedUniffi*Gateway` bindings, supported error variants, nullable/unsigned fields, cancellation and cleanup paths.
- [ ] **COV-702** Test FFI error conversion and edge-value round trips including zero/max unsigned, null fields, large bounded strings and malformed inputs; forbid empty-success coercion.
- [ ] **COV-703** Add repeated open/close and pre/in-flight cancellation race tests through the production gateway/native-loading path.
- [x] **COV-704** Prove `liboffline_yt_core.so` + JNA packaging for both arm64-v8a and x86_64 and runtime tests on x86_64 emulator.
- [ ] **COV-705** Execute arm64 runtime smoke on emulator/hardware if feasible; otherwise record this as an explicit unverified runtime-ABI gap, distinct from cross-build.
- [ ] **COV-706** Validate safe diagnostic redaction through the boundary and Android UI; run exact-head generated-binding/ABI build and Android smoke.

**Acceptance:** typed errors/cancel/cleanup survive boundary crossings, without fabricated data, token leakage or false arm64 runtime claims.

**Evidence:** _pending._

**Evidence (COV-704, validated 2026-11-20 UTC):** `.github/workflows/ci.yml` job `UniFFI Kotlin and Android ABI` (`44646351095`) built and verified both `arm64-v8a` and `x86_64` Rust core/JNA packaging; on the same SHA API 29 smoke (`44646351074`) and API 26/33/34 matrix (`44646351120`) executed `GeneratedUniffiSourceGatewaySmokeTest::repeatedNativeResolverOpenCloseAndPreCanceledRequestsDoNotLeakState` through packaged native `FfiYouTubeSourceService` and `GeneratedUniffiSourceAnalysisGateway` on x86_64. **Arm64 cross-build/packaging is not arm64 runtime qualification**; COV-705 remains open.

## COV-800 — Final evidence and gate policy (P2, after implementation)

- [ ] **COV-801** Recompute actual per-module Rust and Kotlin coverage and compare with COV-100 baseline; report line/branch deltas without conflating languages.
- [ ] **COV-802** Map each added test to risk, executed production path, SHA and CI run, and identify remaining high-risk uncovered branches.
- [ ] **COV-803** Assess whether to enforce a narrow nonregression threshold on stable Rust/JVM packages; **do not** enable it until baseline, stability and developer approval are documented.
- [x] **COV-804** Run full required exact-head CI including Android smoke, FGS timeout, supply chain, deterministic E2E, cold-start and real-network E2E; failures must be fixed or remain open.
- [ ] **COV-805** Verify tag-only artifact policy still passes, no live-source dependency exists in mandatory lanes, and no security/privacy exceptions were broadened.
- [ ] **COV-806** Publish a dated coverage closeout note with report locations, tool versions, CI run IDs, confirmed gaps and any pending Samsung A54/human release gates.
- [ ] **COV-807** Check this task only after all remaining actionable subtasks are verified; do not mark the initiative done if device/API or measurement requirements are still blocked.

**Acceptance:** measured, reproducible coverage baseline and improvement, behavioral proof with exact-head CI, and honest residual-risk reporting.

**Evidence:** _pending._

**Evidence (COV-804, latest fully green implementation SHA):** All 11 workflows passed at `1534875022dc3273bae07af9a5b423c4e2e8ad2e`; individual run IDs and attempt numbers are in COV-106/110 above. This only proves that implementation commit; subsequent changes require fresh CI. Do **not** treat COV-804 as final initiative closeout or mark COV-807 complete.

---

## Recommended implementation order

COV-000 → COV-100 → COV-200 → COV-300 → COV-500 → COV-400 → COV-600 → COV-700 → COV-800.

This is a priority recommendation, not permission to skip independent work or mark tasks complete without test execution. If an emulator, arm64 runtime or external dependency is genuinely blocked, document the blocker and continue with independent Rust/JVM work.

## Completion record

- **Current baseline SHA at document creation:** `e946a24b9939c03a7f77f667705a1f888ae44771`.
- **Implemented subtasks:** COV-001, 002, 004, 005, 101, 102, 103, 104, 105, 107, 108, 306, 501; COV-201, 303, 503; remaining tasks retain unchecked status.
- **Coverage percentages (measured at `af38f8ffc31a27d6edd7111800f3a32c3fd1280e`):** Rust core 89.00% lines (branch unmeasured); Kotlin/JVM production code 37.80% lines and 30.69% branches; Android instrumentation still unmeasured.
- **CI evidence for new test changes:** Rust coverage `44501336363` and main CI `44501336322` passed at exact `af38f8ffc31a27d6edd7111800f3a32c3fd1280e`; other workflow statuses require recheck on the next head.
