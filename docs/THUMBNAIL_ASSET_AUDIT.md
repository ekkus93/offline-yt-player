# Thumbnail Asset Audit

## Scope

OYP-1201 defines portable thumbnail storage, offline display lookup, and cleanup classification for cached thumbnail assets.

## Policy

- Thumbnail downloads are represented as `MediaKind::Thumbnail` local assets under a deterministic relative library path.
- Thumbnail item ids and extensions are validated as safe path segments to prevent traversal or platform-specific absolute paths.
- Only `image/*` MIME types are accepted for thumbnail asset plans.
- Offline display resolves from the persisted local asset list and never requires the original remote thumbnail URL.
- Cleanup classifies thumbnail assets without an owning library item as removable orphans while retaining thumbnails attached to live items.

## Qualification

`thumbnail` unit tests prove safe local path construction, rejection of traversal and non-image MIME types, offline lookup from persisted assets, and orphan cleanup classification.
