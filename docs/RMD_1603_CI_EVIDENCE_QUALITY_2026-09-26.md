# RMD-1603 CI Evidence Quality — 2026-09-26

The canonical completion checklist remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`. This note records the implementation evidence for the CI evidence-quality slice.

## Implementation

- `.github/workflows/ci.yml` already preserves bounded Rust, Android, UniFFI, APK, Gradle report, and test-output artifacts on failure.
- `.github/workflows/android-smoke.yml` already preserves bounded instrumentation reports, Android test results, screenshot/golden evidence, and managed-device additional output on every run.
- `.github/workflows/android-fgs-timeout.yml` already preserves bounded API-35 foreground-service timeout qualification evidence on every run.
- `.github/workflows/supply-chain.yml` preserves the generated Rust lockfile, Gradle dependency reports, and checked third-party notices.
- `.github/workflows/ci-evidence.yml` adds an explicit candidate evidence-manifest lane for every `master`, `ralph/**`, and PR candidate.
- `scripts/write_ci_evidence_manifest.py` generates a secret-free markdown manifest containing the exact candidate SHA, ref, event, workflow/run identity, tracked workflow files, and the artifact-retention policy used for final qualification review.

## Qualification intent

The RMD-1603 acceptance criteria require evidence quality, not a new product runtime behavior. The new manifest lane makes final RMD-1803 closeout auditable by preserving exact candidate identity and a bounded evidence policy alongside the existing workflow-specific failure and emulator artifacts.

Final release closeout must still cite the concrete candidate SHA and the specific passing run IDs for CI, Android smoke, Android FGS-timeout, Supply chain, screenshot/golden, deterministic fixture E2E, and any additional release gates required at that time.
