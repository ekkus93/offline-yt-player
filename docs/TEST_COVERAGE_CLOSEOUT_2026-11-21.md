# Offline YT Player — coverage and runtime qualification closeout

**As of:** November 21, 2026. **Repository:** `ekkus93/offline-yt-player`. **Latest measured implementation SHA:** `9d09e3c0df31bb3e64287ac0b0b1e56c02cb3afd`. **Traceability:** `docs/TEST_COVERAGE_TRACEABILITY_2026-11-20.md`. **Canonical ledger:** `docs/TEST_COVERAGE_EXPANSION_TODO_2026-11-19.md`.

## Qualification status — not a release approval

At the authored checkpoint, **10/11 exact-implementation GitHub Actions workflows have completed successfully**; latest Android smoke `44943433866` is still in progress, not presumed passed. Prior fully green implementation was `89b633397df31125055c71a7fd8bc582cb013181` with 11/11 workflows. This document closes the requested *reporting subtask*, not the overall coverage initiative or the A54/release-policy gates.

| Required workflow | Exact run ID | Status at authoring |
| --- | --- | --- |
| CI — Rust fmt/clippy/tests, UniFFI ABIs, Android lint/JVM/unit/build | `44943433733` | Success |
| Rust core LCOV and conservative source-matched function evidence | `44943433932` | Success |
| Android API 29 on-device JaCoCo | `44943433785` | Success |
| Android API 26/33/34 native compatibility and host-toggled notification permission | `44943433869` | Success |
| Android real-network E2E (two OS and Wi-Fi cost transitions, native stop race) | `44943433819` | Success |
| Android FGS timeout | `44943433766` | Success |
| Android cold start | `44943433776` | Success |
| Supply chain (advisories/licenses) | `44943433865` | Success |
| CI evidence | `44943433863` | Success |
| Deterministic E2E fixture | `44943433736` | Success |
| Android smoke, including Compose/raster goldens and Media3 | `44943433866` | **In progress; not counted green** |

## Measured production-code coverage (disjoint denominators)

| Tool and actual scope | Covered / measured | Coverage | Notes |
| --- | ---: | ---: | --- |
| `cargo-llvm-cov 0.9.1`, Rust `core/src` executable lines | 8,878 / 9,721 | **91.33%** | 45 measured production files; Rust branch count **unmeasured** |
| Conservative Rust source-declaration mapping | 290 / 312 *matched source declarations executed* | partial only | 1,232 of 1,544 raw LLVM symbols unmatched/excluded; no whole-project function denominator; test-only modules excluded |
| `Kover 0.9.11`, Kotlin/JVM lines | 1,436 / 3,815 | **37.64%** | JVM only, not Android device-side |
| `Kover 0.9.11`, Kotlin/JVM branches | 906 / 2,966 | **30.55%** | Measured JVM branch probes, independent of Rust |
| Android API29 on-device JaCoCo (Gradle-managed) | source hits recorded | separate measurement | Android/Media3 integration and Compose, not added to Kover denominator |

The oldest initial Rust baseline was **89.00%** and the latest measured Rust line coverage is **91.33%** (+2.33 percentage points); Kover line coverage is **37.64%**, below the proposed 80% business-logic goal. None of the proposed Rust 85%, critical-state 90%, or Kotlin 80% thresholds is enforced. Do not interpret raw Rust FNDA (946/1544) as unique named-function coverage.

### Source-level remaining gaps mapped to behavior

The source-mapped Rust report identifies 22 matched declarations without execution on host Rust tests. Some are exercised in *Android* instrumentation, but LLVM host LCOV correctly does not count device execution. Priorities:

