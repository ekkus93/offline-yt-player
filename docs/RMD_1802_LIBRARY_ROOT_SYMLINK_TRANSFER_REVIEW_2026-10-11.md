# RMD-1802 — Symlinked download-library root containment (2026-10-11)

## Independent production finding

`core/src/asset_validation.rs` rejects a symlinked library root, but `core/src/download.rs::canonical_library_root` calls `create_dir_all` and `canonicalize` without verifying the root itself. A symlink from the configured library root to another directory would be accepted as the canonical ownership boundary. Consequently `transfer` could write or `cleanup_orphan_partials` could remove files outside the intended private library.

## Required correction and deterministic regression

Reject a symbolic-link or non-directory root before using its canonical path for write and cleanup containment. Add a Unix regression asserting that transfer and cleanup both fail closed with `InvalidInput`, preserve an external partial, and do not create a final external asset. Preserve legitimate-directory and symlinked-parent behavior.

## Acceptance discipline

This is a newly identified review issue, not implementation or final RMD-1802/RMD-1803 signoff. Keep engineering closeout unchecked until the correction is committed, qualified by exact-head CI, independently reviewed, and reconciled in the canonical TODO. Human service/legal approval remains a separate gate.

## Implementation status and further independent finding

Commit `a555cce60f6bdb18005b211f84db6ccfee990858` changes `core/src/download.rs` to reject a symlinked or non-directory library root before transfer and partial cleanup, and adds `transfer_and_cleanup_reject_symlinked_library_root_without_touching_outside` and `transfer_and_cleanup_reject_non_directory_library_root`. Exact-head CI qualification is pending; this is not RMD-1802 closeout.

A separate destructive production path remains vulnerable: `core/src/deletion.rs::delete_library_item_owned_assets` calls `std::fs::canonicalize(library_root)` without inspecting `symlink_metadata(library_root)`. With a symlinked configured root, the canonical root is the external directory, so parent containment checks can authorize deleting external owned-file paths. Required correction: reject symlinked and non-directory deletion roots before canonicalization; add a Unix regression with a symlinked root proving external files and persisted Library metadata survive, plus a non-directory-root case. The source correction is not merged. Do not close review or engineering qualification until it lands and passes exact-head tests.
