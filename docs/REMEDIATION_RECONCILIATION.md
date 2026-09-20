# Offline YT Player — Remediation Reconciliation and Supersession

This document identifies historical audit/closeout claims that are no longer authoritative for engineering status. It supports RMD-1706 by preserving the historical record while making the current authority explicit.

## Current authority

The authoritative engineering checklist is:

- `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`

The normative remediation specification is:

- `docs/OFFLINE_YT_PLAYER_REMEDIATION_SPEC_2026-09-17.md`

The baseline finding inventory is:

- `docs/REMEDIATION_BASELINE_AUDIT.md`

A task is not complete merely because an older audit, old TODO, summary, source comment, policy constant, or documentation paragraph says it is complete. Completion requires production implementation, behavioral evidence, exact implementation SHA, and CI/device evidence as specified by the remediation TODO.

## Superseded historical closeout claims

The historical detailed v1 checklist remains available in git history at commit `4c977462a1f0ff885484aa88f6936b8dcc2cbf2f` as `docs/OFFLINE_YT_PLAYER_TODO.md`. It is useful historical context, but it is not the current closeout authority.

The prior reconciliation commit `550c1448dbe7d820630bdb794a5b0c2c27580d73` changed checklist state without corresponding implementation proof for every newly claimed-complete item. Any closeout claim from that reconciliation is superseded by the remediation TODO unless it has since been re-proven with production implementation and exact-head evidence.

The current repository also contains narrower RMD audit notes, for example network capability, worker pause/resume/cancel/retry/progress, connectivity, and live YouTube qualification notes. Those documents are implementation evidence only for the specific scope they describe. They are not substitutes for unchecked downstream acceptance tasks such as Android E2E, instrumentation, durable orchestration, release qualification, or external legal approval.

## Corrected claim categories

The remediation track has corrected several categories of misleading prior claims:

| Prior claim pattern | Current interpretation |
| --- | --- |
| A checked TODO line proves a feature is complete. | False. The detailed remediation TODO requires implementation paths, behavioral tests, exact SHA, and CI/evidence before closure. |
| Policy constants or source text prove runtime behavior. | False. Runtime behavior needs production wiring and behavioral evidence. |
| Fixture-only resolution proves production provider support. | False. Fixture sources are deterministic tests; production support requires the registered production source adapter and qualification evidence. |
| Android manifests or service declarations prove compliant background behavior. | False. API-level scheduler behavior, notification behavior, boot behavior, and timeout behavior require tests or device/emulator qualification. |
| UI previews or fake gateways prove real Add/Share/Library/Downloads behavior. | False. Production repositories, gateways, and durable state must drive user-facing workflows. |
| Documentation can close unresolved engineering work. | False. Documentation can clarify status, but it cannot complete runtime acceptance criteria. |
| CI success implies external source-service/legal approval. | False. The external legal/source-service release gate is separate and remains unresolved unless a human authority records approval. |

## Documents added during reconciliation

The following documents reconcile current status without changing detailed checklist state:

- `README.md` — truthful project status, supported/unsupported workflow summary, build prerequisites, and external release gate.
- `docs/ANDROID_RUST_FFI_BUILD.md` — Android ABI, UniFFI, emulator/device, and native-loading troubleshooting notes.
- `docs/ANDROID_BACKGROUND_EXECUTION.md` — API-level background execution model and remaining durable-runtime limits.
- `docs/USER_GUIDE.md` — operational user workflow, offline playback limits, and recovery/error state guidance.
- `docs/SECURITY_PRIVACY_LEGAL.md` — input/security model, diagnostic redaction, storage/deletion behavior, privacy expectations, and external legal gate.

These documents intentionally preserve limitations and unresolved gates. They do not auto-check RMD-1700 or downstream RMD-1800 closeout tasks.

## How to use historical audits

Use historical audits as evidence only when the cited claim still matches current `master` implementation and has fresh qualification. If a historical audit conflicts with the remediation TODO, the remediation TODO wins.

When closing a task, cite the smallest current evidence set:

1. production implementation path;
2. behavioral test or bounded manual/device qualification;
3. exact implementation SHA;
4. exact CI run ID or other recorded evidence.

Do not delete older audits merely because they were misleading. Keep them available for traceability and supersede them with a current reconciliation note such as this document.

## Remaining closeout boundary

This supersession document does not perform final reconciliation. RMD-1801 through RMD-1805 still require a complete review of every detailed checkbox, a post-remediation code review, exact-head full qualification, post-merge verification, and preservation of the detailed TODO.