use crate::resume::resume_metadata_path;
use crate::{CoreError, ErrorKind, LibraryItem, LibraryStore};
use std::path::{Component, Path, PathBuf};

/// Deletes every persisted asset owned by `item_id` before removing its library metadata.
///
/// The operation is intentionally retryable after interruption: already-missing files count as
/// deleted, while metadata is retained until all remaining owned files, transfer partials, and
/// resume sidecars have been removed. This makes a partial delete explicit and lets the same
/// operation reconcile it on the next attempt.
pub fn delete_library_item_owned_assets(
    store: &LibraryStore,
    library_root: &Path,
    item_id: &str,
) -> Result<Option<LibraryItem>, CoreError> {
    let item = match store.get(item_id)? {
        Some(item) => item,
        None => return Ok(None),
    };

    let canonical_root = std::fs::canonicalize(library_root).map_err(delete_io_error)?;
    for asset in &item.assets {
        validate_delete_path(&asset.relative_path)?;
        let asset_path = library_root.join(&asset.relative_path);
        let partial_path = transfer_partial_path(&asset_path);
        let resume_path = resume_metadata_path(&partial_path);

        remove_owned_file(library_root, &canonical_root, &asset_path)?;
        remove_owned_file(library_root, &canonical_root, &partial_path)?;
        remove_owned_file(library_root, &canonical_root, &resume_path)?;
    }

    store.delete(item_id)
}

fn remove_owned_file(
    library_root: &Path,
    canonical_root: &Path,
    path: &Path,
) -> Result<(), CoreError> {
    let Some(parent) = path.parent() else {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "asset path has no parent",
            false,
        ));
    };
    let canonical_parent = match std::fs::canonicalize(parent) {
        Ok(parent) => parent,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(delete_io_error(error)),
    };
    if !canonical_parent.starts_with(canonical_root) {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "asset path escapes the selected library root",
            false,
        ));
    }
    if path.strip_prefix(library_root).is_err() {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "asset path escapes the selected library root",
            false,
        ));
    }
    match std::fs::remove_file(path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(delete_io_error(error)),
    }
}

fn validate_delete_path(value: &str) -> Result<(), CoreError> {
    let path = Path::new(value);
    if value.is_empty()
        || path.is_absolute()
        || path.components().any(|component| {
            matches!(
                component,
                Component::ParentDir | Component::RootDir | Component::Prefix(_)
            )
        })
    {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "asset path must be a safe relative library path",
            false,
        ));
    }
    Ok(())
}

fn transfer_partial_path(final_path: &Path) -> PathBuf {
    let name = final_path
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("asset");
    final_path.with_file_name(format!(".{name}.partial"))
}

