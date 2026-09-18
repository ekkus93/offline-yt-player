# OYP-2001 Full CI Matrix Audit

OYP-2001 is qualified by the repository's single exact-head CI workflow, `.github/workflows/ci.yml`.

## Matrix coverage

- **Rust fmt/clippy/test:** the `Rust fmt clippy test` job runs `cargo fmt --all -- --check`, workspace/all-feature clippy with warnings denied, and workspace/all-feature tests.
- **Kotlin/Android lint/test/build:** the `Android lint unit build` job runs `lintDebug`, `testDebugUnitTest`, and `assembleDebug` with pinned JDK/SDK inputs.
- **FFI generation/build:** the `UniFFI Kotlin and Android ABI` job regenerates Kotlin bindings and builds the portable core for representative `aarch64-linux-android`.
- **UI/golden qualification:** deterministic UI/golden policy tests live in the Android unit-test source set and therefore execute under `testDebugUnitTest`. This intentionally avoids nondeterministic emulator screenshot rendering while preserving the required screen/profile coverage contract.
- **E2E fixtures where the environment permits:** deterministic source/download/share/storage-failure E2E policy and fixture tests are also part of the Rust/Android test suites. Tests that require a real Android device or external live service are not falsely claimed by hosted CI; those boundaries remain explicit release/acceptance gates.

Every job first asserts that the checkout SHA equals `GITHUB_SHA`, so CI evidence is exact-head evidence rather than branch-name evidence.

## Qualification

The OYP-1903 predecessor master SHA `2f27e46311b7bf2d4ff9066e63952bf7dd4ca7fa` passed master CI run `35303380911`. OYP-2001 itself must additionally pass push CI, pull-request CI, and post-merge master CI at its resulting exact SHAs before this audit is used for final closeout.
