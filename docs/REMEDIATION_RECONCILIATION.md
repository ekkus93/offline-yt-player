# Remediation reconciliation

This document explains how the 2026-09-17 remediation track supersedes earlier engineering-closeout claims without deleting their historical evidence.

## Authority

The authoritative engineering checklist is `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`, with requirements defined by `docs/OFFLINE_YT_PLAYER_REMEDIATION_SPEC_2026-09-17.md`. Earlier TODOs, audits, screenshots, CI reports, and closeout summaries remain useful historical records, but they are not authoritative evidence that a capability is complete under the remediation acceptance criteria.

The remediation baseline audit (`docs/REMEDIATION_BASELINE_AUDIT.md`) records why the earlier closeout state was reopened: checklist state had advanced beyond the production implementation and behavioral evidence for a number of user-facing capabilities.

## Claims corrected by remediation

The remediation track requires production-path proof rather than policy/declarative proof. In particular, prior claims are superseded where they relied on any of the following without corresponding production wiring and behavioral qualification:

- fixture or preview data standing in for production source resolution or repository state;
- policy enums, constants, booleans, source-string tests, or no-op UI callbacks standing in for executable behavior;
- Rust/UniFFI artifacts built in CI but not packaged and called through the production Android application;
- download controls or background-execution declarations without one durable queue/control model driving real worker execution;
- playback UI behavior without one MediaSession-owned canonical player and local split-A/V/subtitle behavior where required;
- Android layout/accessibility/E2E assertions that did not render and exercise the actual application on instrumentation infrastructure;
- documentation or checklist edits that asserted completion without exact-head implementation and CI evidence.

The detailed remediation TODO intentionally keeps these areas unchecked until their own acceptance evidence is reconciled. A later implementation may repair an earlier defect, but the historical audit that originally claimed closeout is not retroactively rewritten as correct.

## Historical documents

Historical audit and TODO documents are retained for provenance. When a historical document conflicts with the remediation spec/TODO about current engineering completion, use the remediation documents as the current authority. Git history remains the source for the exact content and context of prior closeout claims.

This repository must not delete or compress the historical records merely to make current status look cleaner. The remediation checklist's anti-false-closeout guard remains the mechanism that prevents an unresolved detailed task from being hidden by a summary.

## External release gate

Engineering reconciliation does not approve YouTube terms/service-policy/legal release questions. Those remain an external human gate. No engineering document, CI run, or remediation checkbox should be interpreted as legal or service-policy approval.

## Final reconciliation rule

At final engineering closeout, RMD-1801 through RMD-1805 must reconcile the current `master` implementation, behavioral evidence, exact candidate SHA, required CI matrix, and post-merge verification. Until then, this document only establishes which status source is authoritative; it does not itself close implementation tasks.
