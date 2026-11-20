# Offline YT Player — Risk-Based Test Coverage Expansion TODO

**Created:** 2026-11-19  
**Status:** In progress; checked items have exact-commit evidence, and every unchecked item remains actionable or requires documented external validation.  
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
- [x] **COV-202** Add 403/404/429/5xx and truncated body fixture tests; verify typed permanent/retryable classification, timeouts, bounds and no erroneous `Unavailable` success.
- [x] **COV-203** Add option-ID fidelity tests across duplicate quality labels, split A/V, audio and subtitle choices, including provider-neutral download plan mapping.
- [x] **COV-204** Exercise source resolution canceled before start and during controlled simulated blocking I/O; assert no stuck worker, duplicate request or false completion.
- [x] **COV-205** Extend Android Add/Analyze behavioral tests for plain/share/query-parameter URLs, native initialization error visibility, duplicate Analyze taps and typed error recovery.
- [x] **COV-206** Add adversarial malformed/oversized metadata and signed-query-token fixtures; prove bounded output and error/log redaction through FFI/UI.
- [x] **COV-207** Run target Rust tests, exact-head API 29 Android smoke/required-method evidence and overall CI; note live-source/hardware exclusions separately.

**Acceptance:** deterministic fixture tests prove parser/adapter/FFI/UI behaviors without live YouTube dependency; original Analyze regressions remain covered.

**Evidence:** _pending._

**Evidence (COV-201):** Production `core/src/youtube_source/parse.rs::{parse_player,parse_stream}` and `core/src/youtube_source/tests.rs::{provider_field_shape_matrix_fails_closed_without_false_metadata,rejects_duplicate_provider_format_ids_instead_of_selecting_arbitrary_url,provider_stream_reordering_preserves_media_id_and_stream_attributes}` reject missing/wrong-shaped, duplicated and reordered sanitized provider data without live external requests. Exact-head Rust LCOV workflow `44505561276` at `a31ea408bbd5dfe01ed5684372c82f5e525214d5` passed, executing `cargo test --workspace --all-features`; deterministic fixture run `44505561262` also passed. Remaining COV-200 acceptance is unchecked.

**Evidence (COV-202):** `core/src/youtube_source/tests.rs::{watch_page_http_status_classifies_retryability_without_leaking_body,watch_page_with_truncated_body_returns_retryable_network_error,unknown_length_watch_response_is_bounded_during_streaming,oversized_watch_response_is_rejected_before_parsing,watch_page_silent_server_times_out_without_exposing_provider_details}` exercise production `YouTubeSource::fetch_watch_page` and bounded response reader against sanitized localhost HTTP 403/404/408/429/500/502/503/504, truncated or missing length, oversized and timed-out response cases. They assert typed `HttpStatus`, `NetworkUnavailable` and `SourceChanged` classifications without forwarding provider tokens or claiming unavailable media was resolved. Target Rust formatter, clippy, and `cargo test --workspace --all-features` passed at exact `84aa0174e4c5707ab0aef32b488d15c66fdb4a75`, CI run `44675474331`; Rust coverage workflow `44675474437` also passed. All COV-202 fixture inputs are loopback, not live YouTube. COV-203 added in a subsequent commit and remains unchecked until the new exact-head CI qualifies it.

**Evidence (COV-203, Rust-qualified):** Production `core/src/youtube_source/parse.rs::parse_stream` now normalizes provider `audio/mp4` AAC-only audio to the playback-compatible `m4a` container, preserving MIME; `core/src/youtube_extract.rs::normalize_stream` thus recognizes direct-play split-audio tracks. `core/src/youtube_source.rs::{download_plan_with_options,plan_download_assets_from_fresh_media}` uses the same refreshed-stream mapping in production and offline fixtures. `core/src/youtube_source/tests.rs::{split_audio_and_subtitle_track_ids_map_to_fresh_provider_neutral_assets,combined_video_rejects_inapplicable_separate_audio_selection}` asserts duplicate 1080p labels preserve `format:137`, explicit audio 140 is not replaced by high-bitrate 141, default audio selects 141, subtitle `.en` selects a local VTT path, and bad IDs and invalid combined-video separate-audio selections fail closed. Rust fmt/clippy/workspace tests `44676046018` and LLVM coverage `44676046105` succeeded at exact implementation SHA `e408880d1719b55026dd35c84a98db5cf7900d52`. Mandatory Android smoke rerun `44676046005` attempt 2 was **in progress** when this evidence was recorded; COV-207 remains unchecked until a passing complete matrix is observed.

