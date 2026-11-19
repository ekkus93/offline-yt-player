# Offline YT Player — Risk-Based Test Coverage Expansion Specification

**Status:** Proposed; no new coverage thresholds or tests are implemented by this document.  
**Date:** 2026-11-19  
**Repository:** `ekkus93/offline-yt-player`  
**Code reviewed at:** `e946a24b9939c03a7f77f667705a1f888ae44771` (`master` at review)  
**Execution tracker:** `docs/TEST_COVERAGE_EXPANSION_TODO_2026-11-19.md`  
**Existing requirements:** `docs/OFFLINE_YT_PLAYER_REMEDIATION_SPEC_2026-09-17.md` and `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`

## 1. Purpose and problem statement

Provide measurable test coverage and targeted regression protection for the existing Android/UniFFI/Rust application. The remediation checklist records extensive functional and integration tests, but the reviewed `.github/workflows/ci.yml` runs Rust tests and Android JVM tests **without publishing line/branch coverage metrics**. A green test suite is not a numeric coverage report. This project shall first establish an auditable baseline, then close uncovered high-risk behaviors with deterministic tests.

This is a *new* improvement track, not a reopening or automatic rechecking of the completed remediation TODO. Existing tests are to be reused and extended, not duplicated to inflate test counts. Passing CI at an earlier SHA does not establish coverage or qualification for a later change.

## 2. Scope and constraints

### In scope

- Instrument and summarize **Rust core** coverage, **Kotlin JVM business-logic** coverage, and a separately qualified **Android instrumentation** coverage lane when tooling permits.
- Add executable failure-path, branch, race, lifecycle, and boundary tests around:
  1. YouTube URL/source parsing, provider response normalization, and Rust↔UniFFI↔Compose Analyze behavior.
  2. SQLite durable queue/promotion and crash/startup reconciliation.
  3. Android foreground service / API 34+ UIDT scheduling / notification and network lifecycle.
  4. HTTP ranged-resume validation, partial files, integrity, and filesystem failures.
  5. Media3 session, offline playback, audio focus, missing assets, and subtitle/audio-track changes.
  6. Generated native/UniFFI boundary error and cancellation semantics on supported ABIs.
- Add concise coverage summaries and test-to-risk traceability to CI logs / job summary and durable documentation.
- Maintain existing deterministic fixtures and exact-SHA execution guards.

### Out of scope

- New user-facing features, changing source-provider policy, enabling unsupported ABIs, or broad production refactors solely to obtain a higher coverage percentage.
- Requiring live YouTube requests, credentials, or external-provider access in ordinary CI. Provider-contract tests must use sanitized, authorized local fixtures; an explicitly authorized live-source smoke remains a separate opt-in gate.
- Claiming physical Samsung Galaxy A54 acceptance from emulator tests; device-specific QA remains separate.
- Public distribution, license decisions, YouTube/service policy approval, or weakening security/release gates.

### Baseline already covered by existing tests (avoid redundant tests)

- `core/src/youtube.rs`, `core/src/youtube_extract.rs`, `core/src/youtube_source/tests.rs`: URL recognition, malformed/unavailable fixtures, normalized formats and captions.
- `core/src/download/tests/{transfer,resume_limits}.rs`: body disconnect/timeout, ENOSPC, malformed ranges, partial reuse, size limits.
- `core/src/persistence/tests.rs`, `core/src/startup_reconciliation.rs`: atomic promotion, stale compare-and-swap races, recovery, orphans, symlink protection.
- Android smoke on API 29, Android FGS timeout on API 35, cold-start, real-network E2E, and Compose/UniFFI Analyze tests already exist.

The implementer must inspect current tests before introducing a new case and identify the **specific untested behavior**. No numerical coverage percentage is inferred from test counts.

## 3. Coverage measurement contract (COV-100)

### Rust

- Run `cargo-llvm-cov` under the pinned Rust toolchain (`1.98.1` at review) and a **version-pinned, validated** `cargo-llvm-cov` installation with `llvm-tools-preview`. Use `--workspace --all-features` and produce LCOV plus human-readable totals; verify tool version compatibility before CI promotion.
- Example candidate invocation (adjust flags after validation):

  ```bash
  rustup component add llvm-tools-preview --toolchain 1.98.1
  cargo llvm-cov --workspace --all-features --lcov --output-path coverage/rust.lcov
  ```

- Include `core/src/` in the denominator; do not mask real gaps by excluding modules or by counting generated/third-party bindings as application logic. Record line and region/function/branch metrics **only if supported and accurately labeled** by the selected tool/version.
- Run coverage in a separate job or isolated target directory so ordinary cross-compilation/UniFFI caches cannot contaminate it.

### Kotlin JVM and Android instrumentation

