# Portable Library Format Contract

This document defines the v1 persistence contract that must remain portable across Android and potential future Swift/desktop bindings. It describes semantics, not an Android filesystem API.

## Database and media-directory semantics

The SQLite library database is the authoritative index of durable library and download state. Completed media bytes live in application-owned filesystem storage and database records refer to those assets with portable path data. Temporary/incomplete transfer data is kept separate from completed assets and must never be surfaced as a completed library item.

A logical library root contains application-owned completed media and auxiliary assets such as thumbnails/subtitles; a separate temporary/incomplete area contains resumable transfer state. Promotion from incomplete to completed state occurs only after required integrity/size checks and is coordinated with the database so restart reconciliation can distinguish completed, resumable, orphaned, and missing assets.

Persisted paths are relative to an application-selected library root whenever possible. They use normalized portable path components, reject traversal (`..`) and unsafe absolute/provider-supplied paths, and are resolved by the platform integration layer. The portable database does not assume an Android package directory, `Context`, SAF document tree, or device mount point.

## Persisted identifiers and URIs

Portable records store source identity, stable item/job IDs, normalized asset relationships, relative local paths, media metadata, and playback/download state. They must not store Android-only `content://` or `android.resource://` URIs as the canonical cross-platform asset identity. Android may derive a Media3-compatible `Uri` at runtime from the resolved local asset path.

Remote provider URLs are source/download inputs, not completed-playback identities. Signed URLs, cookies, authorization headers, and transient extractor tokens are not durable library identifiers and must not be logged or persisted as generic diagnostics.

## Versioning and migration

SQLite schema changes are versioned through the core migration layer. Migrations are forward, explicit, and transactional where SQLite permits. A failed/incomplete migration must not be treated as a successfully upgraded database. Startup opens the database through the migration/reconciliation path before exposing library state.

Before the first public release, schema version `1` is the baseline released contract. During pre-release development, migrations may evolve, but tests must cover fresh database creation and every released-version upgrade path once released versions exist. Destructive migration is not an implicit fallback for a user library.

Changes to persisted enum/state representations require either backward-compatible decoding or a schema/data migration. Unknown/newer schema versions must fail explicitly rather than being silently interpreted with older semantics.

## Asset relationships

A completed item may reference a single playable media file or coordinated local video/audio assets plus optional thumbnail/subtitle assets. The relationship is represented in portable records; it is not inferred from Android URI structure. Deleting a library item removes only application-owned assets associated with that item and must not follow untrusted paths outside the library root.

Missing or corrupt files are reconciliation conditions. The database row alone does not prove media availability, and file presence alone does not promote an unverified partial into the completed library.

## Platform responsibilities

The portable core owns schema/migration semantics, path safety, durable state, asset relationships, and reconciliation decisions. A platform layer supplies the concrete writable library root, free-space information, lifecycle scheduling, and conversion from resolved local paths to the platform playback representation.

Future Swift/desktop bindings may choose different physical roots while preserving these database/media semantics. No future UI is required for v1; this contract exists to prevent Android-only persistence choices from closing that option.