**Evidence (COV-204):** `core/src/ffi_source.rs::{canceled_source_boundary_before_dispatch_never_starts_work,in_flight_cancellation_disallows_completion_after_blocked_source_returns}` exercise production `resolve_source` through `ControlledResolutionSource` with bounded test-controlled channels. The pre-canceled request never enters the provider (`calls=0`); a cancellation while blocked has exactly one provider call, joins its worker, and yields a typed nonretryable `Canceled` rather than fabricated media. Both execute as ordinary workspace Rust tests in CI `44676046018` (passing exact SHA `e408880...`). The Android production gateway pre-canceled repeated-open smoke is also covered in API matrix `44676046033`; this is not a claim of actual external live YouTube cancellation.

**Evidence (COV-205/206/207, exact full-green SHA `89b633397df31125055c71a7fd8bc582cb013181`):** Compose API29 `AddResolverAvailabilityInstrumentedTest` tests plain/short/share YouTube URLs, tracking query normalization at the actual `AppSourceAnalysisGateway`, duplicate Analyze gating, startup errors and safe typed NETWORK_TIMEOUT→second-Analyze recovery without a false-success card. The edit field intentionally keeps raw user text; gateway sees only the token-free canonical URL. Android JaCoCo `44752387425`, smoke `44752387182`, main Rust/JVM `44752387157`, Rust LCOV `44752387168`, API 26/33/34 `44752387129`, real network `44752386893`, cold start `44752386985`, FGS `44752387151`, supply `44752387030`, deterministic fixture `44752387325`, CI evidence `44752387640` all passed **11/11** on exact implementation SHA. FFI fixture-source malformed input returns typed `UnsupportedSource`; `SourceMetadataPolicyTest` exercises 100K oversized input, signed-query and encoded-header metadata redaction; `ProductionRedactionBehaviorTest` exercises actual Compose presentation with persisted signed URLs. This does not qualify live YouTube or Samsung A54. See `docs/TEST_COVERAGE_TRACEABILITY_2026-11-20.md` for the per-test risk table.

## COV-300 — SQLite durability, worker races and startup repair (P0)

- [x] **COV-301** Add file-backed SQLite transaction fault tests for enqueue, claim, stage, promote, cancellation, and completion boundaries, including reopen/recovery assertions.
- [x] **COV-302** Cover future schema version, malformed/corrupt DB, foreign-key/orphan data and failed migration; verify fail-closed errors and no destructive fallback.
- [x] **COV-303** Exercise concurrent independent DB connections/workers racing to claim and settle the same job; verify a single authoritative winner.
- [x] **COV-304** Add cancel-vs-complete, pause-vs-retry, stale worker, and duplicate schedule races without nondeterministic sleeps; use barriers or test-controlled clocks.
- [x] **COV-305** Add repeated startup reconciliation/partial-staged-file tests, including missing assets, symlinks, stale lease and crash-between-checkpoint boundaries; prove idempotence and outside-root safety.
- [x] **COV-306** Add a bundled-SQLite upgrade regression smoke for `rusqlite 0.41.x`: file open/migrate, FK enforcement, transaction, snapshot persisted after reopen.
- [x] **COV-307** Run new Rust tests under normal CI and coverage; document observed durable-state invariant and exact-head CI.

**Acceptance:** no double promotion/completion, silent state loss or recovery escape; all fault injections are test-only.