- `youtube_source.rs::{fetch,fetch_innertube_android,resolve,choices}`: source HTTP fallback and dynamic provider policy — COV-200 fixture tests already qualify malformed and signed-query paths; live provider throttling and fallback remain higher risk and deliberately out of mandatory CI.
- `ffi_source.rs::list_choices`, `ffi_download_control.rs::{enqueue_with_choice,enqueue_with_options,retry,retry_inner}`: UniFFI choice/retry and concurrency — Rust host tests cover stale control CAS, while Android generated-boundary smoke covers runtime calls. Additional true provider choice execution remains unmeasured.
- `ffi_download_worker.rs::{request_platform_stop,download_presentations}`: latest Android **real native** platform-stop E2E `44943433819` qualifies the former on-device, but host Rust function coverage still reports zero.
- `persistence/rows.rs::json_error`, `resume.rs::io_error`: malformed SQLite row and I/O errors — partial file-backed corruption and transfer/range fault tests exist; not all OS error branches are executable in bounded CI.
- `ffi.rs::{library_list,library_get,library_delete}` and `asset_validation.rs::validation_io_error` had newly added direct Rust/SQLite/ENAMETOOLONG tests; the source report's per-name execution may still vary by codegen identity. Trust independently reported line evidence over a raw symbol hit percentage.

No branch counters are inferred for Rust when `BRDA` records are absent. The full per-file JSON report and named function JSON are written to `coverage/rust-core-summary.json` and `coverage/rust-named-function-evidence.json` *inside each CI job* and the bounded Markdown summaries are appended to the workflow's `GITHUB_STEP_SUMMARY`. The reports are **not** automatically uploaded as master artifacts by policy. JVM XML and JaCoCo on-device reports are produced inside respective runners; use their step summaries for durable cited measurements.

## Coverage work completed in this continuation

- Seeded 200/206/416 resume validation, forged range/ETag/Last-Modified, chunked/truncated HTTP body; injected ENOSPC and cancellation-retention tests; traversal/symlink I/O fault families.
- Native `FfiError` redaction and Android error display, source-mapped Rust function telemetry.
- Verified local H.264/AAC MP4 (3,064 bytes), real MediaExtractor/Muxer split audio/video + WebVTT, Media3 controller teardown/recreate, audio-focus and rapid transport commands.
- Real Android 26/33/34 scheduler/locked boot and **post-install** denied/granted notification-permission phases; no Android process-death-in-test shortcut.
- Two real Android OS-offline/online and Wi-Fi metered/unmetered transitions, exact durable single completion, 8 MiB real native in-flight stop with concurrent callbacks and retained retry state.
- Found and corrected a foreground-service race that could leave a resumed durable row `QUEUED` without dispatch after a network-loss worker was still unwinding; bounded deferred relaunch and JVM regression.

## Still open — no fabricated qualifications

- **COV-403:** Granted/denied runtime permission is qualified, but repeated notification Pause/Resume/Cancel `PendingIntent` idempotency must be added and verified; existing single-cycle UI/durable test is `Rmd1507NotificationControlInstrumentedTest::notificationActionsDriveDurableQueueAndProductionDownloadsUi`.
- **COV-604:** Missing/corrupt local video shows a safe error, but deliberately truncated MP4/VTT, missing subtitle and no-false-completion behavior still need separate passing emulator evidence.
- **COV-705:** AArch64 Android Rust core and APK are cross-compiled and packaged, **not executed on arm64 Android runtime**. Do not substitute host x86_64 emulator execution for A54 device runtime.
- **COV-408:** Samsung Galaxy A54 real-device battery/Doze, network handoffs, foreground/background restrictions, offline playback, Bluetooth/audio-focus and UI acceptance remain pending user/device access.
- **COV-807:** Overall TODO completion remains blocked on these unverified items and any human source-policy/licensing release approval.

Hardware QA must be performed on an attached authorized Android arm64 device (e.g. Samsung A54) and documented with exact APK commit/build, device SDK/ABI, run logs, network transitions, playback and OS background state before acceptance. Do not mark a release signed off from emulator-only results.

**Security/build policy:** no mandatory live-provider fixture, credential/log exception, non-tag artifact upload or threshold gate added. Supply chain and CI evidence workflows are green at the cited implementation SHA.
