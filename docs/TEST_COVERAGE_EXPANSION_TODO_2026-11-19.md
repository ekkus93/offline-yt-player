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

- [ ] **COV-001** Record exact `master` SHA and latest full CI matrix; distinguish pre-existing qualifications from this track.
- [ ] **COV-002** Inventory production Rust modules (`core/src`) and Android packages (`app/src/main`) and map current unit, fixture, integration and instrumentation tests.
- [ ] **COV-003** Map highest-risk uncovered *functions/branches*, not just file count, to COV-200–700. Explicitly note tests that already cover an edge case.
- [ ] **COV-004** Document baseline CI job durations, test stability and artifact-upload restrictions before expanding emulator jobs.
- [ ] **COV-005** Create `docs/TEST_COVERAGE_BASELINE_2026-11-19.md` (or a dated successor if run later), listing unknown metrics as **unmeasured** until actual reports exist.

**Acceptance:** committed inventory traces at least one measured or explicitly unmeasured finding to each risk area; no invented percentages.

**Evidence:** _pending._

## COV-100 — Coverage measurement infrastructure (P0; implement first)

- [ ] **COV-101** Pin `cargo-llvm-cov` and `llvm-tools-preview` compatible with the repository's Rust toolchain. Capture version and command in CI.
- [ ] **COV-102** Add an isolated, reproducible Rust coverage job/script exercising the workspace; generate LCOV plus readable core-only line totals and supported region/branch metrics.
- [ ] **COV-103** Confirm Rust denominator covers actual `core/src` logic, excluding tests, generated UniFFI bindings and external crates without silently hiding real source modules.
- [ ] **COV-104** Evaluate/pin compatible Kotlin JVM coverage instrumentation (Kover or justified equivalent); wire `testDebugUnitTest` and produce validated machine-readable per-package results.
- [ ] **COV-105** Confirm Android/JVM denominator excludes generated bindings/test code and includes key production `downloads`, UI-state and gateway logic.
- [ ] **COV-106** Prototype Android instrumentation coverage on API 29 in a *separate* lane, verifying counters for a known app production path; if incompatible, document blocker and keep task open.
- [ ] **COV-107** Publish bounded Rust/JVM totals with exact SHA and tool versions in logs and `GITHUB_STEP_SUMMARY`; preserve tag-only artifact uploads except existing narrowly permitted cases.
- [ ] **COV-108** Add host-side tests validating coverage report parsing/denominators; missing/empty reports must be reported as errors or `unmeasured`, never 100%.
- [ ] **COV-109** Record measured baseline per component/package/function. Keep thresholds **report-only** initially and document proposed (not enforced) Rust 85%, critical state 90%, Kotlin business logic 80% line goals.
- [ ] **COV-110** Run exact-head standard CI plus coverage lane; record separate per-workflow evidence and verify no unauthorized artifact uploads or altered existing smoke acceptance.

**Acceptance:** repeatable numeric Rust and JVM totals from production code, distinct Android instrumentation status, exact-SHA evidence, and no fake green report on missing data.

**Evidence:** _pending._

## COV-200 — YouTube source and Analyze resiliency (P0)

- [ ] **COV-201** Extend sanitized `youtube_source` / `youtube_extract` fixtures for missing/null/mismatched, duplicated and reordered provider fields, with exact valid/invalid outcomes.
- [ ] **COV-202** Add 403/404/429/5xx and truncated body fixture tests; verify typed permanent/retryable classification, timeouts, bounds and no erroneous `Unavailable` success.
- [ ] **COV-203** Add option-ID fidelity tests across duplicate quality labels, split A/V, audio and subtitle choices, including provider-neutral download plan mapping.
- [ ] **COV-204** Exercise source resolution canceled before start and during controlled simulated blocking I/O; assert no stuck worker, duplicate request or false completion.
- [ ] **COV-205** Extend Android Add/Analyze behavioral tests for plain/share/query-parameter URLs, native initialization error visibility, duplicate Analyze taps and typed error recovery.
- [ ] **COV-206** Add adversarial malformed/oversized metadata and signed-query-token fixtures; prove bounded output and error/log redaction through FFI/UI.
- [ ] **COV-207** Run target Rust tests, exact-head API 29 Android smoke/required-method evidence and overall CI; note live-source/hardware exclusions separately.

**Acceptance:** deterministic fixture tests prove parser/adapter/FFI/UI behaviors without live YouTube dependency; original Analyze regressions remain covered.

**Evidence:** _pending._

## COV-300 — SQLite durability, worker races and startup repair (P0)

- [ ] **COV-301** Add file-backed SQLite transaction fault tests for enqueue, claim, stage, promote, cancellation, and completion boundaries, including reopen/recovery assertions.
- [ ] **COV-302** Cover future schema version, malformed/corrupt DB, foreign-key/orphan data and failed migration; verify fail-closed errors and no destructive fallback.
- [ ] **COV-303** Exercise concurrent independent DB connections/workers racing to claim and settle the same job; verify a single authoritative winner.
- [ ] **COV-304** Add cancel-vs-complete, pause-vs-retry, stale worker, and duplicate schedule races without nondeterministic sleeps; use barriers or test-controlled clocks.
- [ ] **COV-305** Add repeated startup reconciliation/partial-staged-file tests, including missing assets, symlinks, stale lease and crash-between-checkpoint boundaries; prove idempotence and outside-root safety.
- [ ] **COV-306** Add a bundled-SQLite upgrade regression smoke for `rusqlite 0.41.x`: file open/migrate, FK enforcement, transaction, snapshot persisted after reopen.
- [ ] **COV-307** Run new Rust tests under normal CI and coverage; document observed durable-state invariant and exact-head CI.