**Evidence (COV-306):** `core/src/persistence/tests.rs::bundled_sqlite_file_reopen_preserves_durable_queue_and_foreign_keys` exercises `LibraryStore::open/migrate/save_download_snapshot/stage_asset/promote_completed` against a file-backed bundled SQLite database, reopens and checks saved queue/library, verifies `PRAGMA foreign_keys=ON` and rejects an orphan `assets` insert. The test is included in the passing Rust coverage lane `44501336363` at `af38f8ffc31a27d6edd7111800f3a32c3fd1280e` and normal Rust CI `44501336322`; no arbitrary fixture claims about other migration versions.

**Evidence (COV-303):** Production `LibraryStore` snapshot compare-and-swap and atomic promotion in `core/src/persistence/{snapshots}.rs` and `core/src/persistence.rs` are exercised by `core/src/persistence/tests.rs::{independent_sqlite_worker_connections_race_for_one_authoritative_claim,independent_sqlite_completion_and_cancel_race_never_promotes_canceled_item}` with two independent connections and synchronization barriers, requiring exactly one claim or terminal transition and library/staging consistency. Exact-head Rust coverage `44505561276` and deterministic fixture `44505561262` passed at `a31ea408bbd5dfe01ed5684372c82f5e525214d5`. Other fault-phase and job-scheduling races remain open.

**Evidence (COV-307, requalified):** Exact-head `e408880d1719b55026dd35c84a98db5cf7900d52` passed `cargo test --workspace --all-features` and clippy in CI run `44676046018`, plus `cargo-llvm-cov --workspace --all-features` in `44676046105` (**8,511/9,430 = 90.25%** scoped Rust production lines; no measurable branches). Included `persistence/tests.rs::{independent_sqlite_worker_connections_race_for_one_authoritative_claim,independent_sqlite_completion_and_cancel_race_never_promotes_canceled_item,bundled_sqlite_file_reopen_preserves_durable_queue_and_foreign_keys}` assert a single durable winner, fail-closed stale snapshot CAS and no promoted canceled asset after file-backed reopen. This run qualifies test execution, not all still unchecked transaction fault phases COV-301/302/304/305.

**Evidence (COV-301/302/304; exact source SHA `bfba51ec3a3f5807670e2bf1f895ec6f8519b17d`):** Rust workspace fmt/clippy/tests in main CI `44726100035` passed, including `core/src/persistence/tests.rs::{injected_queue_claim_stage_cancel_and_completion_write_faults_recover_after_reopen,failed_asset_insert_rolls_back_promotion_and_preserves_staging_after_reopen,failed_initial_migration_rolls_back_ddl_and_preserves_user_tables_after_reopen,corrupt_download_snapshot_is_not_silently_deleted_during_reopen,future_schema_and_corrupt_file_fail_closed_without_destroying_user_data,independent_sqlite_pause_and_retry_race_preserves_one_authoritative_state,concurrent_duplicate_schedule_insert_writes_only_one_durable_job,independent_sqlite_completion_and_cancel_race_never_promotes_canceled_item}`. Production `LibraryStore::{insert_download_snapshot_if_absent,replace_download_snapshot_if_unchanged,stage_asset,promote_completed_if_unchanged,promote_completed,migrate,load_download_snapshots}` is exercised against **on-disk SQLite** with test-only failure triggers, reopen/retry of enqueue, claim, stage, cancel, completion, and rollback-safe promotion. Failed v0 migration preserves an existing user-owned `assets` table and rolls back partially created `library_items` / index on two attempts; unknown future schema, damaged files, orphan FK and malformed download row fail closed without deleting data. Two-connection CAS pause-vs-retry and terminal cancel-vs-complete races and three-worker duplicate scheduling use synchronization barriers, require exactly one winner and preserve the authoritative row after reopen. Rust LCOV `44726100060` passed with **8,688/9,584 = 90.65%** production source lines (branch unmeasured). These tests do not assert physical flash failure or SQLite disk crash durability beyond file-backed transaction/reopen behavior.

**Partial evidence (COV-305; remains unchecked):** `core/src/startup_reconciliation.rs::tests::file_backed_two_checkpoint_crashes_are_reconciled_idempotently_without_partial_loss` reopens a real database three times after staged-download crash checkpoints and proves a single queued recovery, one failed orphaned stage recovery, preservation of user Paused/RetryWait deadlines and no deletion of partial/resume sidecars. Existing symlink/missing asset cases and `stale_startup_requeue_cannot_override_newer_pause_or_completion` also pass in Rust CI `44726100035`. A distinct explicit stale-lease/crash-between-actual-filesystem-move test is still needed before checking full COV-305.

