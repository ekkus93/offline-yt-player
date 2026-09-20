# Rust advisory exceptions

The default release policy is **zero ignored RustSec advisories**. The CI gate runs `cargo audit --deny warnings` without ignore arguments, so a vulnerability, unmaintained crate warning, or other RustSec warning fails qualification.

If an exception is ever required, it must be reviewed explicitly in a pull request. The review must record the RustSec advisory ID, affected dependency/version, why the shipped runtime is not exploitable or why no viable upgrade exists, compensating controls, reviewer, and an expiry/removal condition. Only after that review may the advisory ID be added as an explicit `--ignore RUSTSEC-YYYY-NNNN` argument in `.github/workflows/rust-advisory.yml`.

Blanket ignores, wildcard suppression, and undocumented exceptions are prohibited. This file intentionally contains no active exceptions.