- Evaluate **Kover** or another compatible JVM coverage plugin against the repo's actual Kotlin/Gradle/AGP versions (Gradle 9.6 and AGP 9.4 at review). Use a pinned compatible version; demonstrate that reports contain production Kotlin classes and exclude test/auto-generated code.
- Run the existing `testDebugUnitTest` suite and emit at least one machine-readable report (XML or LCOV) and totals by relevant package.
- Evaluate AGP/JaCoCo Android instrumentation coverage in a separate API 29 emulator lane, with explicit instrumented production classes, honest treatment of generated UniFFI/JNA and Android framework code, and evidence that a known production function increments coverage. Do **not** assume JVM report metrics include device execution.
- Never sum Rust and Kotlin percentages into an unweighted headline metric. Show separate denominators and modules.

### CI evidence, baseline and policy

- Record exact SHA, tool versions, test command, generated report locations, coverage denominators, line/branch percentage where meaningful, and uncovered high-risk functions.
- Make the **first** implementation report-only. If instrumentation cannot run reliably, label the component `unmeasured` rather than substituting 100% or suppressing errors; the coverage lane must still fail for genuine test failures.
- Preserve the existing `tests/test_actions_artifact_tag_policy.py` constraint: ordinary master pushes do not upload CI artifacts except the narrowly authorized debug APK case. Publish bounded coverage totals to logs and `GITHUB_STEP_SUMMARY`; only retain/upload detailed files on permitted refs or after an explicit, separately reviewed policy change.
- Proposed **longer-term** goals (not release gates until measured and approved): Rust core >=85% line coverage, critical durable state transitions >=90% line coverage, testable Kotlin business logic >=80% line coverage. Prioritize missing behavior over numerical target gaming; a threshold change requires a baseline and rationale.
- Report results for both the instrumented metrics and existing mandatory CI; coverage must never replace behavioral and E2E acceptance.

## 4. Required test workstreams

### COV-200 — YouTube source / Analyze pipeline (P0)

**Production targets:** `core/src/youtube.rs`, `core/src/youtube_source.rs`, `core/src/youtube_source/parse.rs`, `core/src/youtube_extract.rs`, `core/src/ffi_source.rs`, Android `AddResolverAvailabilityInstrumentedTest` and source gateway.

- Use a matrix of sanitized local JSON/HTML/provider-response fixtures: absent, null, wrong-type, duplicated, reordered, partially valid, and excessive fields; stream/caption metadata, signed URLs, alternate format IDs, and unsupported codecs.
- Verify HTTP status/error classification for 403, 404, 429, 5xx and malformed/truncated bodies using a local stub; do not equate temporary rate limiting with permanently unavailable media.
- Test selected quality/audio/subtitle ID correspondence through the provider-neutral plan and FFI records; never silently choose an unrelated duplicate-label stream.
- Prove cancellation before and during simulated source resolution is nonblocking and safely reported, and retries do not occur when `CoreError.retryable` is false.
- Android: verify Analyze is actionable for syntactically supported URLs, reports native startup failures visibly, canonicalizes URLs with query parameters, and prevents duplicate submissions without hiding actionable errors.
- No live-provider network dependency in regular CI. Preserve secret redaction and bounded output on all errors.

### COV-300 — SQLite / durable worker / recovery (P0)

**Production targets:** `core/src/persistence.rs`, `core/src/persistence/{rows,snapshots}.rs`, `core/src/worker.rs`, `core/src/startup_reconciliation.rs`, `core/src/ffi_download_*.rs`.

- Fault-inject transaction failures at distinct stages of enqueue/claim/stage/promote/cancel/finish, then reopen a **file-backed** database to assert consistent queue state, asset ownership, and absence of partial promotion.
- Verify older/future `schema_meta` values, damaged/truncated SQLite files, foreign-key/orphan behavior, and deterministic errors; never destroy a user library on incompatible schema.
- Exercise 2+ racing workers/owners over distinct SQLite connections, including cancel-versus-complete, duplicate schedule, stale lease/retry, and repeated process-death recovery.
- Reconcile with preseeded partials, staged assets, missing/corrupt files and symlinks; idempotence and preservation of user-paused/terminal states are invariants.
- Add focused regression for `rusqlite` bundled-SQLite upgrade boundary (0.41 / libsqlite3-sys 0.39): open, transaction, FK, migration and reopen roundtrip.
- Use controlled fault injection confined to tests; never add a production-accessible corruption toggle.

### COV-400 — Android execution / API matrix (P1)

**Production targets:** `DownloadExecutionScheduler.kt`, `DownloadForegroundService.kt`, notification state/control, `DownloadRebootReceiver`, `MainActivity` lifecycle.

