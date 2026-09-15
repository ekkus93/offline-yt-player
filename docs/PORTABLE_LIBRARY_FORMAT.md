# Portable Library Format Contract

This document defines the v1 persistence contract that the portable Rust core owns. It is a compatibility contract for future Android, Swift, and desktop consumers; it is not a promise that applications may mutate the database behind a running process.

## Database and directory semantics

The library database is SQLite and is opened/migrated by `core::persistence::LibraryStore`. `schema_meta.version` is the authoritative schema version; v1 uses schema version `1`. Library rows store portable source identity, display metadata, quality, completion state, and playback position. Asset rows belong to a library item and store an asset kind, byte count, optional digest/MIME type, and a **relative path**.

Incomplete transfer metadata is separate from completed library state. `staged_assets` and durable `download_jobs` represent work that has not yet been promoted. A completed item is promoted transactionally only after verification. Startup/recovery logic may reconcile incomplete state, but partial files must never be represented as a completed item.

Applications choose a platform-appropriate private library root. Relative asset paths are resolved beneath that root. Persisted core records must not contain Android `content://` URIs, `file://` URIs, `Context` values, document-provider handles, or other platform-specific object identities. Platform code may translate a validated relative path to its native playback/file API at the boundary.

A representative portable layout is:

```text
<library-root>/
  library.sqlite3
  media/
    <item-id>/...
  thumbnails/
    <item-id>/...
  subtitles/
    <item-id>/...
  incomplete/
    <job-id>/...
```

The exact top-level directory names are an application policy rather than database keys. The durable invariant is that asset records contain validated relative paths and cannot escape the selected root. Moving the whole library root therefore does not require rewriting platform URIs in database rows.

## Versioning and migration expectations

Schema changes increment `SCHEMA_VERSION` and must be forward migrations from every released schema version supported by that release. Opening a database whose recorded version is newer than the running core fails explicitly rather than attempting a destructive downgrade. Migration failure must not silently mark a partially migrated database current.

Before a schema version ships publicly, its migration behavior becomes part of the compatibility contract. New nullable/optional metadata should be preferred when it preserves older completed items. Destructive representation changes require an explicit migration and qualification fixture.

Media files are versioned indirectly by their database asset records and integrity metadata. Code must not infer a newer semantic format merely from a filename extension. New asset kinds or relationships must remain representable through portable domain values before platform presentation code depends on them.

## Portability rules

The Rust core owns source identities, library/download records, relative asset paths, migration semantics, and integrity state. Platform layers own their library-root selection, permissions, lifecycle, and conversion from a validated local path to a platform playback handle.

Portable persisted records must remain serializable without Android framework classes or Media3 types. Provider-specific signed URLs, cookies, tokens, and transient extractor internals are not stable library identity and must not be required to reconstruct a completed item offline.

A future Swift or desktop binding may use a different absolute root and native media player while consuming the same portable database/domain semantics. Such a client must honor the schema version and relative-path validation rules rather than embedding its own absolute/platform URI into shared records.
