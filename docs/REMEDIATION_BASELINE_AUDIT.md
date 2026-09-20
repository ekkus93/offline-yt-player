# Offline YT Player Remediation Baseline Audit

**Reviewed baseline:** `b8aebfd0467de67a2cc0b0a583d91f9a1783da7c`  
**Remediation spec:** `docs/OFFLINE_YT_PLAYER_REMEDIATION_SPEC_2026-09-17.md`  
**Remediation checklist:** `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`

## Purpose

This document freezes the post-closeout code-review findings and maps each finding to one or more remediation task IDs. It is evidence for RMD-001; it is not a replacement for the detailed remediation checklist.

Supersession and reconciliation notes are maintained in `docs/REMEDIATION_RECONCILIATION.md`. Historical audit and closeout claims remain available for traceability, but the detailed remediation TODO is the current authority when a historical claim conflicts with current implementation evidence.

The historical detailed v1 checklist remains available at commit `4c977462a1f0ff885484aa88f6936b8dcc2cbf2f`:

- `docs/OFFLINE_YT_PLAYER_TODO.md` at `4c977462a1f0ff885484aa88f6936b8dcc2cbf2f`

The prior reconciliation commit `550c1448dbe7d820630bdb794a5b0c2c27580d73` changed only that TODO file relative to its parent. The diff was 92 additions and 639 deletions (731 changed lines). That reconciliation changed checklist state without corresponding implementation changes for all newly claimed-complete items. The remediation checklist therefore remains detailed and requires implementation/test/SHA evidence before any task is closed.

## Finding inventory

| Review finding | Remediation task(s) |
| --- | --- |
| Android manifest lacks `android.permission.INTERNET`. | RMD-101 |
| APK does not package/use Rust native libraries and generated UniFFI Kotlin bindings. | RMD-201, RMD-202, RMD-203, RMD-204 |
| No live production YouTube `MediaSource`; fixture source is the only operational source. | RMD-301 through RMD-304 |
| Add/Paste/Analyze/Download production path contains no-ops or fabricated data. | RMD-701 through RMD-704 |
| Library and Downloads screens use hard-coded empty state instead of repositories. | RMD-601, RMD-602, RMD-604 |
| Foreground-service pause/resume/cancel/connectivity/reboot behavior is not operational. | RMD-501 through RMD-508 |
| Boot-time `dataSync` foreground-service design conflicts with modern Android restrictions. | RMD-102 through RMD-104 |
| `LOCKED_BOOT_COMPLETED` lacks a valid Direct Boot design. | RMD-102 |
| Android 15+ foreground-service timeout paths are unhandled where such services remain. | RMD-104 |
| Notification permission/degraded behavior is unspecified on Android 13+. | RMD-105 |
| Split audio/video is modeled but production playback ignores the audio asset. | RMD-903 |
| Playback UI and MediaSession service own different ExoPlayer instances. | RMD-901, RMD-902 |
| Playback-position persistence is not wired to the canonical player lifecycle. | RMD-906 |
| Thumbnail pipeline is incomplete. | RMD-801 |
| Subtitle persistence/download/playback pipeline is incomplete. | RMD-802, RMD-904 |
| Production metadata presentation still permits fake/static values. | RMD-803 |
| Settings UI is not durable or applied to runtime behavior. | RMD-1000 block |
| Android qualification relies on policy constants/source-text checks instead of rendered/runtime behavior. | RMD-1300 through RMD-1500 |
| Retry classification can retry errors whose `retryable` flag is false. | RMD-401 |
| `DownloadPolicy.max_attempts` is not authoritative in production orchestration. | RMD-402 |
| Response-body network failures can be misclassified as local I/O/storage failures. | RMD-403 |
| Deleting a library item can leave owned media assets behind. | RMD-404 |
| Persisted SHA-256 values are not used for later corruption validation. | RMD-405 |
| Raw HTTP client diagnostics can expose signed/sensitive URLs. | RMD-406 |
| Quality curation can retain an inferior same-height stream based on provider ordering. | RMD-407 |
| Concurrency/resource limits are duplicated across core and Android policy. | RMD-408 |
| Prior closeout compressed unresolved acceptance criteria into a checked summary. | RMD-001, RMD-002, RMD-1800 block |

## Evidence required before checking a remediation item

A remediation checkbox may be changed from unchecked to checked only when the reconciliation entry for that task identifies:

1. the production implementation path(s);
2. the behavioral test or explicit device-qualification evidence;
3. the exact implementation commit SHA;
4. the relevant CI run ID(s) or other bounded qualification evidence.

Documentation-only reconciliation may cite implementation that already exists, but it may not make an unresolved behavioral criterion complete by assertion.

## Tracking invariants

- The detailed remediation TODO is authoritative.
- A summary may be generated separately, but must not replace or compress detailed task/subtask state.
- No script may mutate or auto-check the remediation TODO.
- The release-closeout guard must fail while any unchecked checklist item remains.
- The external YouTube/service-policy/legal release gate remains separate from engineering completion.