**Evidence (COV-305, qualified at `fa0dd56c4a22b45beb7b810001a313e0442eeecb`):** Production `startup_reconciliation::{reconcile_startup_with_library_root,requeue_interrupted_snapshot_if_unchanged}` is exercised by `file_backed_two_checkpoint_crashes_are_reconciled_idempotently_without_partial_loss`, `stale_worker_lease_at_startup_cannot_reset_a_newer_reclaimed_attempt`, plus missing/symlink, staged orphan and stale-repair tests. Actual on-disk SQLite reopen, three repeated scans, sidecar retention and compare-and-swap newer lease preservation; Rust fmt/clippy/workspace CI `44727541863` passed. Requalified in full-green Rust CI `44752387157`. Power removal/fsync cannot be simulated by these tests; no physical-device claim.

## COV-400 — Android service, notification and API-level matrix (P1)

- [x] **COV-401** Audit existing API 29/35 tests, and establish feasible API 26/33/34 emulator profiles and bounded execution strategy.
- [x] **COV-402** Add API 26-33 foreground fallback and API 34+ UIDT scheduling behavior tests; assert actual scheduling inputs and failure/restriction cases.
- [ ] **COV-403** Add notification-permission granted/denied/revoked and repeated pause/resume/cancel notification-action tests backed by durable queue state.
- [ ] **COV-404** Add process/service recreation and timeout callback tests ensuring legal teardown, noncancel platform stop, no duplicate worker and recovery ordering.
- [ ] **COV-405** Extend host-controlled network E2E for quick offline/online and metered/unmetered transitions; verify Wi-Fi-only classification and exactly-once resume behavior.
- [x] **COV-406** Cover reboot-before-unlock restrictions and no direct boot-time dataSync FGS launch on applicable APIs, with an executable behavioral test.
- [x] **COV-407** Execute selected API 26, 33, 34 and existing 29/35 runs; record actual APIs exercised, links, skipped/unavailable tiers and flaky retry evidence.
- [ ] **COV-408** Retain hardware-only A54 battery/network behavior as separately documented pending device validation; do not check it as automatically passed.

**Acceptance:** executable platform behavior checked at named API levels; existing workflows remain required and intact.

**Evidence:** _pending._