**Acceptance:** no double promotion/completion, silent state loss or recovery escape; all fault injections are test-only.

**Evidence:** _pending._

## COV-400 — Android service, notification and API-level matrix (P1)

- [ ] **COV-401** Audit existing API 29/35 tests, and establish feasible API 26/33/34 emulator profiles and bounded execution strategy.
- [ ] **COV-402** Add API 26-33 foreground fallback and API 34+ UIDT scheduling behavior tests; assert actual scheduling inputs and failure/restriction cases.
- [ ] **COV-403** Add notification-permission granted/denied/revoked and repeated pause/resume/cancel notification-action tests backed by durable queue state.
- [ ] **COV-404** Add process/service recreation and timeout callback tests ensuring legal teardown, noncancel platform stop, no duplicate worker and recovery ordering.
- [ ] **COV-405** Extend host-controlled network E2E for quick offline/online and metered/unmetered transitions; verify Wi-Fi-only classification and exactly-once resume behavior.
- [ ] **COV-406** Cover reboot-before-unlock restrictions and no direct boot-time dataSync FGS launch on applicable APIs, with an executable behavioral test.
- [ ] **COV-407** Execute selected API 26, 33, 34 and existing 29/35 runs; record actual APIs exercised, links, skipped/unavailable tiers and flaky retry evidence.
- [ ] **COV-408** Retain hardware-only A54 battery/network behavior as separately documented pending device validation; do not check it as automatically passed.

**Acceptance:** executable platform behavior checked at named API levels; existing workflows remain required and intact.

**Evidence:** _pending._

## COV-500 — HTTP resume, integrity and filesystem adversaries (P1)

- [ ] **COV-501** Inventory `core/src/download/tests/transfer.rs` and `resume_limits.rs`; avoid re-adding existing 200/206/416, ENOSPC and body-disconnect cases.
- [ ] **COV-502** Add reproducible bounded property tests over Range/Content-Range, validators, content length and chunked/truncated response combinations using a local HTTP stub.
- [ ] **COV-503** Assert unsafe combinations always restart or fail (never append unverifiable bytes); output failing seed/input for replay.
- [ ] **COV-504** Inject mid-write disk failures, cancellation and malformed resume metadata; verify retained/removed partials match policy and no accepted corrupt SHA/length.
- [ ] **COV-505** Check no path traversal, symlink escape, excessive writes or unbounded memory from generated inputs; retain minimized adversarial fixtures.
- [ ] **COV-506** Run deterministic tests repeatedly in CI; record coverage delta, exact-head Rust job and accepted invariants.

**Acceptance:** zero accepted corrupt/resumed outputs across bounded adversarial cases; reproducible seeds in failures.

**Evidence:** _pending._

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
- [ ] **COV-704** Prove `liboffline_yt_core.so` + JNA packaging for both arm64-v8a and x86_64 and runtime tests on x86_64 emulator.
- [ ] **COV-705** Execute arm64 runtime smoke on emulator/hardware if feasible; otherwise record this as an explicit unverified runtime-ABI gap, distinct from cross-build.
- [ ] **COV-706** Validate safe diagnostic redaction through the boundary and Android UI; run exact-head generated-binding/ABI build and Android smoke.

**Acceptance:** typed errors/cancel/cleanup survive boundary crossings, without fabricated data, token leakage or false arm64 runtime claims.

**Evidence:** _pending._

## COV-800 — Final evidence and gate policy (P2, after implementation)

- [ ] **COV-801** Recompute actual per-module Rust and Kotlin coverage and compare with COV-100 baseline; report line/branch deltas without conflating languages.
- [ ] **COV-802** Map each added test to risk, executed production path, SHA and CI run, and identify remaining high-risk uncovered branches.
- [ ] **COV-803** Assess whether to enforce a narrow nonregression threshold on stable Rust/JVM packages; **do not** enable it until baseline, stability and developer approval are documented.
- [ ] **COV-804** Run full required exact-head CI including Android smoke, FGS timeout, supply chain, deterministic E2E, cold-start and real-network E2E; failures must be fixed or remain open.
- [ ] **COV-805** Verify tag-only artifact policy still passes, no live-source dependency exists in mandatory lanes, and no security/privacy exceptions were broadened.
- [ ] **COV-806** Publish a dated coverage closeout note with report locations, tool versions, CI run IDs, confirmed gaps and any pending Samsung A54/human release gates.
- [ ] **COV-807** Check this task only after all remaining actionable subtasks are verified; do not mark the initiative done if device/API or measurement requirements are still blocked.

**Acceptance:** measured, reproducible coverage baseline and improvement, behavioral proof with exact-head CI, and honest residual-risk reporting.

**Evidence:** _pending._

---

## Recommended implementation order

COV-000 → COV-100 → COV-200 → COV-300 → COV-500 → COV-400 → COV-600 → COV-700 → COV-800.

This is a priority recommendation, not permission to skip independent work or mark tasks complete without test execution. If an emulator, arm64 runtime or external dependency is genuinely blocked, document the blocker and continue with independent Rust/JVM work.

## Completion record

- **Current baseline SHA at document creation:** `e946a24b9939c03a7f77f667705a1f888ae44771`.
- **Implemented subtasks:** none in this new track.
- **Coverage percentages:** unknown until COV-100.
- **CI evidence for new test changes:** none yet; documentation-only commit must receive its own exact-head checks.