- Extend API 29/35 evidence with targeted API **26, 33 and 34** checks; select emulator images available to GitHub Actions and document any unavailable tier instead of pretending it passed.
- Verify API 26-33 fallback versus API 34+ UIDT path, including failed scheduling, quota/restriction paths, and no duplicate durable work.
- Test permission denial/revocation, repeated notification pause/resume/cancel intents, killed service, timeout callback, reboot/process recreation, and rapid metered/unmetered/no-network transitions.
- Ensure `BOOT_COMPLETED` never directly starts an illegal `dataSync` foreground service or opens credential-encrypted state before unlock.
- Keep expensive expanded emulator matrix optional/periodic initially while mandatory API 29/35 gates remain unchanged; promote only after reliable execution/accurate evidence.

### COV-500 — HTTP transfer / resume / integrity (P1)

**Production targets:** `core/src/download.rs`, `core/src/download/{errors,integrity,library_paths}.rs`, `core/src/resume.rs`, `core/src/resume_http.rs`.

- Deterministically generate bounded `Range`, `Content-Range`, `Content-Length`, ETag, Last-Modified, 200/206/416 and truncated/chunked response combinations; use property-based tests with **fixed/replayable seeds**, bounded case count and runtime.
- Invariants: no unverified append, no accepted result with wrong SHA-256/length, no file escape, bounded bytes/memory, and safe restart when entity validators change or disappear.
- Interleave cancellation, mid-stream network failures, disk full/permission errors, and resume metadata corruption. Verify correct transient/permanent classification and partial cleanup policy.
- Extend existing transfer/resume_limits tests rather than duplicating their already-covered examples.

### COV-600 — Media3 offline playback (P1)

**Production targets:** `PlaybackSessionService.kt`, Media3 controller binding, local playback gateway, playback-position persistence and subtitle handling.

- Instrument offline fixtures with combined and split A/V, local subtitle tracks and unavailable/removed assets.
- Verify foreground/background/session recreation, audio-focus loss and regain, rapid play/pause/seek, source-switch and controller disconnect, and saved position after process restart.
- Confirm missing/corrupt local files fail visibly without network fetch or fictitious completion, and switching audio/subtitle tracks uses the selected local track.
- Check notification/headset/media-button semantics where supported by emulator API. Keep real-device audio-route/OS behavior in manual QA, not silently marked as automated.

### COV-700 — UniFFI native boundary (P1)

**Production targets:** `core/src/ffi.rs`, `core/src/ffi_source.rs`, `core/src/ffi_download_*.rs`, `GeneratedUniffi*Gateway` Kotlin files.

- Round-trip all supported FFI error kinds, nullable/unsigned values and boundary lengths; verify typed failures cannot be mistaken for empty success.
- Test repeated open/close, pre-cancellation and in-flight cancellation races; prove native loading occurs through production gateway initialization.
- Check native library/JNA packaging on both supported ABIs; execute on x86_64 emulator and **arm64 runtime when infrastructure/device is available**. Successful arm64 cross-build/packaging is **not** arm64 runtime coverage.
- Exercise native failure diagnostics while ensuring no URL credentials, tokens or signed query parameters leak.

## 5. Test quality, determinism and security

- Every test must assert observable *production behavior*, not merely the presence of code, a checkbox, or a mocked return value.
- External HTTP tests use loopback services and fixed fixtures. Test inputs must not require provider sign-in, user files, secrets or third-party network availability.
- Limit generated cases, CPU, disk, emulator startup and logs. A random/property test failure must print its reproducible seed and minimized case; maintain a small stable regression corpus in-repo.
- Do not weaken the existing mandatory smoke class/method evidence, golden SHA checks, network E2E, exact-HEAD CI policy, or tag-only artifact guard to get a green result.
- Check exception privacy: never expose raw provider response tokens or device identifiers in CI output.
- Favor focused unit/property tests for parser and state logic; use instrumentation only when lifecycle/platform behavior cannot be asserted at the Rust/JVM level.

## 6. Acceptance and release boundaries

A workstream is complete only after all items in the companion TODO are implemented, new tests are executed (not skipped), their assertions exercise the named production paths, and the **exact resulting SHA** passes its relevant CI workflow(s). Record file paths, test names, commands, run IDs, outcomes, uncovered branches, and any excluded device/API limitations.

The complete initiative additionally requires:

1. A reproducible Rust and Kotlin JVM coverage baseline, with separate totals; Android instrumented coverage is measured or explicitly documented as blocked with actionable follow-up.
2. Failure-path test matrices for COV-200 through COV-700 with no falsely marked tests or hand-waved hardware cases.
3. No regression in required existing CI, no unauthorized new artifact uploads, no new live-provider dependency in ordinary CI.
4. A dated final summary identifying residual low-coverage/risk areas and suggesting measured, approved gates rather than retroactively inventing target attainment.
5. Explicitly separate **engineering qualification** from Samsung A54 physical-device acceptance, licensing, source-service terms, and release authorization.

**Do not change the all-checked historical remediation checklist** to represent work under this specification; keep this new TODO's incomplete items unchecked until verified.
