# RMD-1303 File/path safety reconciliation

This document records the implementation and qualification evidence for the completed RMD-1303 security-hardening slice from `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Canonical checklist scope

`RMD-1303 — File/path safety for all mutations` requires:

- applying safe-root/path validation to download, delete, rename, thumbnail, subtitle, cleanup, and recovery operations;
- adding traversal/symlink/adversarial path tests appropriate to platform/filesystem semantics.

## Prior safe paths already present

The RMD-1303 audit documented that several mutation paths were already constrained before the final download hardening patch:

- `core/src/security.rs::validate_relative_library_path` rejects empty, absolute, parent-directory, root, and platform-prefix paths.
- `core/src/deletion.rs::delete_library_item_owned_assets` validates persisted asset paths and verifies canonicalized asset parents stay under the canonical library root before deleting owned files.
- `core/src/thumbnail.rs` builds thumbnail paths from safe segments and rejects unsafe item IDs/extensions.
- `core/src/subtitle.rs` builds subtitle paths from safe item/track/language/format segments and filters unsafe persisted subtitle paths before local playback exposure.
- `core/src/ffi_library_rename.rs` is metadata-only and does not mutate filesystem paths.

The audit was merged in PR #313 as `6024f8933e3040e50d9d1baeebdb6df16ef0b6ea` from exact head `2411bb1e8ffbb8e807778cb9d9491994074ebaf7`. Push CI `35791357376` and PR CI `35792116432` passed.

## Final implementation evidence

PR #314 hardened the remaining download and orphan-partial cleanup mutation gaps in `core/src/download.rs`.

The implementation:

- canonicalizes the library root before transfer writes;
- creates the transfer parent only after validating the requested relative path and rejecting existing symlink components;
- canonicalizes the transfer parent and verifies it remains under the canonical library root;
- rejects existing final/partial symlinks before opening, resuming, or promoting transfer output;
- rechecks the final path before rename/promotion;
- changes orphan-partial traversal to use `symlink_metadata` rather than following symlinked directories;
- rejects cleanup traversal that would escape the canonical library root;
- adds Unix adversarial regression tests for symlinked transfer-parent escape and symlinked orphan-partial cleanup escape.

Qualified implementation evidence:

- PR #314 merged as `05d53e92cef91a34056abea3ccbc100ab86c561b`.
- Exact implementation head `74f1e814ac893cc4a01642c245bb211bf11273ed` passed push CI `35793406251`.
- The same exact head passed PR CI `35794146016`.
- Post-merge `master` SHA `05d53e92cef91a34056abea3ccbc100ab86c561b` passed master CI `35794658300`.

## Reconciliation result

RMD-1303 is considered implementation-qualified for the bounded security-hardening scope. The canonical remediation TODO can mark both RMD-1303 subtasks complete using this document, PR #313, PR #314, and the CI evidence listed above.

This reconciliation does not mark broader Android UI/E2E qualification complete. Those remain under RMD-1400/RMD-1500 and must follow `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`.
