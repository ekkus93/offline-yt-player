# Offline YT Player — Implementation TODO

This checklist is the final reconciliation of `docs/OFFLINE_YT_PLAYER_SPEC.md` for the v1 engineering scope. The detailed historical acceptance criteria and per-milestone evidence remain in git history and the audit documents named below. A checked task means its implementation, automated qualification, documentation, and engineering reconciliation are complete.

Public/app-store distribution is **not** authorized by this engineering closeout. The human policy/legal review in `docs/YOUTUBE_POLICY_RELEASE_GATE.md` remains an external release gate.

## OYP-000 — Repository and project bootstrap
- [x] OYP-001 — Establish repository structure.
- [x] OYP-002 — Pin toolchains.
- [x] OYP-003 — Baseline CI.

## OYP-100 — Rust core foundation
- [x] OYP-101 — Domain models.
- [x] OYP-102 — Download state machine.
- [x] OYP-103 — Core event model.

## OYP-200 — Android portrait-only application shell
- [x] OYP-201 — Compose application.
- [x] OYP-202 — Portrait-only enforcement.
- [x] OYP-203 — Navigation shell.
- [x] OYP-204 — Fixed-region layout primitives.

## OYP-300 — Rust/Kotlin FFI
- [x] OYP-301 — Select and configure FFI.
- [x] OYP-302 — Stable coarse-grained API.
- [x] OYP-303 — FFI qualification.

## OYP-400 — Persistence and offline library
- [x] OYP-401 — SQLite schema.
- [x] OYP-402 — Atomic library completion.
- [x] OYP-403 — Library repository API.
- [x] OYP-404 — Migration tests.

## OYP-500 — Generic download engine
- [x] OYP-501 — HTTP transfer foundation.
- [x] OYP-502 — Pause/resume.
- [x] OYP-503 — Retry policy.
- [x] OYP-504 — Integrity and completion.
- [x] OYP-505 — Cleanup.
- [x] OYP-506 — Download test server.

## OYP-600 — Source abstraction
- [x] OYP-601 — `MediaSource` contract.
- [x] OYP-602 — Source registry.
- [x] OYP-603 — Direct/local fixture adapter.

## OYP-700 — YouTube source adapter
- [x] OYP-701 — Extraction strategy spike.
- [x] OYP-702 — URL recognition.
- [x] OYP-703 — Metadata resolution.
- [x] OYP-704 — Format normalization.
- [x] OYP-705 — Adapter diagnostics.
- [x] OYP-706 — Policy/legal release gate engineering controls and documentation. Human approval remains external and unresolved.

## OYP-800 — Media format selection and local asset strategy
- [x] OYP-801 — Compatibility policy.
- [x] OYP-802 — Adaptive audio/video assets.
- [x] OYP-803 — Muxing decision gate.

## OYP-900 — Media3 offline playback
- [x] OYP-901 — Local player integration.
- [x] OYP-902 — Portrait player screen.
- [x] OYP-903 — Playback position.
- [x] OYP-904 — MediaSession.
- [x] OYP-905 — Offline playback qualification.

## OYP-1000 — Android download service and notifications
- [x] OYP-1001 — Foreground download service.
- [x] OYP-1002 — Notifications.
- [x] OYP-1003 — Network policy.
- [x] OYP-1004 — Process death/reboot recovery.

## OYP-1100 — Android Share flow
- [x] OYP-1101 — Share intent.
- [x] OYP-1102 — Share-to-download UX.

## OYP-1200 — Thumbnails, subtitles, and metadata
- [x] OYP-1201 — Thumbnails.
- [x] OYP-1202 — Subtitles.
- [x] OYP-1203 — Metadata presentation.

## OYP-1300 — Library UX
- [x] OYP-1301 — Library screen.
- [x] OYP-1302 — Library item actions.
- [x] OYP-1303 — Compact-screen validation.

## OYP-1400 — Downloads UX
- [x] OYP-1401 — Downloads screen.
- [x] OYP-1402 — Download row actions.

## OYP-1500 — Add and Download Setup UX
- [x] OYP-1501 — Add screen.
- [x] OYP-1502 — Download Setup.
- [x] OYP-1503 — Advanced options subpage.

## OYP-1600 — Settings UX
- [x] OYP-1601 — Settings hub.
- [x] OYP-1602 — Download settings.
- [x] OYP-1603 — Playback settings.
- [x] OYP-1604 — Storage settings.
- [x] OYP-1605 — Appearance settings.
- [x] OYP-1606 — About.

## OYP-1700 — Design-system and UI qualification
- [x] OYP-1701 — Midnight Transit theme.
- [x] OYP-1702 — Deterministic screenshot/golden coverage contract.
- [x] OYP-1703 — No-hidden-controls gate.
- [x] OYP-1704 — Accessibility.

## OYP-1800 — Security, privacy, and resilience
- [x] OYP-1801 — Untrusted input hardening.
- [x] OYP-1802 — Secret/log hygiene.
- [x] OYP-1803 — Resource bounds.
- [x] OYP-1804 — Corruption/recovery.

## OYP-1900 — End-to-end qualification
- [x] OYP-1901 — Deterministic E2E fixture flow.
- [x] OYP-1902 — Share E2E.
- [x] OYP-1903 — Storage failure E2E.

## OYP-2000 — CI, release engineering, and supply chain
- [x] OYP-2001 — Full CI matrix.
- [x] OYP-2002 — Dependency/license checks.
- [x] OYP-2003 — Reproducible release metadata.

## OYP-2100 — Documentation
- [x] OYP-2101 — README.
- [x] OYP-2102 — Architecture decisions.
- [x] OYP-2103 — User-facing documentation.

## OYP-2200 — Cross-platform readiness
- [x] OYP-2201 — Core portability audit.
- [x] OYP-2202 — Portable library-format contract.

No iOS/desktop UI is required for v1.

## OYP-2300 — v1 engineering closeout
- [x] OYP-2301 — TODO reconciliation: every v1 engineering task is reconciled here; there are no stale partial checkbox states.
- [x] OYP-2302 — Exact-head qualification: the complete CI matrix passed on the pre-closeout candidate `ac986c16f38fb8141b3f230cf987732bb5b3f38f` in run `35306407218`; the closeout audit merge `4c977462a1f0ff885484aa88f6936b8dcc2cbf2f` passed post-merge master CI in run `35307165689`. This reconciliation change must likewise pass push, PR, and post-merge master CI.
- [x] OYP-2303 — Portrait UX audit: portrait-only, no hidden primary controls, no horizontal control scrolling, and bounded content scrolling are covered by deterministic policies/tests.
- [x] OYP-2304 — Offline acceptance: deterministic fixtures cover reconstruction, local metadata/thumbnail/subtitle availability, and representative local playback with networking unavailable.
- [x] OYP-2305 — Release gate: known limitations and policy state are documented; public/app-store distribution remains blocked until required human policy/legal review is resolved.

## Closeout evidence

The authoritative closeout narrative is `docs/V1_ENGINEERING_CLOSEOUT_AUDIT.md`. Supporting evidence includes the milestone-specific `*_AUDIT.md` and qualification documents, `docs/RELEASE.md`, `docs/USER_GUIDE.md`, `docs/ARCHITECTURE_DECISIONS.md`, `docs/PORTABLE_LIBRARY_FORMAT.md`, and `docs/YOUTUBE_POLICY_RELEASE_GATE.md`.

Engineering completion does not imply external legal, service-policy, app-store, or live-device approval. Those boundaries are explicitly documented rather than represented as unfinished engineering TODOs.
