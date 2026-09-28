# RMD-1603 CI Evidence Quality — 2026-09-26

The canonical completion checklist remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`. This note records the implementation evidence for the CI evidence-quality slice.

## Implementation

- `.github/workflows/ci.yml` already preserves bounded Rust, Android, UniFFI, APK, Gradle report, and test-output artifacts on failure.
- `.github/workflows/android-smoke.yml` already preserves bounded instrumentation reports, Android test results, screenshot/golden evidence, and managed-device additional output on every run.
- `.github/workflows/android-fgs-timeout.yml` already preserves bounded API-35 foreground-service timeout qualification evidence on every run.
- `.github/workflows/supply-chain.yml` preserves the generated Rust lockfile, Gradle dependency reports, and checked third-party notices.
- `.github/workflows/ci-evidence.yml` adds an explicit candidate evidence-manifest lane for every `master`, `ralph/**`, and PR candidate.
- `scripts/write_ci_evidence_manifest.py` generates a secret-free markdown manifest containing the exact candidate SHA, ref, event, workflow/run identity, tracked workflow files, and the artifact-retention policy used for final qualification review.

## Exact-head evidence

The RMD-1603 implementation was landed directly on `master` in small, exact-head-gated commits:

- `dca1dc5a7a7fb9a312913ee2c8f02353822115d0` added this RMD-1603 evidence note and passed CI `36347477860`, Supply chain `36347477852`, Android FGS timeout `36347477850`, and Android smoke `36347477887`.
- `187491d65b6a20e68adb2cd5c8d80bfbe5271ddd` added `scripts/write_ci_evidence_manifest.py` and passed CI `36352838908`, Supply chain `36352838888`, Android FGS timeout `36352838903`, and Android smoke `36352838896`.
- `57373c2440eb26191c8c9de9ef8d4ca2d29fd315` added `.github/workflows/ci-evidence.yml` and passed CI `36353930305`, CI evidence `36353930218`, Supply chain `36353930273`, Android FGS timeout `36353930237`, and Android smoke `36353930296`.

## Qualification intent

The RMD-1603 acceptance criteria require evidence quality, not a new product runtime behavior. The manifest lane makes final RMD-1803 closeout auditable by preserving exact candidate identity and a bounded evidence policy alongside the existing workflow-specific failure and emulator artifacts.

Final release closeout must still cite the concrete candidate SHA and the specific passing run IDs for CI, Android smoke, Android FGS-timeout, Supply chain, screenshot/golden, deterministic fixture E2E, and any additional release gates required at that time.
