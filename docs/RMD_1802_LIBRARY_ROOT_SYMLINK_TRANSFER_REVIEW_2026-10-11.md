# RMD-1802 — Symlinked download-library root containment (2026-10-11)

## Independent production finding

`core/src/asset_validation.rs` rejects a symlinked library root, but `core/src/download.rs::canonical_library_root` calls `create_dir_all` and `canonicalize` without verifying the root itself. A symlink from the configured library root to another directory would be accepted as the canonical ownership boundary. Consequently `transfer` could write or `cleanup_orphan_partials` could remove files outside the intended private library.

## Required correction and deterministic regression

Reject a symbolic-link or non-directory root before using its canonical path for write and cleanup containment. Add a Unix regression asserting that transfer and cleanup both fail closed with `InvalidInput`, preserve an external partial, and do not create a final external asset. Preserve legitimate-directory and symlinked-parent behavior.

## Acceptance discipline

This is a newly identified review issue, not implementation or final RMD-1802/RMD-1803 signoff. Keep engineering closeout unchecked until the correction is committed, qualified by exact-head CI, independently reviewed, and reconciled in the canonical TODO. Human service/legal approval remains a separate gate.