fn delete_io_error(error: std::io::Error) -> CoreError {
    CoreError::new(
        ErrorKind::Persistence,
        format!("failed to delete owned library asset: {error}"),
        false,
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{LibraryItem, LocalAsset, MediaKind, SourceIdentity, THUMBNAIL_ASSET_ID};
    use tempfile::tempdir;

    fn item() -> LibraryItem {
        LibraryItem {
            item_id: "item-1".into(),
            source: SourceIdentity {
                provider: "fixture".into(),
                media_id: "source-1".into(),
                canonical_url: None,
            },
            display_title: "Test Video".into(),
            duration_ms: Some(60_000),
            quality_label: "720p".into(),
            assets: vec![
                LocalAsset {
                    asset_id: "video".into(),
                    kind: MediaKind::Video,
                    relative_path: "items/item-1/video.mp4".into(),
                    bytes: 4,
                    sha256: None,
                    mime_type: Some("video/mp4".into()),
                },
                LocalAsset {
                    asset_id: "audio".into(),
                    kind: MediaKind::Audio,
                    relative_path: "items/item-1/audio.m4a".into(),
                    bytes: 6,
                    sha256: None,
                    mime_type: Some("audio/mp4".into()),
                },
                LocalAsset {
                    asset_id: "z-subtitle".into(),
                    kind: MediaKind::Subtitle,
                    relative_path: "items/item-1/subtitles/en.vtt".into(),
                    bytes: 3,
                    sha256: None,
                    mime_type: Some("text/vtt".into()),
                },
                LocalAsset {
                    asset_id: THUMBNAIL_ASSET_ID.into(),
                    kind: MediaKind::Thumbnail,
                    relative_path: "items/item-1/thumbnails/thumbnail.jpg".into(),
                    bytes: 5,
                    sha256: None,
                    mime_type: Some("image/jpeg".into()),
                },
            ],
            created_at_epoch_ms: 1,
            playback_position_ms: 0,
            completed: true,
        }
    }

    fn persist(store: &LibraryStore, item: &LibraryItem) {
        store.promote_completed("job-1", item).unwrap();
    }

    fn write_asset_family(
        root: &Path,
        relative_path: &str,
        bytes: u64,
    ) -> (PathBuf, PathBuf, PathBuf) {
        let final_path = root.join(relative_path);
        std::fs::create_dir_all(final_path.parent().unwrap()).unwrap();
        std::fs::write(&final_path, vec![0_u8; bytes as usize]).unwrap();
        let partial_path = transfer_partial_path(&final_path);
        std::fs::write(&partial_path, b"partial bytes").unwrap();
        let resume_path = resume_metadata_path(&partial_path);
        std::fs::write(&resume_path, b"{\"sidecar\":true}").unwrap();
        (final_path, partial_path, resume_path)
    }

    #[test]
    fn removes_all_owned_assets_before_metadata() {
        let store = LibraryStore::open_in_memory().unwrap();
        let item = item();
        persist(&store, &item);
        let root = tempdir().unwrap();
        for asset in &item.assets {
            let path = root.path().join(&asset.relative_path);
            std::fs::create_dir_all(path.parent().unwrap()).unwrap();
            std::fs::write(&path, vec![0_u8; asset.bytes as usize]).unwrap();
        }

        let deleted = delete_library_item_owned_assets(&store, root.path(), "item-1")
            .unwrap()
            .unwrap();
        assert_eq!(deleted.item_id, "item-1");
        assert!(store.get("item-1").unwrap().is_none());
        for asset in &item.assets {
            assert!(!root.path().join(&asset.relative_path).exists());
        }
    }

    #[test]
    fn deleting_item_removes_managed_thumbnail_before_metadata() {
        let store = LibraryStore::open_in_memory().unwrap();
        let item = item();
        persist(&store, &item);
        let root = tempdir().unwrap();
        let thumbnail = item
            .assets
            .iter()
            .find(|asset| asset.kind == MediaKind::Thumbnail)
            .unwrap();
        let thumbnail_path = root.path().join(&thumbnail.relative_path);
        std::fs::create_dir_all(thumbnail_path.parent().unwrap()).unwrap();
        std::fs::write(&thumbnail_path, [1_u8; 5]).unwrap();

        delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap();

        assert!(!thumbnail_path.exists());
        assert!(store.get("item-1").unwrap().is_none());
    }

    #[test]
    fn removes_final_partial_and_resume_sidecar_for_each_asset_kind() {
        let store = LibraryStore::open_in_memory().unwrap();
        let item = item();
        persist(&store, &item);
        let root = tempdir().unwrap();
        let owned_paths = item
            .assets
            .iter()
            .map(|asset| write_asset_family(root.path(), &asset.relative_path, asset.bytes))
            .collect::<Vec<_>>();

        delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap();

        assert!(store.get("item-1").unwrap().is_none());
        for (final_path, partial_path, resume_path) in owned_paths {
            assert!(
                !final_path.exists(),
                "final asset remained: {final_path:?}"
            );
            assert!(
                !partial_path.exists(),
                "partial asset remained: {partial_path:?}"
            );
            assert!(
                !resume_path.exists(),
                "resume sidecar remained: {resume_path:?}"
            );
        }
    }

    #[test]
    fn partial_failure_keeps_metadata_and_retry_reconciles() {
        let store = LibraryStore::open_in_memory().unwrap();
        let mut item = item();
        item.assets.truncate(2);
        persist(&store, &item);
        let root = tempdir().unwrap();
        let video = root.path().join(&item.assets[0].relative_path);
        std::fs::create_dir_all(video.parent().unwrap()).unwrap();
        std::fs::write(&video, [0_u8; 4]).unwrap();
        let audio = root.path().join(&item.assets[1].relative_path);
        std::fs::create_dir_all(&audio).unwrap();

        let error = delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap_err();
        assert_eq!(error.kind, ErrorKind::Persistence);
        assert!(!video.exists());
        assert!(store.get("item-1").unwrap().is_some());

        std::fs::remove_dir(&audio).unwrap();
        std::fs::write(&audio, [0_u8; 6]).unwrap();
        delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap();
        assert!(!audio.exists());
        assert!(store.get("item-1").unwrap().is_none());
    }

    #[test]
    fn resume_sidecar_failure_keeps_metadata_and_retry_reconciles() {
        let store = LibraryStore::open_in_memory().unwrap();
        let mut item = item();
        item.assets.truncate(1);
        persist(&store, &item);
        let root = tempdir().unwrap();
        let (final_path, partial_path, resume_path) = write_asset_family(
            root.path(),
            &item.assets[0].relative_path,
            item.assets[0].bytes,
        );
        std::fs::remove_file(&resume_path).unwrap();
        std::fs::create_dir(&resume_path).unwrap();

        let error = delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap_err();

        assert_eq!(error.kind, ErrorKind::Persistence);
        assert!(!final_path.exists());
        assert!(!partial_path.exists());
        assert!(resume_path.exists());
        assert!(store.get("item-1").unwrap().is_some());

        std::fs::remove_dir(&resume_path).unwrap();
        std::fs::write(&resume_path, b"{\"sidecar\":true}").unwrap();
        delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap();
        assert!(!resume_path.exists());
        assert!(store.get("item-1").unwrap().is_none());
    }

    #[cfg(unix)]
    #[test]
    fn rejects_symlink_parent_escape_without_touching_outside_file() {
        use std::os::unix::fs::symlink;

        let store = LibraryStore::open_in_memory().unwrap();
        let mut item = item();
        item.assets.truncate(1);
        persist(&store, &item);
        let root = tempdir().unwrap();
        let outside = tempdir().unwrap();
        std::fs::create_dir_all(root.path().join("items")).unwrap();
        symlink(outside.path(), root.path().join("items/item-1")).unwrap();
        let outside_file = outside.path().join("video.mp4");
        std::fs::write(&outside_file, [0_u8; 4]).unwrap();

        let error = delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert!(outside_file.exists());
        assert!(store.get("item-1").unwrap().is_some());
    }
}
