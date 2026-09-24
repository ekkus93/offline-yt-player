# RMD-405 — Stored-hash corruption detection reconciliation

RMD-405 is implemented on current `master` (`0ea81f8336a77ed233fb1153de727dd937da54bd`). This note records implementation and qualification evidence without changing the canonical checklist state before the evidence commit itself is qualified and merged.

## Requirement mapping

- **Preserve cheap existence/size checks:** `core/src/asset_validation.rs::validate_asset` first validates the safe relative path, regular-file existence, and persisted byte length. `AssetValidationDepth::Cheap` returns after those checks without hashing.
- **Verify stored SHA-256 during explicit/deep validation:** `AssetValidationDepth::Deep` validates a persisted SHA-256 value, streams the local file through SHA-256, and compares it case-insensitively with the persisted digest.
- **Same-length corruption regression:** `asset_validation::tests::deep_validation_detects_same_length_corruption_using_stored_hash` writes `evil` over expected `good`; cheap validation remains healthy because length is unchanged, while deep validation reports corruption from the hash mismatch.
- **Repairable/user-visible state:** validation returns `AssetHealth::Missing` or `AssetHealth::Corrupt { reason }` rather than treating an invalid asset as playable or converting corruption into an opaque success. Existing recovery/UI plumbing consumes missing/integrity failures as repairable application state under RMD-1204.

## Historical implementation qualification

The implementation was qualified on exact head `b6fe8bf346f935c455c02a0917c2b40c7caa2119` by push CI run `35408844074` and pull-request CI run `35409095124`, both successful. The same `core/src/asset_validation.rs` implementation is present on current master.

## Current-master qualification

Current master `0ea81f8336a77ed233fb1153de727dd937da54bd` passed CI run `35949623488`, Android smoke run `35949623385`, and Android FGS-timeout run `35949623359`. This is consistent with the Android qualification acceleration plan: RMD-405 integrity proof is deterministic Rust/core behavior and does not require emulator-specific runtime proof.

## Canonical TODO reconciliation rule

The four RMD-405 checkboxes in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md` may be checked only after this evidence note is qualified on its exact head and merged to `master`; the canonical TODO should then cite this note plus the exact merged SHA and CI runs.