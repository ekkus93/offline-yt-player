# Supply-chain review policy

The canonical remediation checklist is `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`. This policy supports RMD-1602 by defining the required advisory and license-review gates for release qualification.

## Required automated gates

- Rust crates are scanned with `cargo audit --deny warnings` against a generated `Cargo.lock` in `.github/workflows/supply-chain.yml`.
- Android/Gradle runtime and instrumentation dependency graphs are exported in `.github/workflows/supply-chain.yml` as bounded review artifacts.
- `docs/THIRD_PARTY_NOTICES.md` is generated from the Gradle version catalog and Rust manifests by `scripts/generate_third_party_notices.py` and must remain checked in.
- The supply-chain workflow uploads the generated Rust lockfile, Gradle dependency reports, and checked notices as evidence artifacts.

## Exception process

No unresolved security advisory, prohibited license, or license-incompatible dependency may be ignored silently.

Every exception must include all of the following before release qualification:

1. Dependency name and version.
2. Advisory ID or license issue.
3. Reason the dependency remains acceptable for the candidate release.
4. Mitigation or replacement plan.
5. Explicit expiry condition or follow-up issue.

Exceptions belong in a dated `docs/SUPPLY_CHAIN_EXCEPTION_*.md` file and must be referenced from the final RMD-1803 closeout audit. The external YouTube/service-policy/legal approval gate remains separate and cannot be satisfied by this engineering policy.
