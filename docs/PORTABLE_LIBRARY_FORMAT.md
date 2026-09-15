# Portable offline-library format contract

Offline YT Player uses a platform-supplied library root plus a SQLite database managed by the Rust core. Persisted core records remain portable primitives and do not contain Android framework objects.

## Database and migrations

The database schema has an integer version in `schema_meta`. Rust owns schema interpretation and migrations. A database newer than the running core is rejected rather than downgraded or guessed at. Once a schema version ships publicly, CI retains an upgrade fixture for it. Migration changes are transactional so failure does not masquerade as a completed migration.

## Item identity

Each item has a portable item ID and source identity: provider, provider media ID, and optional canonical network URL. Display title, duration, selected quality label, creation time, playback position, completion state, and local assets are ordinary portable data values.

Completed offline playback must not require the canonical network URL.

## Asset paths

Asset records store relative paths beneath the platform-supplied library root. They do not persist Android URI objects or absolute device-specific locations. The platform maps the root to its storage mechanism; the core validates every relative path before use.

A recommended logical layout is a database at the root, completed item assets grouped beneath an items directory, and incomplete job data grouped separately beneath a partial directory. The exact root name is not part of the contract.

## Asset relationships

One item may own multiple assets, including separate video and audio streams, thumbnails, and subtitles. Each asset records an item-local asset ID, semantic media kind, relative path, byte count, optional SHA-256, and optional MIME type. Separate adaptive audio/video assets remain related through their common item identity; platform playback classes are not persisted.

## Completion and integrity

Only verified assets are promoted into a completed item. Promotion of database metadata is atomic. Incomplete or staged data is not a completed library asset and may be reconciled after interruption. Missing or corrupt completed files are explicit recovery conditions rather than silently playable items.

## Versioning and portability

- Rust owns migrations on every supported platform.
- Future Swift or desktop bindings consume the same provider-independent records.
- New platform-specific metadata stays outside the portable contract unless represented by a portable primitive with defined semantics.
- Changes to persisted meaning require a schema-version migration and compatibility test.
- Moving a library preserves the database and relative asset tree together.
- Media files are not rewritten merely because the library moves between platforms; the receiving platform evaluates playback compatibility.
