# RMD-1802: Empty completed-library assets

Independent review of core/src/persistence.rs found that validate_item_assets returns success when a completed item's persisted asset list is empty. The validation loop is vacuously successful, so a corrupted completed item can be reported as healthy despite having no playable media. Both promotion methods also accept completed items with empty or thumbnail-only asset lists.

Required remediation: reject completed-item promotion without at least one Video or Audio asset, and reject empty or thumbnail-only persisted completed items as CorruptAsset during validation. Add Rust regression coverage for both entry points and the persisted corruption case. Keep RMD-1802 and final engineering closeout unchecked until implementation and exact-head CI qualification.
