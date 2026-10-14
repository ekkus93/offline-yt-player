# RMD-1802 — Relative Library root-alias path validation (2026-10-14)

## Independent review finding

At exact `master` `132084374aeae0806827e1f7136df64b1b8b7f8d`, `core/src/security.rs::validate_relative_library_path` rejected empty strings, absolute paths, parent traversal and NUL bytes, but accepted the nonempty relative paths `.`, `./` and `././`. These are not relative media-file paths: filesystem resolution identifies the Library root itself. Any caller that relies on the validator as its only boundary check could mistakenly treat the Library directory as a valid individual asset path. This is a path-validation contract weakness; no exploit or destructive caller behavior is claimed without separate proof.

## Correction and regression

Require at least one normal path component in addition to the existing checks. Keep valid `./items/abc/video.mp4` relative asset paths accepted. Extend the existing executable Rust `security::tests::path_traversal_is_rejected` regression with the three root aliases and a valid dot-prefixed relative file path. No UI behavior or external service policy is changed.

## Qualification

Source and regression are committed to `master` with a compare-and-swap head check. Run Rust formatting/clippy/tests and all eight exact-head CI workflows before citing this SHA as qualified. Do not mark RMD-1802, RMD-1803, RMD-1805 or any global proof checkbox complete from this bounded correction. The independent production review and human service/legal release gate remain separate.
