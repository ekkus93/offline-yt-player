# RMD-1802 independent review: cross-item asset deletion

Status: **OPEN — production fix and exact-head qualification required**.

Reviewed source at `52246845c8cb611e4495ed4b521a31758722f298`: `core/src/deletion.rs::delete_library_item_owned_assets` validates that each persisted asset path is relative and that its parent stays under the library root, but it does **not** establish that the path is exclusively owned by the Library item being removed. `LibraryStore::promote_completed` in `core/src/persistence.rs` likewise checks relative-path safety, not cross-item path ownership.

## Reproduction and impact

Persist completed Library items A and B. Give A a malformed/corrupted `LocalAsset.relative_path` equal to B's persisted asset path (or a lexically equivalent `./` alias). Request deletion of A. The current deletion loop can remove B's on-disk media while retaining B's Library row. The symlink protections do not prevent this because the path remains inside the library root. This is an independent review finding, **not** a claim that external input currently controls Library paths.

## Required remediation

Before the first filesystem mutation, validate *all* A asset paths and reject any that overlap an asset owned by another persisted Library item, comparing normalized path components rather than raw strings. Preserve A and B metadata and B's bytes on rejection. Consider a database-level ownership constraint and concurrency-safe enforcement if a stronger invariant is feasible. Add a deterministic regression with two persisted items and a `./` alias. Run Rust fmt/clippy/tests and all required exact-head Android, supply-chain, fixture, and packaging workflows before RMD-1802/RMD-1803 closeout.

## Separate currently observed CI blockers

Exact master `afcff34549211999330ac16c7055e51c6727de72` failed deterministic fixture E2E run `38202869678`: `core/src/worker.rs:962` calls `execute_one` with five arguments after a sixth `platform_stop: &AtomicBool` argument was added. The same head failed Rust formatting in CI run `38202869668`. Android compilation failed due to unresolved `ProductionDatabaseName` in `DownloadWorkerExecutor.kt` on that head; the Android name was corrected on master by `52246845c8cb611e4495ed4b521a31758722f298`. The Rust callsite and formatting still require correction and fresh exact-head qualification.

Do not mark RMD-1802, RMD-1803, RMD-1805, or global closeout complete on this finding alone.