**Evidence (COV-401/407, validated 2026-11-20 UTC):** `.github/workflows/android-api-matrix.yml` and `scripts/run_android_api_matrix.sh` invoke and require executed XML for four native/runtime/timeout/notification classes **separately** at APIs **26, 33 and 34**. All three matrix jobs passed on exact SHA `1534875022dc3273bae07af9a5b423c4e2e8ad2e`, Actions run `44646351120` **attempt 2** (API 26 retry followed Maven Central HTTP 429 for Gradle's JUnit Platform dependencies, not an assertion failure). Existing API 29 Android smoke `44646351074`, cold start `44646351058` and real OS-network `44646351039` passed, as did API 35 FGS timeout `44646351114`. Emulator results do not prove an actual Samsung A54 battery/firmware behavior, arm64 runtime, or every scheduling restriction on every API tier. COV-402/403/404/405/406/408 remain open for their full behavioral scope.

**Partial evidence (COV-405; remains unchecked):** Production `DownloadForegroundService.handleConnectivityChange` now requests a cooperative native stop on a durable connectivity pause, and the real API 29 host-controlled `Rmd1506ConnectivityE2EInstrumentedTest` passed both offline→online restore and unmetered→metered→unmetered Wi-Fi-only modes (`WIFI_ONLY_METERED_QUALIFIED`) in run `44646351039`. Extend rapid multi-transition and exactly-once resume assertions before checking the entire COV-405 requirement.

**Evidence (COV-402/406, exact `89b633397df31125055c71a7fd8bc582cb013181`):** Actual API 26/33/34 emulator workflow `44752387129` passed all three profiles and `scripts/run_android_api_matrix.sh` required executed XML for `AndroidRuntimeSmokeTest::{runtimeSchedulingInputsAndRestrictedUidtFailureMatchActualApiTier,bootBeforeUnlockCannotStartDataSyncOrOpenCredentialProtectedFiles}`. Production `DownloadExecutionSchedulerPolicy` constructs API-tier `JobInfo` extras/any-network, API34 Wi-Fi NetworkRequest and setUserInitiated; null platform scheduler must refuse a fabricated accepted UIDT. Production `DownloadRebootReceiver` was invoked with a hostile ContextWrapper whose foreground/service/file methods throw; no illegal launch or pre-unlock file access occurred, and recovery remains deferred to the normal startup path. These qualify Android API behavior, **not** an actual locked/rebooting OEM device. COV-403/404/405 remain open for notification revoke, race/recreation and real rapid network-transition scope, COV-408 for physical A54.

## COV-500 — HTTP resume, integrity and filesystem adversaries (P1)

- [x] **COV-501** Inventory `core/src/download/tests/transfer.rs` and `resume_limits.rs`; avoid re-adding existing 200/206/416, ENOSPC and body-disconnect cases.
- [x] **COV-502** Add reproducible bounded property tests over Range/Content-Range, validators, content length and chunked/truncated response combinations using a local HTTP stub.
- [x] **COV-503** Assert unsafe combinations always restart or fail (never append unverifiable bytes); output failing seed/input for replay.
- [x] **COV-504** Inject mid-write disk failures, cancellation and malformed resume metadata; verify retained/removed partials match policy and no accepted corrupt SHA/length.
- [x] **COV-505** Check no path traversal, symlink escape, excessive writes or unbounded memory from generated inputs; retain minimized adversarial fixtures.
- [x] **COV-506** Run deterministic tests repeatedly in CI; record coverage delta, exact-head Rust job and accepted invariants.

**Acceptance:** zero accepted corrupt/resumed outputs across bounded adversarial cases; reproducible seeds in failures.

**Evidence (COV-501):** `core/src/download/tests/{transfer,resume_limits}.rs` and `docs/TEST_COVERAGE_BASELINE_2026-11-19.md` inventory existing 200/206/416, validator/restart, mid-body disconnect, ENOSPC and size-limit tests before designing new property tests. Inspected at `b46ba5c899eba4ae191afd4867d3762241f4a1d5`; relevant existing tests passed as part of the exact-head Rust coverage run `44501336363` at `af38f8ffc31a27d6edd7111800f3a32c3fd1280e`. COV-502 through COV-506 still need implementation.

**Evidence (COV-503):** `core/src/resume_http.rs::{seeded_remote_validator_matrix_never_appends_unproven_partial_bytes,malformed_content_ranges_never_report_a_trustworthy_total}` exercise production representation extraction, `ResumeRepresentation::matches`, sidecar saving and `prepare_partial_reuse` across 128 repeatable validator/size combinations and invalid Content-Range values. Failures include seed/case data. Rust coverage `44505561276` and deterministic fixture E2E `44505561262` passed at `a31ea408bbd5dfe01ed5684372c82f5e525214d5`. The broader HTTP 200/206/416 property matrix and injected filesystem failures remain unchecked.

**Evidence (COV-502/504/505/506):** Actual production `DownloadEngine::transfer` and `transfer_with_progress` tests `core/src/download/tests/resume_limits.rs::{seeded_local_http_resume_matrix_never_accepts_unverified_appends,seeded_raw_chunked_and_truncated_http_responses_never_promote_corrupt_media}` (28+20 deterministic localhost 200/206/416, validators, forged range and premature EOF); `core/src/download/tests/transfer.rs::{seeded_mid_write_failures_keep_exact_partials_and_recover_with_verified_sha,canceled_progress_callback_obeys_partial_retention_and_retries_safely,corrupt_resume_sidecar_keeps_original_partial_and_rejects_false_completion,seeded_unsafe_paths_are_rejected_before_network_or_outside_root_writes}` (12 seeded storage faults, both cancel-retention modes, corrupt resume sidecar, 36 traversal/absolute/symlink cases). Seed/case data is on assertion failure; exact bytes and SHA-256 required for completed outputs; outside-root sentinel unchanged. Rust CI `44748753191` and LLVM LCOV `44748752980` passed at implementation `429e82530a9afcfcc3eeeb4b04daf20353b185e0`, repeated in Rust LCOV `44751198528` and fully green Rust+LCOV `44752387157`/`44752387168` at `89b633397df31125055c71a7fd8bc582cb013181`. Measured total Rust line coverage **90.65% → 90.78%** (+0.13 percentage points), branches unmeasured; tests do not claim real power failure. See traceability.

## COV-600 — Media3 local playback lifecycle (P1)

- [x] **COV-601** Inventory existing playback unit/instrumented tests and prepare small deterministic combined A/V, split A/V and subtitle assets.
- [x] **COV-602** Add real Media3 `PlaybackSessionService` instrumentation for bind/unbind and destruction/recreation, ensuring one canonical playback session and no leaks.
- [ ] **COV-603** Exercise audio-focus loss/gain, controller disconnect, rapid play/pause/seek and saved-position restoration after lifecycle/process restart.
- [ ] **COV-604** Test missing, truncated and corrupt local media/subtitles: user-visible error, no unintended network access or false completed state.
- [x] **COV-605** Test selected local audio/subtitle track switching and representative media-button/notification behavior without relying on external providers.
- [x] **COV-606** Run exact-head playback instrumented smoke, golden/layout tests if impacted, and standard CI; record limitations of emulator audio routing.

**Acceptance:** production Media3 session behavior is exercised by deterministic offline assets and retained across supported lifecycle transitions.

**Evidence:** _pending._

**Evidence (COV-601/602/605/606, exact green `89b633397df31125055c71a7fd8bc582cb013181`):** `PlaybackSessionBehaviorInstrumentedTest::packagedOfflineAvFixtureCanBeDemuxedIntoRealVideoAudioAndSubtitles` decodes SHA-verified 3,064-byte local H.264/AAC MP4 and uses Android `MediaExtractor`/`MediaMuxer` to derive split video MP4 / audio M4A + local WebVTT. `releasedControllerAndStoppedServiceRecreateOneFreshCanonicalPlaybackSession` exercises real session controller unbind, PLAYER_RELEASED→SESSION_RELEASED teardown and new offline `STATE_READY` with distinct ExoPlayer instance. Existing `subtitleControlCyclesTracksThenOffOnTheSessionTrackSelection`, `audioControlCyclesPersistedTracksAndWrapsOnTheSessionTrackSelection`, and `systemMediaButtonsPauseResumeAndSeekTheCanonicalSession` exercise selection and button actions. Android smoke `44752387182`, main CI + goldens `44752387157`, API 26/33/34 matrix `44752387129`, all 11 workflows passed; reference `docs/MEDIA3_COVERAGE_FIXTURE_INVENTORY_2026-11-20.md`. Emulator audio routing not physical A54 audio-focus qualification. COV-603 remains open; missing/corrupt local playback UI COV-604 from newer `a82ec599...` is **pending** latest Android smoke, not checked.

## COV-700 — UniFFI/native boundary correctness (P1)

- [x] **COV-701** Inventory `core/src/ffi*.rs` and `GeneratedUniffi*Gateway` bindings, supported error variants, nullable/unsigned fields, cancellation and cleanup paths.
- [x] **COV-702** Test FFI error conversion and edge-value round trips including zero/max unsigned, null fields, large bounded strings and malformed inputs; forbid empty-success coercion.
- [ ] **COV-703** Add repeated open/close and pre/in-flight cancellation race tests through the production gateway/native-loading path.
- [x] **COV-704** Prove `liboffline_yt_core.so` + JNA packaging for both arm64-v8a and x86_64 and runtime tests on x86_64 emulator.
- [ ] **COV-705** Execute arm64 runtime smoke on emulator/hardware if feasible; otherwise record this as an explicit unverified runtime-ABI gap, distinct from cross-build.
- [ ] **COV-706** Validate safe diagnostic redaction through the boundary and Android UI; run exact-head generated-binding/ABI build and Android smoke.

**Acceptance:** typed errors/cancel/cleanup survive boundary crossings, without fabricated data, token leakage or false arm64 runtime claims.

**Evidence:** _pending._

**Evidence (COV-704, validated 2026-11-20 UTC):** `.github/workflows/ci.yml` job `UniFFI Kotlin and Android ABI` (`44646351095`) built and verified both `arm64-v8a` and `x86_64` Rust core/JNA packaging; on the same SHA API 29 smoke (`44646351074`) and API 26/33/34 matrix (`44646351120`) executed `GeneratedUniffiSourceGatewaySmokeTest::repeatedNativeResolverOpenCloseAndPreCanceledRequestsDoNotLeakState` through packaged native `FfiYouTubeSourceService` and `GeneratedUniffiSourceAnalysisGateway` on x86_64. **Arm64 cross-build/packaging is not arm64 runtime qualification**; COV-705 remains open.

**Evidence (COV-701/702; exact SHA `bfba51ec3a3f5807670e2bf1f895ec6f8519b17d`):** `docs/UNIFFI_NATIVE_BOUNDARY_COVERAGE_INVENTORY_2026-11-20.md` inventories the 11 Rust `ffi*.rs` modules exported from `core/src/lib.rs`, the Android `coregateway` source adapter and native loading/preflight, 14 typed error kinds, nullable/unsigned fields and cooperative cancel/cleanup paths. `core/src/ffi.rs::{all_core_error_categories_preserve_kind_and_retryability_across_ffi,ffi_records_preserve_optional_and_maximum_unsigned_values,zero_unsigned_fields_preserve_presence_and_bounded_text_across_ffi_records,same_label_audio_choices_keep_distinct_format_ids_through_ffi}` exercise typed error mapping for both retryability values, `u32::MAX`/`u64::MAX`, zero-vs-`None` optional values, preserved bounded title text and format IDs. `core/src/ffi_source.rs::malformed_source_requests_fail_with_typed_error_not_empty_success` sends empty, unsupported-scheme/host and signed-query variations through production fixture-source FFI and requires typed `UnsupportedSource` with no fabricated `media`/choice success and no private token in diagnostics. Rust tests main CI `44726100035` and LCOV `44726100060` succeeded. Generated Android x86_64 native smoke was previously validated at `1534875022dc3273bae07af9a5b423c4e2e8ad2e` (CI `44646351074`, API matrix `44646351120`); this does NOT claim arm64 runtime or new SHA's Android smoke has passed. COV-703/705/706 remain separate for real native in-flight/UI/error runtime qualification.

## COV-800 — Final evidence and gate policy (P2, after implementation)

- [x] **COV-801** Recompute actual per-module Rust and Kotlin coverage and compare with COV-100 baseline; report line/branch deltas without conflating languages.
- [x] **COV-802** Map each added test to risk, executed production path, SHA and CI run, and identify remaining high-risk uncovered branches.
- [x] **COV-803** Assess whether to enforce a narrow nonregression threshold on stable Rust/JVM packages; **do not** enable it until baseline, stability and developer approval are documented.
- [x] **COV-804** Run full required exact-head CI including Android smoke, FGS timeout, supply chain, deterministic E2E, cold-start and real-network E2E; failures must be fixed or remain open.
- [x] **COV-805** Verify tag-only artifact policy still passes, no live-source dependency exists in mandatory lanes, and no security/privacy exceptions were broadened.
- [ ] **COV-806** Publish a dated coverage closeout note with report locations, tool versions, CI run IDs, confirmed gaps and any pending Samsung A54/human release gates.
- [ ] **COV-807** Check this task only after all remaining actionable subtasks are verified; do not mark the initiative done if device/API or measurement requirements are still blocked.

**Acceptance:** measured, reproducible coverage baseline and improvement, behavioral proof with exact-head CI, and honest residual-risk reporting.

**Evidence:** _pending._

**Evidence (COV-804, latest fully green implementation SHA):** All 11 workflows passed at `1534875022dc3273bae07af9a5b423c4e2e8ad2e`; individual run IDs and attempt numbers are in COV-106/110 above. This only proves that implementation commit; subsequent changes require fresh CI. Do **not** treat COV-804 as final initiative closeout or mark COV-807 complete.

**Evidence (COV-801/803/805; partial overall closeout):** On exact `e408880d1719b55026dd35c84a98db5cf7900d52`, Rust `core/src` `44676046105`: **8,511/9,430 = 90.25%** executable lines (45 files; branch unmeasured), up **1.25 percentage points** from `af38f8ffc...` Rust baseline 89.00% and **0.84 points** from the latest prior 89.41%; raw function-symbol coverage **921/1,522 = 60.51%**, not a reliable unique named-function metric. Kotlin/JVM `44676046018`: **1,433/3,799 = 37.72%** lines and **904/2,954 = 30.60%** branches, down 0.08 and 0.09 percentage points versus baseline 37.80% and 30.69%; Kotlin `ui` 272/1347, `coregateway` 335/868 and `downloads` 376/796. Separate API 29 on-emulator JaCoCo `44676046080`: measured production-path coverage (never added to Kover denominator). Largest measured Rust remaining uncovered lines include `youtube_source.rs` 185/298, `ffi_download_control.rs` 289/381, `worker.rs` 422/492, `ffi.rs` 387/452. **Threshold assessment (COV-803):** do not enforce Rust 85%, critical durable-state 90% or Kotlin 80% thresholds: branch/file/function scope is incomplete, Kotlin is far below target and developer approval to gate is absent. **COV-805:** main CI `44676046018` plus supply chain `44676046026` and CI evidence `44676046160` succeeded under unchanged tag-only artifact policy; all newly required tests use offline/local fixtures, the live provider test remains explicitly ignored/manual, and no security/privacy exception was broadened. New exact-head Android smoke `44676046005` attempt 2 was still running at checkpoint; do not claim this newer commit has 11/11 green until confirmed.

**Evidence (COV-802, published):** `docs/TEST_COVERAGE_TRACEABILITY_2026-11-20.md` maps each new/verified COV-200/300/400/500/600/700 test family to production function, actual test method, exact source SHA and passing Rust/JVM/API29/API26-34 workflow, with missing branch/function measurement and hardware/arm64 gaps explicitly enumerated. Verified current report-only Rust **8,727/9,613 = 90.78%**, Kotlin JVM **1,433/3,799 = 37.72%**, Kotlin JVM branch **905/2,954 = 30.64%**, no trustworthy named Rust function or BRDA branch counters. The final closeout COV-806 and overall COV-807 remain unchecked because measurable software gaps/hardware gates remain.

---

## Recommended implementation order

COV-000 → COV-100 → COV-200 → COV-300 → COV-500 → COV-400 → COV-600 → COV-700 → COV-800.

This is a priority recommendation, not permission to skip independent work or mark tasks complete without test execution. If an emulator, arm64 runtime or external dependency is genuinely blocked, document the blocker and continue with independent Rust/JVM work.

## Completion record

- **Current baseline SHA at document creation:** `e946a24b9939c03a7f77f667705a1f888ae44771`.
- **Completed (evidence-backed) subtasks at this checkpoint:** 49/62; 13 remain open. The task sections and exact-SHA evidence above, not this summary count, are authoritative.
- **Latest evidence-backed coverage:** exact `89b633397df31125055c71a7fd8bc582cb013181`, Rust core **8,727/9,613 (90.78%)** lines (branches unmeasured); JVM Kotlin **1,433/3,799 (37.72%)** lines, **905/2,954 (30.64%)** branches, separate API29 JaCoCo. Do not combine coverage lanes.
- **Latest fully green implementation SHA:** `89b633397df31125055c71a7fd8bc582cb013181`, **11/11** workflows, main CI `44752387157`, Android smoke `44752387182`, JaCoCo `44752387425`, Rust LCOV `44752387168`, API matrix `44752387129`. A newer production playback-error change `a82ec5997a69dfb6d4aa7448645f40b0342294bd` requires its **own** full CI before check-off.
