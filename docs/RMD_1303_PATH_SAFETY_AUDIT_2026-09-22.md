# RMD-1303 path-safety audit

This audit starts `RMD-1303 — File/path safety for all mutations` from `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Current safe paths already present

The current implementation already contains several path-safety controls:

- `core/src/security.rs::validate_relative_library_path` rejects empty, absolute, parent-directory, root, and platform-prefix paths.
- `core/src/deletion.rs::delete_library_item_owned_assets` validates persisted asset paths and verifies the canonicalized asset parent stays under the canonical library root before deleting owned files.
- `core/src/thumbnail.rs` builds thumbnail paths from safe segments and rejects unsafe item IDs/extensions.
- `core/src/subtitle.rs` builds subtitle paths from safe item/track/language/format segments and filters unsafe persisted subtitle paths before local playback exposure.
- `core/src/ffi_library_rename.rs` is metadata-only and does not mutate filesystem paths.

## Gap found

`core/src/download.rs::DownloadEngine::transfer` validates the requested relative path syntactically, but it currently joins it to `library_root` and creates/opens the destination/partial path without canonical parent verification. That leaves two concrete mutation risks to harden:

1. A symlinked parent directory under the library root can redirect the partial write outside the managed root.
2. `cleanup_orphan_partials` recursively follows `Path::is_dir()`, which can traverse a symlinked directory and remove outside-root files ending in `.partial`.

Deletion already has a stronger canonical-parent pattern; RMD-1303 should apply the same class of safe-root check to transfer and cleanup mutations.

## Required patch plan

- Add a transfer destination resolver that validates the relative path, creates the parent if needed, canonicalizes the library root and final parent, and rejects parent paths outside the root.
- Reject existing symlink partial files before opening/resuming them.
- Make orphan-partial traversal use `symlink_metadata` so symlinked directories are skipped or rejected rather than followed.
- Add Unix adversarial tests for symlinked parent escape and symlinked orphan cleanup escape.
- Re-run exact-head CI and reconcile RMD-1303 only after the mutation path is hardened.

## Status

This audit is not RMD-1303 completion evidence. It records the blocker that must be fixed before the RMD-1303 checkboxes can be marked complete.
