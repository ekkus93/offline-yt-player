# Persistence and offline-library audit

This audit records implementation and automated-test evidence for OYP-401 through OYP-404.

## OYP-401 — SQLite schema

`core/src/persistence.rs` implements `LibraryStore` on SQLite/rusqlite. `migrate()` maintains an explicit `schema_meta` version and creates the v1 schema transactionally. The schema persists library identity (`provider`, `source_media_id`, canonical URL), item metadata, asset records (including media kind/path/size/checksum/MIME), staged assets, durable download snapshots, and playback position. Foreign keys are enabled and library source identity has a unique index.

## OYP-402 — Atomic library completion

Incomplete transfer metadata lives in `staged_assets`, separate from completed `library_items`/`assets`. `promote_completed()` rejects incomplete items, validates every relative asset path, then performs the item upsert, final asset replacement, staging cleanup, and commit in one SQLite transaction. A failed transaction therefore cannot expose a partially promoted metadata state. Files are required to have been durably moved to their final relative paths before metadata promotion, making interruption before promotion recoverable as staging/orphan state rather than a falsely completed library item.

Automated tests prove staging is cleared only on successful promotion and that an incomplete item cannot be promoted.

## OYP-403 — Library repository API

`LibraryStore` exposes list/search, get, rename, playback-position save, delete, and asset validation. Search escapes SQL LIKE wildcards. Delete returns the removed item so the platform layer can safely remove its associated relative files after the database mutation. `validate_item_assets()` rejects missing files and wrong-sized/corrupt assets; all persisted relative paths pass traversal validation.

Tests cover search, retrieve/update/delete round trips, wildcard escaping, traversal rejection, and missing/wrong-sized asset detection.

## OYP-404 — Migration tests

`fresh_database_migrates_to_current_schema` proves a clean database reaches `SCHEMA_VERSION`. There is currently only one released schema version (`SCHEMA_VERSION = 1`), so there are no historical released-version upgrade fixtures yet. The migration code rejects databases newer than the application and wraps migration/database failures in the typed `Persistence` error category. Upgrade-from-every-released-version fixtures become applicable when schema v2 exists; this is a future-version condition rather than missing v1 behavior.

## Qualification boundary

This closes the v1 persistence/library implementation contract. Android presentation and filesystem cleanup orchestration remain covered by their own UX/service/recovery milestones. Exact-head CI for the audit branch and merged master is recorded in the TODO reconciliation commit that consumes this audit.
